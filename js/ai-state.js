// Maps the AI-fill response (/api/ai/fill → state) onto a complete, shape-safe form state.

import { defaultState } from './state.js';
import { sanitizeState } from './sanitize-state.js';
import { generateSvnr } from './faker.js';

const PV_STATUS = ['unbekannt', 'keine', 'beachtlich', 'verbindlich', 'vorsorgevollmacht'];
const LIST_KEYS = ['diagnosen', 'vorerkrankungen', 'medikation', 'allergien', 'risikofaktoren'];

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

export function buildStateFromAi(aiState) {
    const next = sanitizeState(aiState, defaultState());
    if (!['M', 'F'].includes(next.patient.gender)) next.patient.gender = 'F';
    if (!PV_STATUS.includes(next.patientenverfuegung.status)) next.patientenverfuegung.status = 'unbekannt';
    for (const key of LIST_KEYS) next[key] = next[key].filter((row) => !isBlankRow(row));
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
export async function fillFromAi({ prompt, headers, fetchJson, isDialogOpen, apply }) {
    const response = await fetchJson('/api/ai/fill', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...headers },
        body: JSON.stringify({ prompt }),
    });
    if (!isDialogOpen()) return 'discarded';
    apply(buildStateFromAi(response.state));
    return 'applied';
}
