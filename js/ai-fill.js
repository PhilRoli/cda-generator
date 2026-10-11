// "Mit KI ausfüllen" (BETA): dialog that turns a short description into a filled form.

import { apiJson } from './api.js';
import { cloudUserHeader } from './cloud-scenarios.js';
import { getCloudUsername } from './scenario-manager.js';
import { replaceState } from './state.js';
import { rebindAll } from './form.js';
import { report, withButtonBusy } from './ui-feedback.js';
import { buildStateFromAi } from './ai-state.js';

export async function setupAiFill() {
    const openBtn = document.getElementById('btn-open-ai-fill');
    const dialog = document.getElementById('ai-fill-dialog');
    if (!openBtn || !dialog) return;

    try {
        const status = await apiJson('/api/ai/status');
        openBtn.classList.toggle('hidden', !status?.enabled);
    } catch {
        openBtn.classList.add('hidden'); // backend without the feature: keep the button hidden
    }

    const textarea = document.getElementById('ai-fill-prompt');
    const counter = document.getElementById('ai-fill-counter');
    const submit = document.getElementById('btn-ai-fill-submit');
    const max = Number(textarea.getAttribute('maxlength')) || 1000;

    const updateCounter = () => {
        counter.textContent = `${textarea.value.length} / ${max}`;
        submit.disabled = textarea.value.trim() === '';
    };
    textarea.addEventListener('input', updateCounter);
    updateCounter();

    submit.addEventListener('click', async () => {
        if (!window.confirm('Das aktuelle Formular wird mit KI-generierten Daten überschrieben. Fortfahren?')) return;
        await withButtonBusy(submit, 'KI arbeitet…', async () => {
            try {
                const response = await apiJson('/api/ai/fill', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', ...cloudUserHeader(getCloudUsername()) },
                    body: JSON.stringify({ prompt: textarea.value.trim() }),
                });
                replaceState(buildStateFromAi(response.state));
                rebindAll();
                dialog.close();
                report('Formular per KI ausgefüllt (BETA). Bitte alle Inhalte prüfen.', 'success');
            } catch (err) {
                report(`KI-Ausfüllen fehlgeschlagen: ${err.message}`, 'error');
            }
        });
        updateCounter(); // withButtonBusy re-enables the button; restore the empty-input gate
    });
}
