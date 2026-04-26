// UI-Logik: Form-State <-> DOM, Tabs, Listen-Items, Persistenz, Generierung.

import { generateRandomPatient } from './faker.js';
import { buildEntlassungsbrief } from './doctype-entlassung.js';
import { LOGO_DATA_URI } from './logo-base64.js';

const STORAGE_KEY = 'cda-uebung:last';

const TABS = [
    { id: 'patient', label: 'Patient' },
    { id: 'aufenthalt', label: 'Aufenthalt & Arzt' },
    { id: 'brieftext', label: 'Brieftext' },
    { id: 'aufnahmegrund', label: 'Aufnahmegrund' },
    { id: 'diagnosen', label: 'Diagnosen' },
    { id: 'anamnese', label: 'Anamnese / Vorerkrankungen' },
    { id: 'verlauf', label: 'Verlauf' },
    { id: 'medikation', label: 'Medikation' },
    { id: 'empfehlungen', label: 'Empfehlungen' },
];

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
        },
        brieftext: { text: '' },
        aufnahmegrund:
            'Stationäre Aufnahme nach häuslichem Sturz mit Verdacht auf Schenkelhalsfraktur links. Patientin wurde durch den Notarzt zugewiesen.',
        diagnosen: [
            { text: 'Mediale Schenkelhalsfraktur links', icd: 'S72.00', side: 'links' },
            { text: 'Arterielle Hypertonie', icd: 'I10', side: '' },
            { text: 'Diabetes mellitus Typ 2, medikamentös eingestellt', icd: 'E11.9', side: '' },
        ],
        vorerkrankungen: [
            { text: 'Arterielle Hypertonie', since: '2008', note: 'unter ACE-Hemmer eingestellt' },
            { text: 'Diabetes mellitus Typ 2', since: '2015', note: 'orale Antidiabetika' },
            { text: 'Z.n. Cholezystektomie', since: '2012', note: '' },
        ],
        anamnese:
            'Patientin wohnt allein in Erdgeschosswohnung, ist bisher selbstständig mobil. Sturz beim Aufstehen aus dem Sessel, kein Bewusstseinsverlust, keine Synkope erinnerlich. Schmerzen unmittelbar im linken Hüftbereich, keine Belastbarkeit mehr.',
        verlauf:
            'Bei Aufnahme klinisch und radiologisch Bestätigung der medialen Schenkelhalsfraktur links. Indikation zur operativen Versorgung gestellt. Am Folgetag Implantation einer zementierten Hüfttotalendoprothese links in Spinalanästhesie, intraoperativer Verlauf unauffällig. Postoperativ rasche Mobilisation an zwei Unterarmstützen unter physiotherapeutischer Anleitung. Wundverhältnisse reizlos, Drainagezug am 2. postoperativen Tag.\n\nUnter Thromboseprophylaxe mit niedermolekularem Heparin keine Komplikationen. Blutzucker stabil. Vorbestehende antihypertensive Therapie unverändert fortgeführt.',
        medikation: [
            {
                wirkstoff: 'Enoxaparin',
                handelsname: 'Lovenox 40mg',
                staerke: '40 mg',
                schema: '0-0-0-1 s.c.',
                bemerkung: 'für 4 Wochen postoperativ',
            },
            {
                wirkstoff: 'Pantoprazol',
                handelsname: 'Pantoloc',
                staerke: '40 mg',
                schema: '1-0-0',
                bemerkung: 'morgens nüchtern',
            },
            { wirkstoff: 'Ramipril', handelsname: 'Tritace', staerke: '5 mg', schema: '1-0-0', bemerkung: 'unverändert' },
            {
                wirkstoff: 'Metformin',
                handelsname: 'Glucophage',
                staerke: '850 mg',
                schema: '1-0-1',
                bemerkung: 'unverändert',
            },
            {
                wirkstoff: 'Paracetamol',
                handelsname: 'Mexalen',
                staerke: '500 mg',
                schema: '1-1-1-1',
                bemerkung: 'bei Schmerzen',
            },
            {
                wirkstoff: 'Tramadol',
                handelsname: 'Tramal Tropfen',
                staerke: '20 Tr.',
                schema: 'b.B.',
                bemerkung: 'maximal 4×/Tag bei stärkeren Schmerzen',
            },
        ],
        empfehlungen:
            'Mobilisation an zwei Unterarmstützen unter Teilbelastung links für 6 Wochen. Physiotherapie ambulant fortführen. Wundkontrolle und Fadenzug beim Hausarzt am 14. postoperativen Tag.\n\nKlinische Kontrolle in unserer orthopädischen Ambulanz in 6 Wochen mit Röntgenkontrolle.\n\nThromboseprophylaxe mit Lovenox 40mg s.c. einmal täglich für 4 Wochen.\n\nBei Fieber, zunehmenden Schmerzen, Wundsekretion oder Rötung im OP-Bereich umgehende Wiedervorstellung.',
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

