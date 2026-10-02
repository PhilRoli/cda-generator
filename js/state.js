// Form state: defaults, persistence and the single shared state object.

import { sanitizeState } from './sanitize-state.js';

const STORAGE_KEY = 'cda-uebung:last';

// Sinnvolle Default-Inhalte (RD-Übungs-tauglich, leicht anpassbar)
export function defaultState() {
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

let state = loadState();

/** The current form state. Read it fresh on every use: replaceState() swaps the object. */
export function getState() {
    return state;
}

/** Replaces the whole state (load, reset) and persists it. */
export function replaceState(next) {
    state = next;
    saveState();
}

function loadState() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (raw) return sanitizeState(JSON.parse(raw), defaultState());
    } catch {}
    return defaultState();
}

export function saveState() {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {}
}

export function getByPath(obj, path) {
    return path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj);
}

export function setByPath(obj, path, value) {
    const parts = path.split('.');
    let cur = obj;
    for (let i = 0; i < parts.length - 1; i++) {
        if (cur[parts[i]] == null) cur[parts[i]] = {};
        cur = cur[parts[i]];
    }
    cur[parts[parts.length - 1]] = value;
}
