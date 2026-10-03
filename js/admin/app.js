// Admin page entry point: tabs, period selector, per-tab refresh.

import { initScenariosTab } from './scenarios.js';
import { initDataTab } from './data.js';

let days = 30;

const panels = {
    szenarien: initScenariosTab(document.querySelector('[data-panel="szenarien"]')),
    daten: initDataTab(document.querySelector('[data-panel="daten"]')),
};

function selectTab(name) {
    if (!document.querySelector(`[data-panel="${name}"]`)) name = 'statistik';
    document.querySelectorAll('[data-tab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === name)));
    document.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== name; });
    history.replaceState(null, '', `#${name}`);
    panels[name]?.refresh();
}

document.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => selectTab(b.dataset.tab)));
document.querySelectorAll('[data-days]').forEach((b) => b.addEventListener('click', () => {
    days = Number(b.dataset.days);
    document.querySelectorAll('[data-days]').forEach((x) => {
        x.classList.toggle('active', x === b);
        x.setAttribute('aria-pressed', String(x === b));
    });
    panels.statistik?.refresh();
}));

selectTab(location.hash.slice(1) || 'statistik');