// Tabs ----------------------------------------------------------------------
function setupTabs() {
    const nav = document.getElementById('tabs');
    const panels = document.querySelectorAll('section.tab-panel');
    TABS.forEach((tab, idx) => {
        const btn = document.createElement('button');
        btn.textContent = tab.label;
        btn.dataset.tabId = tab.id;
        if (idx === 0) btn.classList.add('active');
        btn.addEventListener('click', () => {
            document.querySelectorAll('nav.tabs button').forEach((b) => b.classList.remove('active'));
            btn.classList.add('active');
            panels.forEach((p) => p.classList.toggle('active', p.dataset.tab === tab.id));
        });
        nav.appendChild(btn);
    });
    panels.forEach((p, i) => p.classList.toggle('active', i === 0));
}

// Listen-Items (Diagnosen, Vorerkrankungen, Medikation) ---------------------
const LIST_TEMPLATES = {
    diagnosen: {
        fields: [
            { key: 'text', label: 'Diagnose', type: 'text' },
            { key: 'icd', label: 'ICD-10', type: 'text' },
            { key: 'side', label: 'Seitigkeit', type: 'text' },
        ],
        grid: 'grid-diag',
        empty: { text: '', icd: '', side: '' },
    },
    vorerkrankungen: {
        fields: [
            { key: 'text', label: 'Vorerkrankung', type: 'text' },
            { key: 'since', label: 'Seit', type: 'text' },
            { key: 'note', label: 'Bemerkung', type: 'text' },
        ],
        grid: 'grid-vorerk',
        empty: { text: '', since: '', note: '' },
    },
    medikation: {
        fields: [
            { key: 'wirkstoff', label: 'Wirkstoff', type: 'text' },
            { key: 'handelsname', label: 'Handelsname', type: 'text' },
            { key: 'staerke', label: 'Stärke', type: 'text' },
            { key: 'schema', label: 'Schema', type: 'text' },
            { key: 'bemerkung', label: 'Bemerkung', type: 'text' },
        ],
        grid: 'grid-medi',
        empty: { wirkstoff: '', handelsname: '', staerke: '', schema: '', bemerkung: '' },
    },
};

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
            if (!state[name]) state[name] = [];
            state[name].push({ ...tpl.empty });
            saveState();
            renderList(name);
        });
    });
}

// Buttons -------------------------------------------------------------------
function setStatus(msg) {
    document.getElementById('status').textContent = msg || '';
}

function setupButtons() {
    document.getElementById('btn-faker').addEventListener('click', () => {
        state.patient = generateRandomPatient();
        saveState();
        rebindAll();
        setStatus(
            `Stammdaten generiert: ${state.patient.givenName} ${state.patient.familyName}, SVNR ${state.patient.svnr}`,
        );
    });

    document.getElementById('btn-reset').addEventListener('click', () => {
        if (!confirm('Formular auf Default-Werte zurücksetzen? (Aktuelle Eingaben gehen verloren)')) return;
        state = defaultState();
        saveState();
        rebindAll();
        setStatus('Formular zurückgesetzt.');
    });

    document.getElementById('btn-generate').addEventListener('click', () => {
        const xml = buildEntlassungsbrief(state);
        const filename = `entlassungsbrief-${(state.patient.familyName || 'anonym').toLowerCase()}-${(state.documentDate || '').slice(0, 10)}.xml`;
        downloadFile(filename, xml, 'application/xml');
        setStatus(`Generiert: ${filename}. Datei in den Ordner mit elga-stylesheet-uebung.xsl legen und im Browser öffnen.`);
    });

    document.getElementById('btn-save').addEventListener('click', () => {
        const filename = `szenario-${(state.patient.familyName || 'anonym').toLowerCase()}-${new Date().toISOString().slice(0, 10)}.json`;
        downloadFile(filename, JSON.stringify(state, null, 2), 'application/json');
        setStatus(`Szenario gespeichert: ${filename}`);
    });

    const fileInput = document.getElementById('file-load');
    document.getElementById('btn-load').addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        if (!file) return;
        try {
            const text = await file.text();
            const loaded = JSON.parse(text);
            state = Object.assign(defaultState(), loaded);
            saveState();
            rebindAll();
            setStatus(`Szenario geladen: ${file.name}`);
        } catch (err) {
            setStatus(`Fehler beim Laden: ${err.message}`);
        }
        fileInput.value = '';
    });
}

function downloadFile(filename, content, mime) {
    const blob = new Blob([content], { type: mime });
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
}

// Init ----------------------------------------------------------------------
function init() {
    document.getElementById('header-logo').src = LOGO_DATA_URI;
    setupTabs();
    bindInputs();
    Object.keys(LIST_TEMPLATES).forEach(renderList);
    setupListAddButtons();
    setupButtons();
}

init();
