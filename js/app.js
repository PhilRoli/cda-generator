// UI-Logik: Form-State <-> DOM, Tabs, Listen-Items, Persistenz, Generierung.

import { generateRandomPatient, generateRandomDoctor } from './faker.js';
import { buildEntlassungsbrief } from './doctype-entlassung.js';
import { LOGO_DATA_URI } from './logo-base64.js';

const STORAGE_KEY = 'cda-uebung:last';

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

// Init ----------------------------------------------------------------------
function init() {
    document.getElementById('header-logo').src = LOGO_DATA_URI;
    bindInputs();
    Object.keys(LIST_TEMPLATES).forEach(renderList);
    setupListAddButtons();
    setupButtons();
    setupPvVisibility();
}

init();
