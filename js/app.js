// Entry point: wires up the form, buttons, dialogs and the scenario manager.

import { generateRandomPatient, generateRandomDoctor } from './faker.js';
import { buildEntlassungsbrief } from './doctype-entlassung.js';
import { LOGO_DATA_URI } from './logo-base64.js';
import { HOSPITALS_BY_BUNDESLAND } from './hospitals.js';
import { getState, replaceState, saveState, defaultState } from './state.js';
import { renderAllLists, setupListAddButtons, setupQuickAddDropdowns } from './lists.js';
import { bindInputs, rebindAll, setupPvVisibility, setupSvnrValidation, svnrIsAcceptable, updateSvnrMarking } from './form.js';
import { setupScenarioManager, getCloudUsername } from './scenario-manager.js';
import { cloudUserHeader } from './cloud-scenarios.js';
import { apiJson, apiPdf } from './api.js';
import { downloadFile, downloadBlob } from './download.js';
import { report, withButtonBusy } from './ui-feedback.js';

/**
 * Checks the SVNR and builds the CDA document. Returns { xml, xmlFilename }, or null
 * (after pointing the user at the SVNR field) when the SVNR is invalid.
 */
function validateAndBuild() {
    const state = getState();
    if (!svnrIsAcceptable(state.patient.svnr)) {
        report('Ungültige SVNR — bitte korrigieren (10-stellig mit gültiger Prüfziffer).', 'error');
        document.querySelector('[data-bind="patient.svnr"]')?.focus();
        updateSvnrMarking();
        return null;
    }
    const family = (state.patient.familyName || 'anonym').toLowerCase();
    const xmlFilename = `entlassungsbrief-${family}-${(state.documentDate || '').slice(0, 10)}.xml`;
    return { xml: buildEntlassungsbrief(state), xmlFilename };
}

function setupButtons() {
    document.getElementById('btn-faker').addEventListener('click', () => {
        const bl = document.getElementById('global-bundesland')?.value || null;
        const state = getState();
        state.patient = generateRandomPatient(bl);
        saveState();
        rebindAll();
        report(`Stammdaten generiert: ${state.patient.givenName} ${state.patient.familyName}, SVNR ${state.patient.svnr}`, 'success');
    });

    document.getElementById('btn-faker-doctor').addEventListener('click', () => {
        const state = getState();
        state.author = generateRandomDoctor();
        saveState();
        rebindAll();
        report(`Arzt generiert: ${state.author.title} ${state.author.givenName} ${state.author.familyName}`, 'success');
    });

    setupXmlUpload();

    document.getElementById('btn-reset').addEventListener('click', () => {
        if (!confirm('Formular auf Default-Werte zurücksetzen? (Aktuelle Eingaben gehen verloren)')) return;
        replaceState(defaultState());
        rebindAll();
        report('Formular zurückgesetzt.', 'info');
    });

    document.getElementById('btn-generate-xml').addEventListener('click', () => {
        const built = validateAndBuild();
        if (!built) return;
        const { xml, xmlFilename: filename } = built;
        downloadFile(filename, xml, 'application/xml');
        // Usage statistics only — a failure here must never bother the user.
        fetch('/api/usage/xml-download', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: getCloudUsername() }),
        }).catch(() => {});
        report(`XML generiert: ${filename}`, 'success');
    });

    const btnGenerate = document.getElementById('btn-generate');
    btnGenerate.addEventListener('click', async () => {
        const built = validateAndBuild();
        if (!built) return;
        const { xml, xmlFilename } = built;
        const pdfFilename = xmlFilename.replace(/\.xml$/i, '.pdf');
        await withButtonBusy(btnGenerate, 'wird erstellt…', async () => {
            try {
                const pdfBlob = await apiPdf('/api/pdf', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', ...cloudUserHeader(getCloudUsername()) },
                    body: JSON.stringify({ xml, fileName: pdfFilename }),
                });
                downloadBlob(pdfFilename, pdfBlob);
                report(`PDF generiert: ${pdfFilename}`, 'success');
            } catch (err) {
                report(`PDF-Generierung fehlgeschlagen: ${err.message}`, 'error');
            }
        });
    });

}

