// Daten tab: export/import of all scenarios and the last automatic backup.

import { adminApiJson } from '../admin-token.js';
import { downloadFile } from '../download.js';
import { report, withButtonBusy } from '../ui-feedback.js';

export function initDataTab(root) {
    root.innerHTML = `
        <div class="admin-card">
            <h2>Export / Import</h2>
            <div class="scenario-row">
                <button class="btn" id="admin-export">Export herunterladen</button>
                <button class="btn" id="admin-import">Import …</button>
                <input type="file" id="admin-import-file" accept="application/json" class="hidden-file-input" />
            </div>
            <p class="admin-note">Der Import überschreibt Szenarien mit gleicher ID und legt fehlende neu an.</p>
        </div>
        <div class="admin-card">
            <h2>Automatisches Backup</h2>
            <p id="admin-backup-status">–</p>
        </div>`;

    const exportBtn = root.querySelector('#admin-export');
    const importBtn = root.querySelector('#admin-import');
    const fileInput = root.querySelector('#admin-import-file');
    const backupStatus = root.querySelector('#admin-backup-status');

    exportBtn.addEventListener('click', () => withButtonBusy(exportBtn, 'wird exportiert…', async () => {
        try {
            const data = await adminApiJson('/api/admin/scenarios/export');
            downloadFile(`cda-szenarien-${new Date().toISOString().slice(0, 10)}.json`,
                JSON.stringify(data, null, 2), 'application/json');
            report(`${data.count} Szenario(s) exportiert.`, 'success');
        } catch (err) {
            if (!err.cancelled) report(`Export fehlgeschlagen: ${err.message}`, 'error');
        }
    }));

    importBtn.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async () => {
        const file = fileInput.files[0];
        fileInput.value = '';
        if (!file) return;
        let scenarios;
        try {
            scenarios = JSON.parse(await file.text()).scenarios;
            if (!Array.isArray(scenarios)) throw new Error('Kein "scenarios"-Array in der Datei.');
        } catch (err) {
            report(`Datei ungültig: ${err.message}`, 'error');
            return;
        }
        if (!confirm(`${scenarios.length} Szenario(s) aus „${file.name}" importieren?`)) return;
        try {
            const result = await adminApiJson('/api/admin/scenarios/import', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ scenarios }),
            });
            report(`${result.imported} Szenario(s) importiert.`, 'success');
        } catch (err) {
            if (!err.cancelled) report(`Import fehlgeschlagen: ${err.message}`, 'error');
        }
    });

    const refresh = async () => {
        try {
            const s = await adminApiJson('/api/admin/backup/status');
            backupStatus.textContent = s.file
                ? `Letztes Backup: ${s.writtenAt.slice(0, 16).replace('T', ' ')} UTC · ${s.count} Szenario(s) · ${s.file}`
                : 'Noch kein automatisches Backup vorhanden.';
        } catch (err) {
            if (!err.cancelled) backupStatus.textContent = `Status nicht verfügbar: ${err.message}`;
        }
    };
    return { refresh };
}
