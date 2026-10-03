// Szenarien tab: all cloud scenarios with filter, JSON view and delete.

import { adminApiJson } from '../admin-token.js';
import { apiJson } from '../api.js';
import { cloudUserHeader } from '../cloud-scenarios.js';
import { report } from '../ui-feedback.js';

function cell(text, className) {
    const td = document.createElement('td');
    td.textContent = text;
    if (className) td.className = className;
    return td;
}

function button(label, className, onClick) {
    const b = document.createElement('button');
    b.className = `btn ${className || ''}`.trim();
    b.textContent = label;
    b.addEventListener('click', onClick);
    return b;
}

export function initScenariosTab(root) {
    root.innerHTML = '';
    const card = document.createElement('div');
    card.className = 'admin-card';
    const heading = document.createElement('h2');
    heading.textContent = 'Cloud-Szenarien';
    const filter = document.createElement('input');
    filter.type = 'text';
    filter.className = 'admin-filter';
    filter.placeholder = 'Filtern nach Titel oder Benutzer …';
    filter.setAttribute('aria-label', 'Szenarien filtern');
    const table = document.createElement('table');
    table.className = 'admin-table';
    card.append(heading, filter, table);
    root.appendChild(card);

    let scenarios = [];

    const render = () => {
        const q = filter.value.trim().toLowerCase();
        table.innerHTML = '';
        const head = document.createElement('tr');
        ['Titel', 'Benutzer', 'Geändert', ''].forEach((h) => {
            const th = document.createElement('th');
            th.textContent = h;
            head.appendChild(th);
        });
        table.appendChild(head);
        scenarios
            .filter((s) => !q || s.title.toLowerCase().includes(q) || s.username.toLowerCase().includes(q))
            .forEach((s) => {
                const tr = document.createElement('tr');
                const actions = document.createElement('td');
                actions.append(
                    button('Öffnen', '', () => openJson(s)),
                    button('Löschen', 'danger', () => remove(s)),
                );
                tr.append(cell(s.title), cell(s.username), cell(s.updatedAt.slice(0, 16).replace('T', ' ')), actions);
                table.appendChild(tr);
            });
    };

    const load = async () => {
        try {
            scenarios = await adminApiJson('/api/admin/scenarios');
            render();
        } catch (err) {
            if (!err.cancelled) report(`Szenarien konnten nicht geladen werden: ${err.message}`, 'error');
        }
    };

    const openJson = async (s) => {
        try {
            const detail = await apiJson(`/api/scenarios/${encodeURIComponent(s.id)}`, {
                headers: cloudUserHeader('(admin)'),
            });
            document.getElementById('scenario-json-title').textContent = s.title;
            document.getElementById('scenario-json-body').textContent = JSON.stringify(detail.state, null, 2);
            document.getElementById('scenario-json-dialog').showModal();
        } catch (err) {
            report(`Szenario konnte nicht geöffnet werden: ${err.message}`, 'error');
        }
    };

    const remove = async (s) => {
        if (!confirm(`Szenario „${s.title}" von ${s.username} löschen?`)) return;
        try {
            await adminApiJson(`/api/admin/scenarios/${encodeURIComponent(s.id)}`, { method: 'DELETE' });
            report(`Szenario gelöscht: ${s.title}`, 'success');
            await load();
        } catch (err) {
            if (!err.cancelled) report(`Löschen fehlgeschlagen: ${err.message}`, 'error');
        }
    };

    filter.addEventListener('input', render);
    document.getElementById('scenario-json-close').addEventListener('click', () =>
        document.getElementById('scenario-json-dialog').close());
    return { refresh: load };
}
