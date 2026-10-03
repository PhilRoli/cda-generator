// Statistik tab: data shaping (pure, unit-tested) and rendering.

import { adminApiJson } from '../admin-token.js';
import { report } from '../ui-feedback.js';
import { renderStackedBars } from './chart.js';

export const TYPE_LABELS = { pdf: 'PDFs', clean_pdf: 'Saubere PDFs', xml_download: 'XML-Downloads' };
const GENERATION_TYPES = Object.keys(TYPE_LABELS);

export const REASON_LABELS = {
    busy_or_timeout: 'Ausgelastet / Timeout',
    bad_request: 'Ungültige Anfrage',
    too_large: 'Zu groß',
    throttled: 'Zu viele Versuche',
    forbidden: 'Nicht berechtigt',
    server_error: 'Serverfehler',
    client_error: 'Anfragefehler',
};

const TYPE_NAMES = { ...TYPE_LABELS, scenario_save: 'Szenario gespeichert', scenario_load: 'Szenario geladen' };

export function delta(current, previous) {
    if (previous === 0) return { text: current ? 'neu' : '±0', worse: false };
    const pct = Math.round(((current - previous) / previous) * 100);
    if (pct === 0) return { text: '±0 %', worse: false };
    return { text: `${pct > 0 ? '+' : '−'}${Math.abs(pct)} %`, worse: false };
}

export function kpis(summary) {
    const tiles = GENERATION_TYPES.map((type) => ({
        label: TYPE_LABELS[type],
        value: summary.current[type].ok,
        delta: delta(summary.current[type].ok, summary.previous[type].ok),
    }));
    const failedNow = GENERATION_TYPES.reduce((n, t) => n + summary.current[t].failed, 0);
    const failedBefore = GENERATION_TYPES.reduce((n, t) => n + summary.previous[t].failed, 0);
    const attempts = GENERATION_TYPES.reduce((n, t) => n + summary.current[t].ok + summary.current[t].failed, 0);
    const rate = attempts ? ((failedNow / attempts) * 100).toFixed(1).replace('.', ',') : '0,0';
    tiles.push({
        label: `Fehler (${rate} %)`,
        value: failedNow,
        delta: { ...delta(failedNow, failedBefore), worse: failedNow > failedBefore },
    });
    return tiles;
}

export function chartSeries(timeline) {
    const days = timeline.entries.map((e) => e.day);
    const series = GENERATION_TYPES.map((key) => ({
        key,
        label: TYPE_LABELS[key],
        values: timeline.entries.map((e) => e.ok[key] || 0),
    }));
    const failed = timeline.entries.map((e) => e.failed || 0);
    const totals = days.map((_, i) => series.reduce((n, s) => n + s.values[i], 0));
    return { days, series, failed, max: Math.max(1, ...totals) };
}

// --- rendering -----------------------------------------------------------------

function card(title) {
    const c = document.createElement('div');
    c.className = 'admin-card';
    const h = document.createElement('h2');
    h.textContent = title;
    c.appendChild(h);
    return c;
}

function table(headers, rows, numericFrom = 1) {
    const t = document.createElement('table');
    t.className = 'admin-table';
    const head = document.createElement('tr');
    headers.forEach((h, i) => {
        const th = document.createElement('th');
        th.textContent = h;
        if (i >= numericFrom) th.className = 'num';
        head.appendChild(th);
    });
    t.appendChild(head);
    rows.forEach((r) => {
        const tr = document.createElement('tr');
        r.forEach((v, i) => {
            const td = document.createElement('td');
            td.textContent = String(v);
            if (i >= numericFrom && typeof v === 'number') td.className = 'num';
            tr.appendChild(td);
        });
        t.appendChild(tr);
    });
    if (!rows.length) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = headers.length;
        td.textContent = 'Keine Daten im Zeitraum.';
        tr.appendChild(td);
        t.appendChild(tr);
    }
    return t;
}

