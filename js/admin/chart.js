// Stacked daily bar chart as inline SVG (no library). Built with createElementNS so
// no data ever goes through innerHTML.

const SVG = 'http://www.w3.org/2000/svg';
const COLORS = { pdf: '#c8102e', clean_pdf: '#f0a3ae', xml_download: '#9aa0a6' };

function el(name, attrs) {
    const node = document.createElementNS(SVG, name);
    Object.entries(attrs).forEach(([k, v]) => node.setAttribute(k, String(v)));
    return node;
}

export function renderStackedBars(container, { days, series, failed, max }) {
    const width = 720;
    const height = 180;
    const pad = 24;
    const slot = (width - pad) / Math.max(days.length, 1);
    const barWidth = Math.max(2, slot * 0.7);
    const scale = (height - pad) / max;

    const svg = el('svg', { viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': 'Generierungen pro Tag' });
    svg.appendChild(el('line', { x1: pad, y1: height - pad, x2: width, y2: height - pad, stroke: '#ddd' }));
    const maxLabel = el('text', { x: 0, y: 12, 'font-size': 10, fill: '#666' });
    maxLabel.textContent = String(max);
    svg.appendChild(maxLabel);

    days.forEach((day, i) => {
        const x = pad + i * slot + (slot - barWidth) / 2;
        let y = height - pad;
        const group = el('g', {});
        const title = document.createElementNS(SVG, 'title');
        const parts = series.map((s) => `${s.label}: ${s.values[i]}`);
        title.textContent = `${day} — ${parts.join(', ')}, Fehler: ${failed[i]}`;
        group.appendChild(title);
        series.forEach((s) => {
            const h = s.values[i] * scale;
            if (h <= 0) return;
            y -= h;
            group.appendChild(el('rect', { x, y, width: barWidth, height: h, fill: COLORS[s.key] }));
        });
        if (failed[i] > 0) {
            group.appendChild(el('circle', { cx: x + barWidth / 2, cy: y - 5, r: 3, fill: '#222' }));
        }
        svg.appendChild(group);
    });

    container.innerHTML = '';
    container.appendChild(svg);
    const legend = document.createElement('div');
    legend.className = 'chart-legend';
    [...series.map((s) => [COLORS[s.key], s.label]), ['#222', 'Tag mit Fehlern']].forEach(([color, label]) => {
        const item = document.createElement('span');
        const swatch = document.createElement('i');
        swatch.style.background = color;
        item.append(swatch, document.createTextNode(label));
        legend.appendChild(item);
    });
    container.appendChild(legend);
}
