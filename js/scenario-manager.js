// Scenario manager: save/load scenarios as local JSON files or in the cloud (by
// username, optionally across all users), and admin deletion.

import { getState, replaceState, defaultState } from './state.js';
import { sanitizeState } from './sanitize-state.js';
import { scenarioIdToUpdate } from './cloud-scenarios.js';
import { apiJson } from './api.js';
import { downloadFile } from './download.js';
import { report } from './ui-feedback.js';
import { rebindAll } from './form.js';

const CLOUD_USER_KEY = 'cda-uebung:cloud-username';
const SCENARIO_SOURCE_KEY = 'cda-uebung:scenario-source';

let cloudScenarios = [];
let selectedCloudScenarioId = null;
// The cloud scenario the current form state came from ({ id, username }), if any. Only
// this one is ever overwritten by a save — see scenarioIdToUpdate().
let loadedCloudScenario = null;

function cloudScenarioTitleSuggestion() {
    const state = getState();
    const family = (state.patient?.familyName || 'anonym').toLowerCase();
    const date = (state.documentDate || new Date().toISOString()).slice(0, 10);
    return `szenario-${family}-${date}`;
}

function saveLocalScenario() {
    const state = getState();
    const filename = `szenario-${(state.patient.familyName || 'anonym').toLowerCase()}-${new Date().toISOString().slice(0, 10)}.json`;
    downloadFile(filename, JSON.stringify(state, null, 2), 'application/json');
    report(`Lokales Szenario gespeichert: ${filename}`, 'success');
}

async function loadLocalScenarioFromFile(file) {
    if (!file) return;
    try {
        const text = await file.text();
        const loaded = JSON.parse(text);
        replaceState(sanitizeState(loaded, defaultState()));
        loadedCloudScenario = null;
        rebindAll();
        report(`Lokales Szenario geladen: ${file.name}`, 'success');
    } catch (err) {
        report(`Fehler beim lokalen Laden: ${err.message}`, 'error');
    }
}

export function getCloudUsername() {
    const input = document.getElementById('cloud-username');
    return input?.value?.trim() || '';
}

function isShowAll() {
    return document.getElementById('cloud-show-all')?.checked ?? false;
}

async function refreshCloudScenarios(options = {}) {
    const { silent = false } = options;

    if (isShowAll()) {
        const list = await apiJson('/api/scenarios/all');
        cloudScenarios = list;
        renderCloudScenarioSelect();
        if (!silent) {
            report(`Cloud-Liste aktualisiert: ${cloudScenarios.length} Szenario(s) von allen Benutzern.`, 'success');
        }
        return;
    }

    const username = getCloudUsername();
    if (!username) {
        cloudScenarios = [];
        renderCloudScenarioSelect();
        if (!silent) {
            report('Bitte zuerst einen Benutzernamen für Cloud-Szenarien eingeben.', 'info');
        }
        return;
    }
    const list = await apiJson(`/api/scenarios?username=${encodeURIComponent(username)}`);
    cloudScenarios = list;
    renderCloudScenarioSelect();
    if (!silent) {
        report(`Cloud-Liste aktualisiert: ${cloudScenarios.length} Szenario(s).`, 'success');
    }
}

function renderCloudScenarioSelect() {
    const select = document.getElementById('cloud-scenario-select');
    if (!select) return;
    const keepId = selectedCloudScenarioId;
    const showAll = isShowAll();
    select.innerHTML = '';

    if (!cloudScenarios.length) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'Keine Cloud-Szenarien vorhanden';
        select.appendChild(option);
        selectedCloudScenarioId = null;
        return;
    }

    cloudScenarios.forEach((scenario) => {
        const option = document.createElement('option');
        option.value = scenario.id;
        const prefix = showAll ? `[${scenario.username}] ` : '';
        option.textContent = `${prefix}${scenario.title} · ${scenario.updatedAt}`;
        if (keepId && keepId === scenario.id) option.selected = true;
        select.appendChild(option);
    });

    selectedCloudScenarioId = select.value || cloudScenarios[0].id;
    if (selectedCloudScenarioId) select.value = selectedCloudScenarioId;
}

async function saveCloudScenario({ asNew = false } = {}) {
    const username = getCloudUsername();
    if (!username) {
        report('Bitte zuerst einen Benutzernamen für Cloud-Speicherung eingeben.', 'info');
        return;
    }

    const defaultTitle = cloudScenarioTitleSuggestion();
    const title = prompt('Titel für das Cloud-Szenario:', defaultTitle);
    if (title === null) return;

    const id = scenarioIdToUpdate(loadedCloudScenario, username, { asNew });
    const payload = {
        id,
        username,
        title: title.trim() || defaultTitle,
        state: getState(),
    };

    const saved = await apiJson('/api/scenarios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
    });

    selectedCloudScenarioId = saved.id;
    loadedCloudScenario = { id: saved.id, username: saved.username };
    await refreshCloudScenarios({ silent: true });
    report(id ? `Cloud-Szenario aktualisiert: ${saved.title}` : `Neues Cloud-Szenario gespeichert: ${saved.title}`, 'success');
}

