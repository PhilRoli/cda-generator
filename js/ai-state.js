// Maps the AI-fill response (/api/ai/fill → state) onto a complete, shape-safe form state.

import { defaultState } from './state.js';
import { sanitizeState } from './sanitize-state.js';
import { generateSvnr, generateRandomPatient } from './faker.js';
import { DEPARTMENTS, HOSPITALS_BY_BUNDESLAND } from './hospitals.js';

const PV_STATUS = ['unbekannt', 'keine', 'beachtlich', 'verbindlich', 'vorsorgevollmacht'];
const LIST_KEYS = ['diagnosen', 'vorerkrankungen', 'medikation', 'allergien', 'risikofaktoren'];
const TEXT_KEYS = ['aufnahmegrund', 'anamnese', 'verlauf', 'empfehlungen'];
// Internal-medicine sub-specialties are admitted to a general internal department when no hospital has their own.
const INTERNAL_SUBSPECIALTIES = ['KARDIOLOGIE', 'GASTROENTEROLOGIE', 'PULMOLOGIE', 'NEPHROLOGIE', 'ONKOLOGIE', 'GERIATRIE'];

const FALLBACK_BIRTH_DATE = '1960-01-01';

/** Parses YYYY-MM-DD; null for unparsable text and for impossible dates such as 1947-02-30 (Date would roll them over). */
function parseIsoDate(value) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || '');
    if (!m) return null;
    const date = new Date(+m[1], +m[2] - 1, +m[3]);
    const same = date.getFullYear() === +m[1] && date.getMonth() === +m[2] - 1 && date.getDate() === +m[3];
    return same ? date : null;
}

const isBlankRow = (row) => Object.values(row).every((v) => String(v).trim() === '');

/**
 * Picks a real hospital of the Bundesland that has the department; null if the Bundesland has none.
 * Fallbacks, in order: internal medicine for internal sub-specialties, then any hospital of the Bundesland.
 */
export function pickHospital(byBundesland, bundesland, department, random = Math.random) {
    const all = (bundesland && byBundesland[bundesland]) || [];
    const has = (label) => all.filter((h) => (h.departments || []).includes(label));
    let pool = has(DEPARTMENTS[department]);
    if (!pool.length && INTERNAL_SUBSPECIALTIES.includes(department)) pool = has(DEPARTMENTS.INNERE_MEDIZIN);
    if (!pool.length) pool = all;
    return pool.length ? pool[Math.floor(random() * pool.length)] : null;
}

const AGE = String.raw`\d{1,3}(?:\s*-?\s*jährig|\s+Jahre)`;
const BARE_TITLE = new RegExp(String.raw`\b(Herr|Herrn|Frau)(,?)\s+(?=${AGE})`, 'g');

/** "Herr 78 Jahre alt" / "Herr, 78 Jahre alt" -> "Herr Huber 78 Jahre alt" / "Herr Huber, 78 Jahre alt". */
export function nameBareTitles(text, familyName) {
    if (!text || !familyName) return text;
    return text.replace(BARE_TITLE, (_, title, comma) => `${title} ${familyName}${comma} `);
}

export function buildStateFromAi(aiState, { bundesland = '', hospitals = HOSPITALS_BY_BUNDESLAND, random = Math.random } = {}) {
    const next = sanitizeState(aiState, defaultState());
    if (!['M', 'F'].includes(next.patient.gender)) next.patient.gender = 'F';
    if (!PV_STATUS.includes(next.patientenverfuegung.status)) next.patientenverfuegung.status = 'unbekannt';
    for (const key of LIST_KEYS) next[key] = next[key].filter((row) => !isBlankRow(row));
    for (const key of TEXT_KEYS) next[key] = nameBareTitles(next[key], next.patient.familyName);

    const hospital = pickHospital(hospitals, bundesland, next.encounter.department, random);
    delete next.encounter.department; // steering field only, not part of the form state
    if (hospital) {
        next.organization.name = hospital.name;
        next.organization.phone = hospital.phone;
        next.organization.address = {
            street: hospital.street,
            houseNumber: hospital.houseNumber,
            postalCode: hospital.postalCode,
            city: hospital.city,
            country: 'A',
        };
    }
    next.patient.address = generateRandomPatient(bundesland || null).address;
    next.documentDate = next.encounter.dischargeDate;
    next.encounter.caseId = '';
    let birth = parseIsoDate(next.patient.birthDate);
    if (!birth) {
        // Keep the field and the SVNR's date part in agreement.
        next.patient.birthDate = FALLBACK_BIRTH_DATE;
        birth = parseIsoDate(FALLBACK_BIRTH_DATE);
    }
    next.patient.svnr = generateSvnr(birth);
    return next;
}

/**
 * Requests an AI fill and applies it, unless the dialog was closed while waiting
 * (the user may have kept editing the form in the meantime).
 * @returns {Promise<'applied' | 'discarded'>}
 */
export async function fillFromAi({ prompt, bundesland, headers, fetchJson, isDialogOpen, apply }) {
    const response = await fetchJson('/api/ai/fill', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...headers },
        body: JSON.stringify({ prompt }),
    });
    if (!isDialogOpen()) return 'discarded';
    apply(buildStateFromAi(response.state, { bundesland }));
    return 'applied';
}
