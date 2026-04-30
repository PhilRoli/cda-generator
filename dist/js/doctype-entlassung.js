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
        templateId: '1.2.40.0.34.11.2.2.18',
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
        templateId: '1.2.40.0.34.11.2.2.2',
        code: '11535-2',
        displayName: 'Hospital discharge diagnosis',
        title: 'Diagnosen bei Entlassung',
        narrative,
    });
}

function sectionDurchgefuehrteMassnahmen(state) {
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.4',
        code: '29554-3',
        displayName: 'Procedures provided',
        title: 'Durchgeführte Maßnahmen',
        narrative: paragraphsFromText(state.verlauf || ''),
    });
}

function sectionEmpfohleneMedikation(state) {
    const list = Array.isArray(state.medikation) ? state.medikation : [];
    const narrative = list.length
        ? tableFromRows({
              headers: ['Medikament', 'Schema (M-Mi-Ab-N)'],
              rows: list.map((m) => [m.medikament, m.schema || '']),
              colWidths: ['', '25'],
          })
        : '<paragraph>Keine Medikation bei Entlassung empfohlen.</paragraph>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.7',
        code: '10183-2',
        displayName: 'Hospital discharge medications',
        title: 'Empfohlene Medikation',
        narrative,
    });
}

function sectionLetzteMedikation(state) {
    const list = Array.isArray(state.letzteMedikation) ? state.letzteMedikation : [];
    const narrative = list.length
        ? tableFromRows({
              headers: ['Medikament', 'Schema (M-Mi-Ab-N)'],
              rows: list.map((m) => [m.medikament, m.schema || '']),
              colWidths: ['', '25'],
          })
        : '<paragraph>Keine Angaben zur letzten Medikation während des Aufenthalts.</paragraph>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.5',
        code: '10160-0',
        displayName: 'History of medication use',
        title: 'Letzte Medikation',
        narrative,
    });
}

function sectionTermine(state) {
    const list = Array.isArray(state.termine) ? state.termine : [];
    const narrative = list.length
        ? `<list>\n${list.map((t) => `<item>${escapeHtml(typeof t === 'string' ? t : t.text || '')}</item>`).join('\n')}\n</list>`
        : state.termine && typeof state.termine === 'string'
          ? paragraphsFromText(state.termine)
          : '<paragraph>Keine Termine, Kontrollen oder Wiederbestellungen vereinbart.</paragraph>';
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.10',
        code: 'TERMIN',
        codeSystem: '1.2.40.0.34.5.40',
        codeSystemName: 'ELGA_Sections',
        displayName: 'Termine, Kontrollen, Wiederbestellung',
        title: 'Termine, Kontrollen, Wiederbestellung',
        narrative,
    });
}

function sectionAllergien(state) {
    const allergList = Array.isArray(state.allergien) ? state.allergien : [];
    const allergItems = allergList.map((a) => a.substanz).filter(Boolean);
    const risikoList = Array.isArray(state.risikofaktoren) ? state.risikofaktoren : [];
    const risikoItems = risikoList.map((r) => r.faktor).filter(Boolean);

    const allergPart = allergItems.length
        ? `<paragraph styleCode="xELGA_h3">Allergien und Intoleranzen</paragraph>\n<list>\n${allergItems.map((t) => `<item>${escapeHtml(t)}</item>`).join('\n')}\n</list>`
        : '<paragraph>Keine bekannten Allergien oder Intoleranzen.</paragraph>';
    const risikoPart = risikoItems.length
        ? `<paragraph styleCode="xELGA_h3">Risikofaktoren</paragraph>\n<list>\n${risikoItems.map((t) => `<item>${escapeHtml(t)}</item>`).join('\n')}\n</list>`
        : '';
    const narrative = [allergPart, risikoPart].filter(Boolean).join('\n');
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.13',
        code: '48765-2',
        displayName: 'Allergies and adverse reactions Document',
        title: 'Allergien, Unverträglichkeiten und Risiken',
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
        templateId: '1.2.40.0.34.11.1.2.4',
        code: '42348-3',
        displayName: 'Advance directives',
        title: 'Patientenverfügungsstatus',
        narrative,
    });
}

function sectionEmpfehlungen(state) {
    return renderSection({
        templateId: '1.2.40.0.34.11.2.2.9',
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
        sectionEmpfohleneMedikation(state),
        sectionTermine(state),
        sectionDiagnosen(state),
        sectionAllergien(state),
        sectionAnamnese(state),
        sectionDurchgefuehrteMassnahmen(state),
        sectionLetzteMedikation(state),
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
