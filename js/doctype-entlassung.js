// Sektionen für Entlassungsbrief Ärztlich (ELGA OID 1.2.40.0.34.77.4.102).
// Codes/templateIds orientiert an Schematron elga-EntlassungsbriefAerztlich.sch
// und der typischen ELGA-IG-Struktur.

import { escapeXml, renderSection, renderBrieftextSection, composeDocument } from './cda-builder.js';

function escapeHtml(str) {
    return escapeXml(str);
}

// Konvertiert mehrzeiligen Freitext in CDA-narrative <paragraph>-Blöcke.
function paragraphsFromText(text) {
    if (!text) return '<paragraph/>';
    return text
        .split(/\n\n+/)
        .map((p) => `<paragraph>${escapeHtml(p.replace(/\n/g, ' ').trim())}</paragraph>`)
        .join('\n');
}

function tableFromRows({ headers, rows, colWidths }) {
    const colStyles = colWidths || headers.map(() => '');
    const headRow = `<tr>${headers
        .map((h, i) => `<th${colStyles[i] ? ` styleCode="xELGA_colw:${colStyles[i]}"` : ''}>${escapeHtml(h)}</th>`)
        .join('')}</tr>`;
    const bodyRows = rows
        .map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell ?? '')}</td>`).join('')}</tr>`)
        .join('\n');
    return `<table>
<thead>
${headRow}
</thead>
<tbody>
${bodyRows}
</tbody>
</table>`;
}

// --- Sektionen --------------------------------------------------------------

function sectionAufnahmegrund(state) {
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.1',
        code: '42349-1',
        displayName: 'Reason for referral',
        title: 'Aufnahmegrund',
        narrative: paragraphsFromText(state.aufnahmegrund || ''),
    });
}

function sectionAnamnese(state) {
    const items = Array.isArray(state.vorerkrankungen)
        ? state.vorerkrankungen.map((v) => v.text).filter(Boolean)
        : [];
    const listPart = items.length
        ? `<list>\n${items.map((t) => `<item>${escapeHtml(t)}</item>`).join('\n')}\n</list>`
        : '';
    const textPart = state.anamnese ? paragraphsFromText(state.anamnese) : '';
    const narrative = [listPart, textPart].filter(Boolean).join('\n') || '<paragraph/>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.2',
        code: '11329-0',
        displayName: 'History of current illness',
        title: 'Anamnese / Vorerkrankungen',
        narrative,
    });
}

function sectionDiagnosen(state) {
    const list = Array.isArray(state.diagnosen) ? state.diagnosen : [];
    const items = list.map((d) => d.text).filter(Boolean);
    const narrative = items.length
        ? `<list>\n${items.map((t) => `<item>${escapeHtml(t)}</item>`).join('\n')}\n</list>`
        : '<paragraph>Keine Diagnose dokumentiert.</paragraph>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.7',
        code: '11535-2',
        displayName: 'Hospital discharge diagnosis',
        title: 'Diagnosen bei Entlassung',
        narrative,
    });
}

function sectionVerlauf(state) {
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.4',
        code: '8648-8',
        displayName: 'Hospital course',
        title: 'Aufenthaltsverlauf',
        narrative: paragraphsFromText(state.verlauf || ''),
    });
}

function sectionMedikation(state) {
    const list = Array.isArray(state.medikation) ? state.medikation : [];
    const narrative = list.length
        ? tableFromRows({
              headers: ['Medikament', 'Schema (M-Mi-Ab-N)'],
              rows: list.map((m) => [m.medikament, m.schema || '']),
              colWidths: ['', '25'],
          })
        : '<paragraph>Keine Medikation bei Entlassung.</paragraph>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.5',
        code: '10183-2',
        displayName: 'Hospital discharge medications',
        title: 'Medikation bei Entlassung',
        narrative,
    });
}

