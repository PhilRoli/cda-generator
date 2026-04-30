// UI-Logik: Form-State <-> DOM, Tabs, Listen-Items, Persistenz, Generierung.

import { generateRandomPatient, generateRandomDoctor } from './faker.js';
import { buildEntlassungsbrief } from './doctype-entlassung.js';
import { LOGO_DATA_URI } from './logo-base64.js';
import { HOSPITALS_BY_BUNDESLAND } from './hospitals.js';

const APP_VERSION = '1.2.0';

const STORAGE_KEY = 'cda-uebung:last';
const CLOUD_USER_KEY = 'cda-uebung:cloud-username';
const SCENARIO_SOURCE_KEY = 'cda-uebung:scenario-source';

let cloudScenarios = [];
let selectedCloudScenarioId = null;

// Sinnvolle Default-Inhalte (RD-Übungs-tauglich, leicht anpassbar)
function defaultState() {
    const today = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    const isoLocal = (d) =>
        `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    const admission = new Date(today.getTime() - 4 * 86400000);
    admission.setHours(14, 30, 0, 0);
    const discharge = new Date(today);
    discharge.setHours(11, 0, 0, 0);

    return {
        documentDate: isoLocal(discharge),
        patient: {
            givenName: 'Maria',
            familyName: 'Gruber',
            gender: 'F',
            birthDate: '1948-03-12',
            svnr: '',
            phone: '06641234567',
            address: {
                street: 'Linzer Bundesstraße',
                houseNumber: '34',
                postalCode: '5023',
                city: 'Salzburg',
                country: 'A',
            },
        },
        organization: {
            name: 'Universitätsklinikum Salzburg Landeskrankenhaus',
            phone: '+43(0)57255',
            address: {
                street: 'Müllner Hauptstraße',
                houseNumber: '48',
                postalCode: '5020',
                city: 'Salzburg',
                country: 'A',
            },
        },
        author: { title: 'Dr.', givenName: 'Andrea', familyName: 'Hofer' },
        encounter: {
            admissionDate: isoLocal(admission),
            dischargeDate: isoLocal(discharge),
            ward: 'Unfallchirurgie, Station 3B',
            caseId: '',
            type: 'IMP',
        },
        brieftext: { text: '' },
        aufnahmegrund:
            'Stationäre Aufnahme nach häuslichem Sturz mit Verdacht auf Schenkelhalsfraktur links. Patientin wurde durch den Notarzt zugewiesen.',
        diagnosen: [
            { text: 'Mediale Schenkelhalsfraktur links' },
            { text: 'Arterielle Hypertonie' },
            { text: 'Diabetes mellitus Typ 2, medikamentös eingestellt' },
        ],
        vorerkrankungen: [
            { text: 'Arterielle Hypertonie' },
            { text: 'Diabetes mellitus Typ 2' },
            { text: 'Z.n. Cholezystektomie' },
        ],
        anamnese:
            'Patientin wohnt allein in Erdgeschosswohnung, ist bisher selbstständig mobil. Sturz beim Aufstehen aus dem Sessel, kein Bewusstseinsverlust, keine Synkope erinnerlich. Schmerzen unmittelbar im linken Hüftbereich, keine Belastbarkeit mehr.',
        verlauf:
            'Bei Aufnahme klinisch und radiologisch Bestätigung der medialen Schenkelhalsfraktur links. Indikation zur operativen Versorgung gestellt. Am Folgetag Implantation einer zementierten Hüfttotalendoprothese links in Spinalanästhesie, intraoperativer Verlauf unauffällig. Postoperativ rasche Mobilisation an zwei Unterarmstützen unter physiotherapeutischer Anleitung. Wundverhältnisse reizlos, Drainagezug am 2. postoperativen Tag.\n\nUnter Thromboseprophylaxe mit niedermolekularem Heparin keine Komplikationen. Blutzucker stabil. Vorbestehende antihypertensive Therapie unverändert fortgeführt.',
        medikation: [
            { medikament: 'Lovenox 40 mg s.c.', schema: '0-0-0-1' },
            { medikament: 'Pantoloc 40 mg', schema: '1-0-0' },
            { medikament: 'Ramipril 5 mg', schema: '1-0-0' },
            { medikament: 'Metformin 850 mg', schema: '1-0-1' },
            { medikament: 'Mexalen 500 mg', schema: '1-1-1-1' },
            { medikament: 'Tramal Tropfen 20 Tr.', schema: 'b.B.' },
        ],
        empfehlungen:
            'Mobilisation an zwei Unterarmstützen unter Teilbelastung links für 6 Wochen. Physiotherapie ambulant fortführen. Wundkontrolle und Fadenzug beim Hausarzt am 14. postoperativen Tag.\n\nKlinische Kontrolle in unserer orthopädischen Ambulanz in 6 Wochen mit Röntgenkontrolle.\n\nThromboseprophylaxe mit Lovenox 40mg s.c. einmal täglich für 4 Wochen.\n\nBei Fieber, zunehmenden Schmerzen, Wundsekretion oder Rötung im OP-Bereich umgehende Wiedervorstellung.',
        allergien: [{ substanz: 'Penicillin' }, { substanz: 'Jodhaltige Kontrastmittel' }],
        risikofaktoren: [
            { faktor: 'Arterielle Hypertonie' },
            { faktor: 'Adipositas' },
            { faktor: 'Bewegungsmangel' },
        ],
        patientenverfuegung: {
            status: 'beachtlich',
            hinterlegtBei: 'Hausärztin Dr. Berger, Kopie bei Tochter',
            datum: '2022-09-15',
            gueltigBis: '2027-09-15',
            bemerkung:
                'Ablehnung intensivmedizinischer Maßnahmen bei infauster Prognose. Keine künstliche Beatmung, keine Reanimation. Schmerzlinderung erwünscht.',
        },
    };
}

// State + DOM-Bindings ------------------------------------------------------
let state = loadState();

function loadState() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (raw) return Object.assign(defaultState(), JSON.parse(raw));
    } catch {}
    return defaultState();
}

function saveState() {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {}
}

function getByPath(obj, path) {
    return path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj);
}

function setByPath(obj, path, value) {
    const parts = path.split('.');
    let cur = obj;
    for (let i = 0; i < parts.length - 1; i++) {
        if (cur[parts[i]] == null) cur[parts[i]] = {};
        cur = cur[parts[i]];
    }
    cur[parts[parts.length - 1]] = value;
}

function bindInputs() {
    document.querySelectorAll('[data-bind]').forEach((el) => {
        const path = el.getAttribute('data-bind');
        const v = getByPath(state, path);
        if (v !== undefined) el.value = v;
        el.addEventListener('input', () => {
            setByPath(state, path, el.value);
            saveState();
        });
    });
}

// Listen-Items (Diagnosen, Vorerkrankungen, Medikation) ---------------------
const LIST_TEMPLATES = {
    diagnosen: {
        fields: [{ key: 'text', label: 'Diagnose', type: 'text' }],
        grid: 'grid-single',
        empty: { text: '' },
    },
    vorerkrankungen: {
        fields: [{ key: 'text', label: 'Vorerkrankung', type: 'text' }],
        grid: 'grid-single',
        empty: { text: '' },
    },
    medikation: {
        fields: [
            { key: 'medikament', label: 'Medikament', type: 'text' },
            { key: 'schema', label: 'Schema', type: 'text' },
        ],
        grid: 'grid-medi',
        empty: { medikament: '', schema: '' },
    },
    allergien: {
        fields: [{ key: 'substanz', label: 'Allergie / Substanz', type: 'text' }],
        grid: 'grid-single',
        empty: { substanz: '' },
    },
    risikofaktoren: {
        fields: [{ key: 'faktor', label: 'Risikofaktor', type: 'text' }],
        grid: 'grid-single',
        empty: { faktor: '' },
    },
};

const QUICK_ADD_OPTIONS = {
    diagnosen: [
        { text: 'Arterielle Hypertonie' },
        { text: 'Diabetes mellitus Typ 2' },
        { text: 'COPD' },
        { text: 'Pneumonie, ambulant erworben' },
        { text: 'Harnwegsinfekt' },
        { text: 'Herzinsuffizienz' },
    ],
    vorerkrankungen: [
        { text: 'Arterielle Hypertonie' },
        { text: 'Diabetes mellitus Typ 2' },
        { text: 'Koronare Herzkrankheit' },
        { text: 'COPD' },
        { text: 'Vorhofflimmern' },
        { text: 'Chronische Niereninsuffizienz' },
    ],
    allergien: [
        { substanz: 'Penicillin' },
        { substanz: 'Jodhaltige Kontrastmittel' },
        { substanz: 'Latex' },
        { substanz: 'ASS (Acetylsalicylsäure)' },
        { substanz: 'Nüsse' },
        { substanz: 'Pollen' },
    ],
    risikofaktoren: [
        { faktor: 'Nikotinabusus' },
        { faktor: 'Alkoholkonsum' },
        { faktor: 'Adipositas' },
        { faktor: 'Bewegungsmangel' },
        { faktor: 'Diabetes mellitus' },
        { faktor: 'Positive kardiovaskuläre Familienanamnese' },
    ],
    medikation: [
        { medikament: 'Ramipril 5 mg', schema: '1-0-0' },
        { medikament: 'Bisoprolol 5 mg', schema: '1-0-0' },
        { medikament: 'Metformin 850 mg', schema: '1-0-1' },
        { medikament: 'Pantoprazol 40 mg', schema: '1-0-0' },
        { medikament: 'ASS 100 mg', schema: '1-0-0' },
        { medikament: 'Atorvastatin 20 mg', schema: '0-0-1' },
    ],
};

const QUICK_ADD_PLACEHOLDERS = {
    diagnosen: 'Häufige Diagnose auswählen …',
    vorerkrankungen: 'Häufige Vorerkrankung auswählen …',
    allergien: 'Häufige Allergie auswählen …',
    risikofaktoren: 'Häufigen Risikofaktor auswählen …',
    medikation: 'Häufiges Medikament auswählen …',
};

function listItemLabel(name, item) {
    if (name === 'medikation') return `${item.medikament} (${item.schema || '-'})`;
    const firstField = LIST_TEMPLATES[name]?.fields?.[0]?.key;
    return firstField ? item[firstField] || '' : '';
}

function hasSameListItem(name, candidate) {
    const fields = (LIST_TEMPLATES[name]?.fields || []).map((f) => f.key);
    if (!fields.length) return false;
    const normalize = (v) => String(v ?? '').trim().toLowerCase();
    return (state[name] || []).some((entry) => fields.every((key) => normalize(entry[key]) === normalize(candidate[key])));
}

function addListItem(name, item) {
    const tpl = LIST_TEMPLATES[name];
    if (!tpl) return;
    if (!state[name]) state[name] = [];
    state[name].push({ ...item });
    saveState();
    renderList(name);
}

function renderList(name) {
    const tpl = LIST_TEMPLATES[name];
    const container = document.getElementById(`${name}-list`);
    if (!container || !tpl) return;
    container.innerHTML = '';
    const items = state[name] || [];
    items.forEach((item, idx) => {
        const row = document.createElement('div');
        row.className = tpl.grid + (idx === 0 ? ' row-with-header' : '');
        tpl.fields.forEach((f) => {
            const lbl = document.createElement('label');
            const span = document.createElement('span');
            span.className = 'label-text';
            span.textContent = f.label;
            lbl.appendChild(span);
            const input = document.createElement('input');
            input.type = f.type;
            input.value = item[f.key] ?? '';
            input.addEventListener('input', () => {
                item[f.key] = input.value;
                saveState();
            });
            lbl.appendChild(input);
            row.appendChild(lbl);
        });
        const removeBtn = document.createElement('button');
        removeBtn.className = 'btn icon danger';
        removeBtn.title = 'Entfernen';
        removeBtn.textContent = '✕';
        removeBtn.addEventListener('click', () => {
            state[name].splice(idx, 1);
            saveState();
            renderList(name);
        });
        if (idx === 0) {
            // Header-Spacer
            const lbl = document.createElement('label');
            const span = document.createElement('span');
            span.className = 'label-text';
            span.innerHTML = '&nbsp;';
            lbl.appendChild(span);
            lbl.appendChild(removeBtn);
            row.appendChild(lbl);
        } else {
            row.appendChild(removeBtn);
        }
        container.appendChild(row);
    });
}

function setupListAddButtons() {
    document.querySelectorAll('[data-add]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const name = btn.getAttribute('data-add');
            const tpl = LIST_TEMPLATES[name];
            if (!tpl) return;
            addListItem(name, tpl.empty);
        });
    });
}

function setupQuickAddDropdowns() {
    document.querySelectorAll('[data-add]').forEach((btn) => {
        const name = btn.getAttribute('data-add');
        const options = QUICK_ADD_OPTIONS[name];
        if (!options?.length) return;

        const select = document.createElement('select');
        select.className = 'quick-add-select';
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = QUICK_ADD_PLACEHOLDERS[name] || 'Standardwert auswählen …';
        select.appendChild(placeholder);
        options.forEach((entry, idx) => {
            const option = document.createElement('option');
            option.value = String(idx);
            option.textContent = listItemLabel(name, entry);
            select.appendChild(option);
        });
        select.addEventListener('change', () => {
            const idx = Number(select.value);
            if (Number.isNaN(idx) || !options[idx]) return;
            const selectedEntry = options[idx];
            if (hasSameListItem(name, selectedEntry)) {
                setStatus(`Eintrag bereits vorhanden: ${listItemLabel(name, selectedEntry)}`);
                select.value = '';
                return;
            }
            addListItem(name, selectedEntry);
            setStatus(`Standardwert hinzugefügt: ${listItemLabel(name, selectedEntry)}`);
            select.value = '';
        });
        const actionRow = document.createElement('div');
        actionRow.className = 'quick-add-actions';
        btn.insertAdjacentElement('beforebegin', actionRow);
        actionRow.appendChild(select);
        actionRow.appendChild(btn);
    });
}

// Buttons -------------------------------------------------------------------
function setStatus(msg) {
    document.getElementById('status').textContent = msg || '';
}

function cloudScenarioTitleSuggestion() {
    const family = (state.patient?.familyName || 'anonym').toLowerCase();
    const date = (state.documentDate || new Date().toISOString()).slice(0, 10);
    return `szenario-${family}-${date}`;
}

function saveLocalScenario() {
    const filename = `szenario-${(state.patient.familyName || 'anonym').toLowerCase()}-${new Date().toISOString().slice(0, 10)}.json`;
    downloadFile(filename, JSON.stringify(state, null, 2), 'application/json');
    setStatus(`Lokales Szenario gespeichert: ${filename}`);
}

async function loadLocalScenarioFromFile(file) {
    if (!file) return;
    try {
        const text = await file.text();
        const loaded = JSON.parse(text);
        state = Object.assign(defaultState(), loaded);
        saveState();
        rebindAll();
        setStatus(`Lokales Szenario geladen: ${file.name}`);
    } catch (err) {
        setStatus(`Fehler beim lokalen Laden: ${err.message}`);
    }
}

async function apiJson(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try {
            const body = await response.json();
            if (body?.message) message = body.message;
        } catch {}
        throw new Error(message);
    }
    if (response.status === 204) return null;
    return response.json();
}

async function apiPdf(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try {
            const body = await response.json();
            if (body?.message) message = body.message;
        } catch {}
        throw new Error(message);
    }
    return response.blob();
}

function getCloudUsername() {
    const input = document.getElementById('cloud-username');
    return input?.value?.trim() || '';
}

function isShowAll() {
    return document.getElementById('cloud-show-all')?.checked ?? false;
}

async function refreshCloudScenarios(options = {}) {
    const { silent = false } = options;

    if (isShowAll()) {
        const list = await apiJson('/api/scenarios/all');
        cloudScenarios = list;
        renderCloudScenarioSelect();
        if (!silent) setStatus(`Cloud-Liste aktualisiert: ${cloudScenarios.length} Szenario(s) von allen Benutzern.`);
        return;
    }

    const username = getCloudUsername();
    if (!username) {
        cloudScenarios = [];
        renderCloudScenarioSelect();
        if (!silent) setStatus('Bitte zuerst einen Benutzernamen für Cloud-Szenarien eingeben.');
        return;
    }
    const list = await apiJson(`/api/scenarios?username=${encodeURIComponent(username)}`);
    cloudScenarios = list;
    renderCloudScenarioSelect();
    if (!silent) setStatus(`Cloud-Liste aktualisiert: ${cloudScenarios.length} Szenario(s).`);
}

function renderCloudScenarioSelect() {
    const select = document.getElementById('cloud-scenario-select');
    if (!select) return;
    const keepId = selectedCloudScenarioId;
    const showAll = isShowAll();
    select.innerHTML = '';

    if (!cloudScenarios.length) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'Keine Cloud-Szenarien vorhanden';
        select.appendChild(option);
        selectedCloudScenarioId = null;
        return;
    }

    cloudScenarios.forEach((scenario) => {
        const option = document.createElement('option');
        option.value = scenario.id;
        const prefix = showAll ? `[${scenario.username}] ` : '';
        option.textContent = `${prefix}${scenario.title} · ${scenario.updatedAt}`;
        if (keepId && keepId === scenario.id) option.selected = true;
        select.appendChild(option);
    });

    selectedCloudScenarioId = select.value || cloudScenarios[0].id;
    if (selectedCloudScenarioId) select.value = selectedCloudScenarioId;
}

async function saveCloudScenario() {
    const username = getCloudUsername();
    if (!username) {
        setStatus('Bitte zuerst einen Benutzernamen für Cloud-Speicherung eingeben.');
        return;
    }

    const defaultTitle = cloudScenarioTitleSuggestion();
    const title = prompt('Titel für das Cloud-Szenario:', defaultTitle);
    if (title === null) return;

    const payload = {
        id: selectedCloudScenarioId || undefined,
        username,
        title: title.trim() || defaultTitle,
        state,
    };

    const saved = await apiJson('/api/scenarios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
    });

    selectedCloudScenarioId = saved.id;
    await refreshCloudScenarios();
    setStatus(`Cloud-Szenario gespeichert: ${saved.title}`);
}

async function loadCloudScenario() {
    if (!selectedCloudScenarioId) {
        setStatus('Bitte zuerst ein Cloud-Szenario auswählen.');
        return;
    }

    let url;
    if (isShowAll()) {
        url = `/api/scenarios/${encodeURIComponent(selectedCloudScenarioId)}`;
    } else {
        const username = getCloudUsername();
        if (!username) {
            setStatus('Bitte zuerst einen Benutzernamen eingeben.');
            return;
        }
        url = `/api/scenarios/${encodeURIComponent(selectedCloudScenarioId)}?username=${encodeURIComponent(username)}`;
    }

    const detail = await apiJson(url);
    state = Object.assign(defaultState(), detail.state || {});
    saveState();
    rebindAll();
    setStatus(`Cloud-Szenario geladen: ${detail.title}`);
}

async function deleteCloudScenarioAsAdmin() {
    if (!selectedCloudScenarioId) {
        setStatus('Bitte zuerst ein Cloud-Szenario auswählen.');
        return;
    }
    const token = prompt('Admin-Token eingeben:');
    if (token === null) return;
    const trimmedToken = token.trim();
    if (!trimmedToken) {
        setStatus('Bitte Admin-Token eingeben.');
        return;
    }
    if (!confirm('Ausgewähltes Cloud-Szenario als Admin löschen?')) return;

    await apiJson(`/api/admin/scenarios/${encodeURIComponent(selectedCloudScenarioId)}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${trimmedToken}` },
    });
    selectedCloudScenarioId = null;
    await refreshCloudScenarios();
    setStatus('Cloud-Szenario per Admin-Recht gelöscht.');
}

function updateScenarioSourceVisibility() {
    const sourceSelect = document.getElementById('scenario-source');
    const cloudUsernameWrap = document.getElementById('cloud-username-wrap');
    const localActions = document.getElementById('scenario-local-actions');
    const cloudActions = document.getElementById('scenario-cloud-actions');
    const isCloud = sourceSelect.value === 'cloud';

    cloudUsernameWrap.classList.toggle('hidden', !isCloud);
    localActions.classList.toggle('hidden', isCloud);
    cloudActions.classList.toggle('hidden', !isCloud);
}

function setupScenarioManager() {
    const sourceSelect = document.getElementById('scenario-source');
    const usernameInput = document.getElementById('cloud-username');
    const localSaveBtn = document.getElementById('btn-local-save');
    const localLoadBtn = document.getElementById('btn-local-load');
    const select = document.getElementById('cloud-scenario-select');
    const fileInput = document.getElementById('file-load');

    sourceSelect.value = localStorage.getItem(SCENARIO_SOURCE_KEY) || 'local';
    usernameInput.value = localStorage.getItem(CLOUD_USER_KEY) || '';

    sourceSelect.addEventListener('change', () => {
        localStorage.setItem(SCENARIO_SOURCE_KEY, sourceSelect.value);
        updateScenarioSourceVisibility();
        if (sourceSelect.value === 'cloud') {
            refreshCloudScenarios({ silent: true }).catch(() => {
                renderCloudScenarioSelect();
            });
        }
    });
    usernameInput.addEventListener('input', () => localStorage.setItem(CLOUD_USER_KEY, usernameInput.value.trim()));
    select.addEventListener('change', () => {
        selectedCloudScenarioId = select.value || null;
    });
    document.getElementById('cloud-show-all').addEventListener('change', async () => {
        try {
            await refreshCloudScenarios({ silent: false });
        } catch (err) {
            setStatus(`Cloud-Refresh fehlgeschlagen: ${err.message}`);
        }
    });
    localSaveBtn.addEventListener('click', saveLocalScenario);
    localLoadBtn.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        await loadLocalScenarioFromFile(file);
        fileInput.value = '';
    });

    document.getElementById('btn-cloud-refresh').addEventListener('click', async () => {
        try {
            await refreshCloudScenarios();
        } catch (err) {
            setStatus(`Cloud-Refresh fehlgeschlagen: ${err.message}`);
        }
    });
    document.getElementById('btn-cloud-save').addEventListener('click', async () => {
        try {
            await saveCloudScenario();
        } catch (err) {
            setStatus(`Cloud-Speicherung fehlgeschlagen: ${err.message}`);
        }
    });
    document.getElementById('btn-cloud-load').addEventListener('click', async () => {
        try {
            await loadCloudScenario();
        } catch (err) {
            setStatus(`Cloud-Laden fehlgeschlagen: ${err.message}`);
        }
    });
    document.getElementById('btn-cloud-delete-admin').addEventListener('click', async () => {
        try {
            await deleteCloudScenarioAsAdmin();
        } catch (err) {
            setStatus(`Admin-Löschen fehlgeschlagen: ${err.message}`);
        }
    });

    updateScenarioSourceVisibility();
    if (sourceSelect.value === 'cloud') {
        refreshCloudScenarios({ silent: true }).catch(() => {
            renderCloudScenarioSelect();
        });
    } else {
        renderCloudScenarioSelect();
    }
}

function setupButtons() {
    document.getElementById('btn-faker').addEventListener('click', () => {
        const bl = document.getElementById('global-bundesland')?.value || null;
        state.patient = generateRandomPatient(bl);
        saveState();
        rebindAll();
        setStatus(
            `Stammdaten generiert: ${state.patient.givenName} ${state.patient.familyName}, SVNR ${state.patient.svnr}`,
        );
    });

    document.getElementById('btn-faker-doctor').addEventListener('click', () => {
        state.author = generateRandomDoctor();
        saveState();
        rebindAll();
        setStatus(`Arzt generiert: ${state.author.title} ${state.author.givenName} ${state.author.familyName}`);
    });

    document.getElementById('btn-reset').addEventListener('click', () => {
        if (!confirm('Formular auf Default-Werte zurücksetzen? (Aktuelle Eingaben gehen verloren)')) return;
        state = defaultState();
        saveState();
        rebindAll();
        setStatus('Formular zurückgesetzt.');
    });

    document.getElementById('btn-generate-xml').addEventListener('click', () => {
        const xml = buildEntlassungsbrief(state);
        const filename = `entlassungsbrief-${(state.patient.familyName || 'anonym').toLowerCase()}-${(state.documentDate || '').slice(0, 10)}.xml`;
        downloadFile(filename, xml, 'application/xml');
        setStatus(`XML generiert: ${filename}`);
    });

    document.getElementById('btn-generate').addEventListener('click', async () => {
        const xml = buildEntlassungsbrief(state);
        const xmlFilename = `entlassungsbrief-${(state.patient.familyName || 'anonym').toLowerCase()}-${(state.documentDate || '').slice(0, 10)}.xml`;
        const pdfFilename = xmlFilename.replace(/\.xml$/i, '.pdf');
        try {
            const pdfBlob = await apiPdf('/api/pdf', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ xml, fileName: pdfFilename }),
            });
            downloadBlob(pdfFilename, pdfBlob);
            setStatus(`PDF generiert: ${pdfFilename}`);
        } catch (err) {
            setStatus(`PDF-Generierung fehlgeschlagen: ${err.message}`);
        }
    });

}

function downloadFile(filename, content, mime) {
    const blob = new Blob([content], { type: mime });
    downloadBlob(filename, blob);
}

function downloadBlob(filename, blob) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
        URL.revokeObjectURL(url);
        a.remove();
    }, 100);
}

function rebindAll() {
    // Re-fülle Inputs mit aktuellem State
    document.querySelectorAll('[data-bind]').forEach((el) => {
        const path = el.getAttribute('data-bind');
        const v = getByPath(state, path);
        el.value = v ?? '';
    });
    Object.keys(LIST_TEMPLATES).forEach(renderList);
    updatePvVisibility();
}

// Patientenverfügung — Felder je nach Status ein-/ausblenden
function updatePvVisibility() {
    const sel = document.getElementById('pv-status');
    const details = document.getElementById('pv-details');
    if (!sel || !details) return;
    const hide = sel.value === 'keine' || sel.value === 'unbekannt';
    details.classList.toggle('hidden', hide);
}

function setupPvVisibility() {
    const sel = document.getElementById('pv-status');
    if (!sel) return;
    sel.addEventListener('change', updatePvVisibility);
    updatePvVisibility();
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
        state.organization.name = h.name;
        state.organization.phone = h.phone;
        state.organization.address.street = h.street;
        state.organization.address.houseNumber = h.houseNumber;
        state.organization.address.postalCode = h.postalCode;
        state.organization.address.city = h.city;
        state.organization.address.country = 'A';
        saveState();
        rebindAll();
        setStatus(`Krankenhaus ausgewählt: ${h.name}`);
    });
}

// Init ----------------------------------------------------------------------
function init() {
    document.getElementById('header-logo').src = LOGO_DATA_URI;
    bindInputs();
    Object.keys(LIST_TEMPLATES).forEach(renderList);
    setupListAddButtons();
    setupQuickAddDropdowns();
    setupButtons();
    setupScenarioManager();
    setupPvVisibility();
    setupHospitalSelector();
    const badge = document.getElementById('version-badge');
    const changelogDialog = document.getElementById('changelog-dialog');
    if (badge && changelogDialog) {
        badge.textContent = `v${APP_VERSION}`;
        badge.addEventListener('click', () => changelogDialog.showModal());
        document.getElementById('changelog-close')?.addEventListener('click', () => changelogDialog.close());
    }
}

init();
