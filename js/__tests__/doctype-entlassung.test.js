import { test, expect, describe } from 'bun:test';
import { buildEntlassungsbrief } from '../doctype-entlassung.js';

// ---------------------------------------------------------------------------
// Fixed minimal state — mirrors the shape of defaultState() in app.js.
// Using a fixed documentDate so the output is deterministic except for the
// UUID-based ids (document id, setId, encompassingEncounter caseId when blank).
// ---------------------------------------------------------------------------

const BASE_STATE = {
    documentDate: '2024-06-15T11:00',
    patient: {
        givenName: 'Maria',
        familyName: 'Gruber',
        gender: 'F',
        birthDate: '1948-03-12',
        svnr: '4160270104',
        phone: '06641234567',
        patientId: '1234567',
        address: {
            street: 'Linzer Bundesstraße',
            houseNumber: '34',
            postalCode: '5023',
            city: 'Salzburg',
            country: 'A',
        },
    },
    organization: {
        name: 'Universitätsklinikum Salzburg',
        phone: '+43(0)57255',
        address: {
            street: 'Müllner Hauptstraße',
            houseNumber: '48',
            postalCode: '5020',
            city: 'Salzburg',
            country: 'A',
        },
    },
    author: { title: 'Dr.', givenName: 'Andrea', familyName: 'Hofer' },
    encounter: {
        admissionDate: '2024-06-11T14:30',
        dischargeDate: '2024-06-15T11:00',
        ward: 'Chirurgie',
        caseId: 'CASE-001',
        type: 'IMP',
    },
    brieftext: { text: '' },
    aufnahmegrund: 'Sturz',
    diagnosen: [{ text: 'Schenkelhalsfraktur links' }],
    vorerkrankungen: [{ text: 'Hypertonie' }],
    anamnese: 'Anamnese-Text.',
    verlauf: 'Verlauf-Text.',
    medikation: [{ medikament: 'Aspirin 100', schema: '1-0-0' }],
    empfehlungen: 'Empfehlung-Text.',
    allergien: [{ substanz: 'Penicillin' }],
    risikofaktoren: [{ faktor: 'Adipositas' }],
    patientenverfuegung: { status: 'keine' },
};