function setupHospitalSelector() {
    const blSelect = document.getElementById('global-bundesland');
    const hospSelect = document.getElementById('org-hospital-select');
    if (!blSelect || !hospSelect) return;

    blSelect.addEventListener('change', () => {
        const bl = blSelect.value;
        hospSelect.innerHTML = '<option value="">Krankenhaus auswählen …</option>';
        hospSelect.disabled = !bl;
        if (!bl) return;
        (HOSPITALS_BY_BUNDESLAND[bl] || []).forEach((h, i) => {
            const opt = document.createElement('option');
            opt.value = String(i);
            opt.textContent = h.name;
            hospSelect.appendChild(opt);
        });
    });

    hospSelect.addEventListener('change', () => {
        const bl = blSelect.value;
        const idx = Number(hospSelect.value);
        if (!bl || hospSelect.value === '') return;
        const h = (HOSPITALS_BY_BUNDESLAND[bl] || [])[idx];
        if (!h) return;
        const state = getState();
        state.organization.name = h.name;
        state.organization.phone = h.phone;
        state.organization.address.street = h.street;
        state.organization.address.houseNumber = h.houseNumber;
        state.organization.address.postalCode = h.postalCode;
        state.organization.address.city = h.city;
        state.organization.address.country = 'A';
        saveState();
        rebindAll();
        report(`Krankenhaus ausgewählt: ${h.name}`, 'info');
    });
}

// Version badge — source of truth is the Maven version exposed via /api/version
async function initVersionBadge() {
    const badge = document.getElementById('version-badge');
    const changelogDialog = document.getElementById('changelog-dialog');
    if (!badge || !changelogDialog) return;

    badge.addEventListener('click', () => changelogDialog.showModal());
    document.getElementById('changelog-close')?.addEventListener('click', () => changelogDialog.close());

    try {
        const data = await apiJson('/api/version');
        if (data?.version) badge.textContent = `v${data.version}`;
    } catch {
        // If the fetch fails, leave the badge empty rather than showing a stale hardcoded value
    }
}

// Init ----------------------------------------------------------------------
function init() {
    document.getElementById('header-logo').src = LOGO_DATA_URI;
    bindInputs();
    renderAllLists();
    setupListAddButtons();
    setupQuickAddDropdowns();
    setupButtons();
    setupScenarioManager();
    setupPvVisibility();
    setupHospitalSelector();
    initVersionBadge();

    setupSvnrValidation();
    setupPanelDialog('scenario-dialog', 'btn-open-scenarios', 'scenario-dialog-close');
    setupPanelDialog('xml-upload-dialog', 'btn-open-xml-upload', 'xml-upload-dialog-close');
}

function setupPanelDialog(dialogId, openBtnId, closeBtnId) {
    const dialog = document.getElementById(dialogId);
    if (!dialog) return;
    document.getElementById(openBtnId)?.addEventListener('click', () => dialog.showModal());
    document.getElementById(closeBtnId)?.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); });
}

function setupXmlUpload() {
    const fileInput = document.getElementById('xml-upload-input');
    const btnChoose = document.getElementById('btn-xml-upload');
    const btnConvert = document.getElementById('btn-xml-convert');
    const filenameDisplay = document.getElementById('xml-upload-filename');
    const passwordInput = document.getElementById('xml-convert-password');

    btnChoose.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', () => {
        const file = fileInput.files[0];
        if (file) {
            filenameDisplay.textContent = file.name;
            btnConvert.disabled = false;
        } else {
            filenameDisplay.textContent = 'Keine Datei ausgewählt';
            btnConvert.disabled = true;
        }
    });

    btnConvert.addEventListener('click', async () => {
        const file = fileInput.files[0];
        if (!file) return;

        const pw = passwordInput?.value ?? '';
        const formData = new FormData();
        formData.append('file', file);

        await withButtonBusy(btnConvert, 'wird erstellt…', async () => {
            try {
                const pdfBlob = await apiPdf('/api/pdf/upload', {
                    method: 'POST',
                    body: formData,
                    headers: { 'X-Clean-Pdf-Password': pw, ...cloudUserHeader(getCloudUsername()) },
                });
                const pdfFilename = file.name.replace(/\.xml$/i, '.pdf');
                downloadBlob(pdfFilename, pdfBlob);
                report(`PDF generiert: ${pdfFilename}`, 'success');
            } catch (err) {
                report(`PDF-Generierung fehlgeschlagen: ${err.message}`, 'error');
            }
        });
        // withButtonBusy re-enables the button in its finally; restore the
        // file-gated disabled state (the button is only valid with a file selected).
        btnConvert.disabled = fileInput.files.length === 0;
    });
}

init();
