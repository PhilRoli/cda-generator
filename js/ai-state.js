// Maps the AI-fill response (/api/ai/fill → state) onto a complete, shape-safe form state.

import { defaultState } from './state.js';
import { sanitizeState } from './sanitize-state.js';
import { generateSvnr } from './faker.js';

const PV_STATUS = ['unbekannt', 'keine', 'beachtlich', 'verbindlich', 'vorsorgevollmacht'];
const LIST_KEYS = ['diagnosen', 'vorerkrankungen', 'medikation', 'allergien', 'risikofaktoren'];

function parseIsoDate(value) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value || '');
    const date = m ? new Date(+m[1], +m[2] - 1, +m[3]) : null;
    return date && !Number.isNaN(date.getTime()) ? date : new Date(1960, 0, 1);
}

const isBlankRow = (row) => Object.values(row).every((v) => String(v).trim() === '');

export function buildStateFromAi(aiState) {
    const next = sanitizeState(aiState, defaultState());
    if (!['M', 'F'].includes(next.patient.gender)) next.patient.gender = 'F';
    if (!PV_STATUS.includes(next.patientenverfuegung.status)) next.patientenverfuegung.status = 'unbekannt';
    for (const key of LIST_KEYS) next[key] = next[key].filter((row) => !isBlankRow(row));
    next.documentDate = next.encounter.dischargeDate;
    next.encounter.caseId = '';
    next.patient.svnr = generateSvnr(parseIsoDate(next.patient.birthDate));
    return next;
}