function note(days, windowDays) {
    if (days <= windowDays) return null;
    const p = document.createElement('p');
    p.className = 'admin-note';
    p.textContent = `Details pro Benutzer nur für die letzten ${windowDays} Tage verfügbar.`;
    return p;
}

const when = (iso) => (iso ? iso.slice(0, 16).replace('T', ' ') : '–');

export function initStatsTab(root, getDays) {
    const refresh = async () => {
        const days = getDays();
        try {
            const q = `?days=${days}`;
            const [summary, timeline, users, failures, scenarios] = await Promise.all([
                adminApiJson(`/api/admin/stats/summary${q}`),
                adminApiJson(`/api/admin/stats/timeline${q}`),
                adminApiJson(`/api/admin/stats/users${q}`),
                adminApiJson(`/api/admin/stats/failures${q}`),
                adminApiJson(`/api/admin/stats/scenarios${q}`),
            ]);
            root.innerHTML = '';

            const kpiRow = document.createElement('div');
            kpiRow.className = 'kpi-row';
            kpis(summary).forEach((k) => {
                const tile = document.createElement('div');
                tile.className = 'admin-card kpi';
                const value = document.createElement('div');
                value.className = 'kpi-value';
                value.textContent = k.value.toLocaleString('de-AT');
                const label = document.createElement('div');
                label.className = 'kpi-label';
                label.textContent = k.label;
                const d = document.createElement('div');
                d.className = `kpi-delta${k.delta.worse ? ' worse' : ''}`;
                d.textContent = `${k.delta.text} vs. Vorperiode`;
                tile.append(value, label, d);
                kpiRow.appendChild(tile);
            });
            root.appendChild(kpiRow);

            const chartCard = card('Pro Tag (UTC)');
            const chart = document.createElement('div');
            chart.className = 'chart';
            chartCard.appendChild(chart);
            root.appendChild(chartCard);
            renderStackedBars(chart, chartSeries(timeline));

            const cols = document.createElement('div');
            cols.className = 'admin-two-col';
            const userCard = card('Benutzer');
            userCard.appendChild(table(['Benutzer', 'PDF', 'Sauber', 'XML', 'Zuletzt'],
                users.users.map((u) => [u.who, u.ok.pdf, u.ok.clean_pdf, u.ok.xml_download, when(u.lastActivity)]), 1));
            const n1 = note(days, users.detailWindowDays);
            if (n1) userCard.appendChild(n1);
            const failCard = card('Fehler');
            failCard.appendChild(table(['Art', 'Grund', 'Anzahl'],
                failures.groups.map((g) => [TYPE_NAMES[g.type] || g.type, REASON_LABELS[g.reason] || g.reason, g.count]), 2));
            const recentTitle = document.createElement('h2');
            recentTitle.textContent = 'Letzte Fehler';
            failCard.appendChild(recentTitle);
            failCard.appendChild(table(['Zeit', 'Art', 'Benutzer', 'Grund', 'Status'],
                failures.recent.map((f) => [when(f.occurredAt), TYPE_NAMES[f.type] || f.type, f.who,
                    REASON_LABELS[f.reason] || f.reason, f.httpStatus ?? '–']), 4));
            cols.append(userCard, failCard);
            root.appendChild(cols);

            const scenCols = document.createElement('div');
            scenCols.className = 'admin-two-col';
            const topCard = card('Meistgeladene Szenarien');
            topCard.appendChild(table(['Szenario', 'Geladen'], scenarios.top.map((s) => [s.title, s.loads])));
            const perUserCard = card('Szenario-Aktivität pro Benutzer');
            perUserCard.appendChild(table(['Benutzer', 'Gespeichert', 'Geladen'],
                scenarios.perUser.map((u) => [u.who, u.saves, u.loads])));
            const n2 = note(days, scenarios.detailWindowDays);
            if (n2) perUserCard.appendChild(n2);
            scenCols.append(topCard, perUserCard);
            root.appendChild(scenCols);
        } catch (err) {
            if (!err.cancelled) report(`Statistik konnte nicht geladen werden: ${err.message}`, 'error');
        }
    };
    return { refresh };
}