function sectionAllergien(state) {
    const list = Array.isArray(state.allergien) ? state.allergien : [];
    const items = list.map((a) => a.substanz).filter(Boolean);
    const narrative = items.length
        ? `<list>\n${items.map((t) => `<item>${escapeHtml(t)}</item>`).join('\n')}\n</list>`
        : '<paragraph>Keine bekannten Allergien oder Intoleranzen.</paragraph>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.10',
        code: '48765-2',
        displayName: 'Allergies and adverse reactions Document',
        title: 'Allergien und Intoleranzen',
        narrative,
    });
}

function sectionRisikofaktoren(state) {
    const list = Array.isArray(state.risikofaktoren) ? state.risikofaktoren : [];
    const items = list.map((r) => r.faktor).filter(Boolean);
    const narrative = items.length
        ? `<list>\n${items.map((t) => `<item>${escapeHtml(t)}</item>`).join('\n')}\n</list>`
        : '<paragraph>Keine relevanten Risikofaktoren dokumentiert.</paragraph>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.11',
        code: '75310-3',
        displayName: 'Health concerns Document',
        title: 'Risikofaktoren',
        narrative,
    });
}

function sectionPatientenverfuegung(state) {
    const pv = state.patientenverfuegung || {};
    if (pv.status === 'keine' || pv.status === 'unbekannt' || !pv.status) return '';
    const statusLabels = {
        beachtlich: 'Beachtliche Patientenverfügung',
        verbindlich: 'Verbindliche Patientenverfügung',
        vorsorgevollmacht: 'Vorsorgevollmacht vorhanden',
    };
    const statusText = statusLabels[pv.status] || pv.status;
    const formatDate = (d) => {
        if (!d) return '';
        const dt = new Date(d);
        if (isNaN(dt)) return d;
        const pad = (n) => String(n).padStart(2, '0');
        return `${pad(dt.getDate())}.${pad(dt.getMonth() + 1)}.${dt.getFullYear()}`;
    };
    const rows = [['Status', statusText]];
    if (pv.hinterlegtBei) rows.push(['Hinterlegt bei', pv.hinterlegtBei]);
    if (pv.datum) rows.push(['Errichtungsdatum', formatDate(pv.datum)]);
    if (pv.gueltigBis) rows.push(['Gültig bis', formatDate(pv.gueltigBis)]);
    const tablePart = tableFromRows({
        headers: ['Merkmal', 'Wert'],
        rows,
        colWidths: ['25', ''],
    });
    const textPart = pv.bemerkung ? paragraphsFromText(pv.bemerkung) : '';
    const narrative = [tablePart, textPart].filter(Boolean).join('\n');
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.12',
        code: '42348-3',
        displayName: 'Advance directives',
        title: 'Patientenverfügungsstatus',
        narrative,
    });
}

function sectionEmpfehlungen(state) {
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.6',
        code: '18776-5',
        displayName: 'Plan of treatment',
        title: 'Empfehlungen / Weiteres Procedere',
        narrative: paragraphsFromText(state.empfehlungen || ''),
    });
}

// --- Top-Level Build --------------------------------------------------------

export function buildEntlassungsbrief(state) {
    const sections = [
        renderBrieftextSection(state),
        sectionAufnahmegrund(state),
        sectionDiagnosen(state),
        sectionAllergien(state),
        sectionAnamnese(state),
        sectionRisikofaktoren(state),
        sectionVerlauf(state),
        sectionMedikation(state),
        sectionEmpfehlungen(state),
        sectionPatientenverfuegung(state),
    ].filter(Boolean);

    return composeDocument({
        documentMeta: {
            title: 'Ärztlicher Entlassungsbrief',
            code: '11490-0',
            codeDisplayName: 'Physician Discharge summary',
            effectiveTime: state.documentDate || new Date().toISOString(),
        },
        patient: state.patient,
        author: state.author,
        organization: state.organization,
        encounter: state.encounter,
        legalAuthTime: state.documentDate || new Date().toISOString(),
        sections,
    });
}
