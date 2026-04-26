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
    // Vorerkrankungen können als Tabelle angezeigt werden, wenn vorhanden, plus Freitext
    const hasList = Array.isArray(state.vorerkrankungen) && state.vorerkrankungen.length > 0;
    const tablePart = hasList
        ? tableFromRows({
              headers: ['Vorerkrankung / Diagnose', 'Seit', 'Bemerkung'],
              rows: state.vorerkrankungen.map((v) => [v.text, v.since || '', v.note || '']),
              colWidths: ['', '20', '30'],
          })
        : '';
    const textPart = state.anamnese ? paragraphsFromText(state.anamnese) : '';
    const narrative = [tablePart, textPart].filter(Boolean).join('\n') || '<paragraph/>';
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
    const narrative = list.length
        ? tableFromRows({
              headers: ['Diagnose', 'ICD-10', 'Seitigkeit'],
              rows: list.map((d) => [d.text, d.icd || '', d.side || '']),
              colWidths: ['', '15', '15'],
          })
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
              headers: ['Wirkstoff', 'Handelsname', 'Stärke', 'Schema (M-Mi-Ab-N)', 'Bemerkung'],
              rows: list.map((m) => [m.wirkstoff, m.handelsname || '', m.staerke || '', m.schema || '', m.bemerkung || '']),
              colWidths: ['', '', '12', '20', '25'],
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
        sectionAnamnese(state),
        sectionVerlauf(state),
        sectionMedikation(state),
        sectionEmpfehlungen(state),
    ];

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
