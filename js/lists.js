// Editable list sections (Diagnosen, Vorerkrankungen, Medikation, …) with quick-add presets.

import { getState, saveState } from './state.js';
import { report } from './ui-feedback.js';

export const LIST_TEMPLATES = {
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
    return (getState()[name] || []).some((entry) => fields.every((key) => normalize(entry[key]) === normalize(candidate[key])));
}

function addListItem(name, item) {
    const tpl = LIST_TEMPLATES[name];
    if (!tpl) return;
    const state = getState();
    if (!state[name]) state[name] = [];
    state[name].push({ ...item });
    saveState();
    renderList(name);
}

export function renderList(name) {
    const tpl = LIST_TEMPLATES[name];
    const container = document.getElementById(`${name}-list`);
    if (!container || !tpl) return;
    container.innerHTML = '';
    const state = getState();
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
        // The visible "✕" means nothing to a screen reader; name the row it removes.
        removeBtn.setAttribute('aria-label', `Eintrag ${idx + 1} entfernen: ${listItemLabel(name, item) || 'leer'}`);
        removeBtn.textContent = '✕';
        removeBtn.addEventListener('click', () => {
            getState()[name].splice(idx, 1);
            saveState();
            renderList(name);
        });
        if (idx === 0) {
            // Header-Spacer
            const lbl = document.createElement('label');
            const span = document.createElement('span');
            span.className = 'label-text';
            span.textContent = '\u00a0';
            lbl.appendChild(span);
            lbl.appendChild(removeBtn);
            row.appendChild(lbl);
        } else {
            row.appendChild(removeBtn);
        }
        container.appendChild(row);
    });
}

export function setupListAddButtons() {
    document.querySelectorAll('[data-add]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const name = btn.getAttribute('data-add');
            const tpl = LIST_TEMPLATES[name];
            if (!tpl) return;
            addListItem(name, tpl.empty);
        });
    });
}

export function setupQuickAddDropdowns() {
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
                report(`Eintrag bereits vorhanden: ${listItemLabel(name, selectedEntry)}`, 'info');
                select.value = '';
                return;
            }
            addListItem(name, selectedEntry);
            report(`Standardwert hinzugefügt: ${listItemLabel(name, selectedEntry)}`, 'success');
            select.value = '';
        });
        const actionRow = document.createElement('div');
        actionRow.className = 'quick-add-actions';
        btn.insertAdjacentElement('beforebegin', actionRow);
        actionRow.appendChild(select);
        actionRow.appendChild(btn);
    });
}

export function renderAllLists() {
    Object.keys(LIST_TEMPLATES).forEach(renderList);
}
