// "Mit KI ausfüllen" (BETA): dialog that turns a short description into a filled form.

import { apiJson } from './api.js';
import { cloudUserHeader } from './cloud-scenarios.js';
import { getCloudUsername } from './scenario-manager.js';
import { replaceState } from './state.js';
import { rebindAll } from './form.js';
import { report, withButtonBusy } from './ui-feedback.js';
import { fillFromAi } from './ai-state.js';

export async function setupAiFill() {
    const openBtn = document.getElementById('btn-open-ai-fill');
    const dialog = document.getElementById('ai-fill-dialog');
    if (!openBtn || !dialog) return;

    const textarea = document.getElementById('ai-fill-prompt');
    const blSelect = document.getElementById('ai-fill-bundesland');
    const globalBl = document.getElementById('global-bundesland');
    // Same options as the form's own Bundesland select; the placeholder option (value "") stays.
    globalBl.querySelectorAll('option').forEach((o) => blSelect.appendChild(o.cloneNode(true)));
    const counter = document.getElementById('ai-fill-counter');
    const submit = document.getElementById('btn-ai-fill-submit');

    try {
        const status = await apiJson('/api/ai/status');
        openBtn.classList.toggle('hidden', !status?.enabled);
        if (status?.maxPromptChars > 0) textarea.maxLength = status.maxPromptChars; // server limit wins over the markup default
    } catch {
        openBtn.classList.add('hidden'); // backend without the feature: keep the button hidden
    }
    const max = textarea.maxLength > 0 ? textarea.maxLength : 1000;

    const updateCounter = () => {
        counter.textContent = `${textarea.value.length} / ${max}`;
        submit.disabled = textarea.value.trim() === '' || blSelect.value === '';
    };
    textarea.addEventListener('input', updateCounter);
    openBtn.addEventListener('click', () => {
        blSelect.value = globalBl.value; // start from what the form already has
        updateCounter();
    });
    blSelect.addEventListener('change', () => {
        // Keep the form's Bundesland (and its hospital dropdown) in sync with the choice made here.
        globalBl.value = blSelect.value;
        globalBl.dispatchEvent(new Event('change'));
        updateCounter();
    });
    updateCounter();

    submit.addEventListener('click', async () => {
        if (!window.confirm('Das aktuelle Formular wird mit KI-generierten Daten überschrieben. Fortfahren?')) return;
        await withButtonBusy(submit, 'KI arbeitet…', async () => {
            try {
                const outcome = await fillFromAi({
                    prompt: textarea.value.trim(),
                    bundesland: blSelect.value,
                    headers: cloudUserHeader(getCloudUsername()),
                    fetchJson: apiJson,
                    isDialogOpen: () => dialog.open,
                    apply: (state) => {
                        replaceState(state);
                        rebindAll();
                    },
                });
                if (outcome === 'discarded') {
                    report('KI-Ergebnis verworfen, da der Dialog geschlossen wurde.', 'info');
                    return;
                }
                dialog.close();
                report('Formular per KI ausgefüllt (BETA). Bitte alle Inhalte prüfen.', 'success');
            } catch (err) {
                report(`KI-Ausfüllen fehlgeschlagen: ${err.message}`, 'error');
            }
        });
        updateCounter(); // withButtonBusy re-enables the button; restore the empty-input gate
    });
}
