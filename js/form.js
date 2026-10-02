// Form bindings: data-bind inputs <-> state, SVNR validation, Patientenverfügung toggling.

import { isValidSvnr } from './faker.js';
import { getState, saveState, getByPath, setByPath } from './state.js';
import { renderAllLists } from './lists.js';

export function bindInputs() {
    document.querySelectorAll('[data-bind]').forEach((el) => {
        const path = el.getAttribute('data-bind');
        const v = getByPath(getState(), path);
        if (v !== undefined) el.value = v;
        el.addEventListener('input', () => {
            setByPath(getState(), path, el.value);
            saveState();
        });
    });
}

/** Returns true when the SVNR is acceptable for generation: empty OR structurally valid. */
export function svnrIsAcceptable(svnr) {
    return !svnr || isValidSvnr(svnr);
}

export function updateSvnrMarking() {
    const input = document.querySelector('[data-bind="patient.svnr"]');
    if (!input) return;
    const svnr = input.value;
    const invalid = svnr && !isValidSvnr(svnr);
    input.classList.toggle('invalid', !!invalid);
    // Expose the error to assistive tech too, not just as a red outline.
    input.setAttribute('aria-invalid', invalid ? 'true' : 'false');
    let hint = input.parentElement?.querySelector('.svnr-hint');
    if (invalid) {
        if (!hint) {
            hint = document.createElement('span');
            hint.className = 'svnr-hint';
            hint.id = 'svnr-hint';
            hint.textContent = '10-stellig mit gültiger Prüfziffer erforderlich';
            input.insertAdjacentElement('afterend', hint);
        }
        input.setAttribute('aria-describedby', hint.id);
    } else {
        hint?.remove();
        input.removeAttribute('aria-describedby');
    }
}

export function setupSvnrValidation() {
    const input = document.querySelector('[data-bind="patient.svnr"]');
    if (!input) return;
    input.addEventListener('input', updateSvnrMarking);
    input.addEventListener('blur', updateSvnrMarking);
    updateSvnrMarking();
}

/** Re-renders every bound input and list from the current state (after load/reset). */
export function rebindAll() {
    // Re-fülle Inputs mit aktuellem State
    document.querySelectorAll('[data-bind]').forEach((el) => {
        const path = el.getAttribute('data-bind');
        const v = getByPath(getState(), path);
        el.value = v ?? '';
    });
    renderAllLists();
    updatePvVisibility();
    updateSvnrMarking();
}

// Patientenverfügung — Felder je nach Status ein-/ausblenden
function updatePvVisibility() {
    const sel = document.getElementById('pv-status');
    const details = document.getElementById('pv-details');
    if (!sel || !details) return;
    const hide = sel.value === 'keine' || sel.value === 'unbekannt';
    details.classList.toggle('hidden', hide);
}

export function setupPvVisibility() {
    const sel = document.getElementById('pv-status');
    if (!sel) return;
    sel.addEventListener('change', updatePvVisibility);
    updatePvVisibility();
}