// Helper: replace UUID v4 occurrences with the literal string "UUID"
// so snapshot-style assertions are stable across runs.
const UUID_RE = /[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-4[0-9A-Fa-f]{3}-[89ABab][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}/g;
function normalizeUuids(str) {
    return str.replace(UUID_RE, 'UUID');
}

// ---------------------------------------------------------------------------
// Structural / behavioral tests
// ---------------------------------------------------------------------------

describe('buildEntlassungsbrief – structure', () => {
    test('starts with XML declaration', () => {
        const xml = buildEntlassungsbrief(BASE_STATE);
        expect(xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>')).toBe(true);
    });

    test('contains stylesheet processing instruction', () => {
        const xml = buildEntlassungsbrief(BASE_STATE);
        expect(xml).toContain('<?xml-stylesheet');
        expect(xml).toContain('ELGA_Stylesheet_v1.0.xsl');
    });

    test('contains <ClinicalDocument opening tag', () => {
        const xml = buildEntlassungsbrief(BASE_STATE);
        expect(xml).toContain('<ClinicalDocument');
    });

    test('is closed with </ClinicalDocument>', () => {
        const xml = buildEntlassungsbrief(BASE_STATE);
        expect(xml.trimEnd()).toMatch(/<\/ClinicalDocument>$/);
    });

    test('patient given name appears in output', () => {
        const xml = buildEntlassungsbrief(BASE_STATE);
        expect(xml).toContain('<given>Maria</given>');
    });

    test('patient family name appears in output', () => {
        const xml = buildEntlassungsbrief(BASE_STATE);
        expect(xml).toContain('<family>Gruber</family>');
    });
});

// ---------------------------------------------------------------------------
// XML escaping in patient fields
// ---------------------------------------------------------------------------

describe('buildEntlassungsbrief – XML escaping', () => {
    test('& in family name is escaped to &amp;', () => {
        const state = {
            ...BASE_STATE,
            patient: { ...BASE_STATE.patient, familyName: 'M&ller' },
        };
        const xml = buildEntlassungsbrief(state);
        expect(xml).not.toContain('<family>M&ller</family>');
        expect(xml).toContain('<family>M&amp;ller</family>');
    });

    test('< in given name is escaped to &lt;', () => {
        const state = {
            ...BASE_STATE,
            patient: { ...BASE_STATE.patient, givenName: 'A<nna' },
        };
        const xml = buildEntlassungsbrief(state);
        expect(xml).not.toContain('<given>A<nna</given>');
        expect(xml).toContain('<given>A&lt;nna</given>');
    });

    test('no raw & characters appear outside CDATA or XML constructs', () => {
        const state = {
            ...BASE_STATE,
            patient: { ...BASE_STATE.patient, familyName: 'A&B', givenName: 'C&D' },
        };
        const xml = buildEntlassungsbrief(state);
        // After removing all valid &xxx; entities, no bare & should remain in name elements.
        const stripped = xml.replace(/&amp;|&lt;|&gt;|&quot;|&apos;/g, '');
        // The remaining & (if any) should not appear directly inside patient name tags
        const nameBlockMatch = xml.match(/<patient>[\s\S]*?<\/patient>/);
        if (nameBlockMatch) {
            const nameBlock = nameBlockMatch[0].replace(/&amp;|&lt;|&gt;|&quot;|&apos;/g, '');
            expect(nameBlock).not.toContain('&');
        }
    });
});

// ---------------------------------------------------------------------------
// Gender rendering
// ---------------------------------------------------------------------------

describe('buildEntlassungsbrief – gender', () => {
    test('gender F yields displayName="Female"', () => {
        const state = { ...BASE_STATE, patient: { ...BASE_STATE.patient, gender: 'F' } };
        const xml = buildEntlassungsbrief(state);
        expect(xml).toContain('displayName="Female"');
    });

    test('gender M yields displayName="Male"', () => {
        const state = { ...BASE_STATE, patient: { ...BASE_STATE.patient, gender: 'M' } };
        const xml = buildEntlassungsbrief(state);
        expect(xml).toContain('displayName="Male"');
    });
});

// ---------------------------------------------------------------------------
// Encounter type — AMB vs IMP
// ---------------------------------------------------------------------------

describe('buildEntlassungsbrief – encounter type', () => {
    test('encounter type IMP yields code="IMP"', () => {
        const state = {
            ...BASE_STATE,
            encounter: { ...BASE_STATE.encounter, type: 'IMP' },
        };
        const xml = buildEntlassungsbrief(state);
        expect(xml).toContain('code="IMP"');
    });

    test('encounter type AMB yields code="AMB"', () => {
        const state = {
            ...BASE_STATE,
            encounter: { ...BASE_STATE.encounter, type: 'AMB' },
        };
        const xml = buildEntlassungsbrief(state);
        expect(xml).toContain('code="AMB"');
    });

    test('encounter type AMB does not yield IMP code in encompassingEncounter', () => {
        const state = {
            ...BASE_STATE,
            encounter: { ...BASE_STATE.encounter, type: 'AMB' },
        };
        const xml = buildEntlassungsbrief(state);
        // The encompassingEncounter block should have AMB, not IMP
        const encounterBlock = xml.match(/<encompassingEncounter>[\s\S]*?<\/encompassingEncounter>/);
        expect(encounterBlock).not.toBeNull();
        expect(encounterBlock[0]).toContain('code="AMB"');
        expect(encounterBlock[0]).not.toContain('code="IMP"');
    });
});

// ---------------------------------------------------------------------------
// UUID normalization smoke test — output is consistent across two calls
// (structural shape, not exact UUIDs)
// ---------------------------------------------------------------------------

describe('buildEntlassungsbrief – UUID normalization', () => {
    test('normalized output (UUIDs replaced) matches itself on repeated call', () => {
        // Two separate calls will have different UUIDs but same structure.
        // After normalization both should be identical — verifies no other
        // source of randomness crept in.
        const xml1 = normalizeUuids(buildEntlassungsbrief(BASE_STATE));
        const xml2 = normalizeUuids(buildEntlassungsbrief(BASE_STATE));
        expect(xml1).toBe(xml2);
    });
});