async function loadCloudScenario() {
    if (!selectedCloudScenarioId) {
        report('Bitte zuerst ein Cloud-Szenario auswählen.', 'info');
        return;
    }

    let url;
    if (isShowAll()) {
        url = `/api/scenarios/${encodeURIComponent(selectedCloudScenarioId)}`;
    } else {
        const username = getCloudUsername();
        if (!username) {
            report('Bitte zuerst einen Benutzernamen eingeben.', 'info');
            return;
        }
        url = `/api/scenarios/${encodeURIComponent(selectedCloudScenarioId)}?username=${encodeURIComponent(username)}`;
    }

    const detail = await apiJson(url, { headers: { 'X-Cda-User': getCloudUsername() } });
    replaceState(sanitizeState(detail.state, defaultState()));
    loadedCloudScenario = { id: detail.id, username: detail.username };
    rebindAll();
    report(`Cloud-Szenario geladen: ${detail.title}`, 'success');
}

function updateScenarioSourceVisibility() {
    const sourceSelect = document.getElementById('scenario-source');
    const cloudUsernameWrap = document.getElementById('cloud-username-wrap');
    const localActions = document.getElementById('scenario-local-actions');
    const cloudActions = document.getElementById('scenario-cloud-actions');
    const isCloud = sourceSelect.value === 'cloud';

    cloudUsernameWrap.classList.toggle('hidden', !isCloud);
    localActions.classList.toggle('hidden', isCloud);
    cloudActions.classList.toggle('hidden', !isCloud);
}

export function setupScenarioManager() {
    const sourceSelect = document.getElementById('scenario-source');
    const usernameInput = document.getElementById('cloud-username');
    const localSaveBtn = document.getElementById('btn-local-save');
    const localLoadBtn = document.getElementById('btn-local-load');
    const select = document.getElementById('cloud-scenario-select');
    const fileInput = document.getElementById('file-load');

    sourceSelect.value = localStorage.getItem(SCENARIO_SOURCE_KEY) || 'local';
    usernameInput.value = localStorage.getItem(CLOUD_USER_KEY) || '';

    sourceSelect.addEventListener('change', () => {
        localStorage.setItem(SCENARIO_SOURCE_KEY, sourceSelect.value);
        updateScenarioSourceVisibility();
        if (sourceSelect.value === 'cloud') {
            refreshCloudScenarios({ silent: true }).catch(() => {
                renderCloudScenarioSelect();
            });
        }
    });
    usernameInput.addEventListener('input', () => localStorage.setItem(CLOUD_USER_KEY, usernameInput.value.trim()));
    select.addEventListener('change', () => {
        selectedCloudScenarioId = select.value || null;
    });
    document.getElementById('cloud-show-all').addEventListener('change', async () => {
        try {
            await refreshCloudScenarios({ silent: false });
        } catch (err) {
            report(`Cloud-Refresh fehlgeschlagen: ${err.message}`, 'error');
        }
    });
    localSaveBtn.addEventListener('click', saveLocalScenario);
    localLoadBtn.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        await loadLocalScenarioFromFile(file);
        fileInput.value = '';
    });

    document.getElementById('btn-cloud-refresh').addEventListener('click', async () => {
        try {
            await refreshCloudScenarios();
        } catch (err) {
            report(`Cloud-Refresh fehlgeschlagen: ${err.message}`, 'error');
        }
    });
    const onCloudSave = (options) => async () => {
        try {
            await saveCloudScenario(options);
        } catch (err) {
            report(`Cloud-Speicherung fehlgeschlagen: ${err.message}`, 'error');
        }
    };
    document.getElementById('btn-cloud-save').addEventListener('click', onCloudSave());
    document.getElementById('btn-cloud-save-new').addEventListener('click', onCloudSave({ asNew: true }));
    document.getElementById('btn-cloud-load').addEventListener('click', async () => {
        try {
            await loadCloudScenario();
        } catch (err) {
            report(`Cloud-Laden fehlgeschlagen: ${err.message}`, 'error');
        }
    });

    updateScenarioSourceVisibility();
    if (sourceSelect.value === 'cloud') {
        refreshCloudScenarios({ silent: true }).catch(() => {
            renderCloudScenarioSelect();
        });
    } else {
        renderCloudScenarioSelect();
    }
}
