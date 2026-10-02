import { test, expect } from 'bun:test';

// Runs in a timezone west of UTC: a date-only value parsed as UTC midnight would be
// rendered as the previous day there.
process.env.TZ = 'America/New_York';
const { toHl7Time } = await import('../cda-builder.js');

test('date-only values keep their calendar day west of UTC', () => {
    expect(toHl7Time('1948-03-12', false)).toBe('19480312');
});

test('date-only values with time render local midnight', () => {
    expect(toHl7Time('1948-03-12')).toMatch(/^19480312000000[+-]\d{4}$/);
});

test('Patientenverfügung dates keep their calendar day west of UTC', async () => {
    const { buildEntlassungsbrief } = await import('../doctype-entlassung.js');
    const xml = buildEntlassungsbrief({
        documentDate: '2024-06-15T11:00',
        patient: { givenName: 'A', familyName: 'B', gender: 'F', birthDate: '1948-03-12', address: {} },
        organization: { name: 'KH', address: {} },
        author: { givenName: 'C', familyName: 'D' },
        encounter: {},
        patientenverfuegung: { status: 'beachtlich', datum: '2022-09-15', gueltigBis: '2027-09-15' },
    });
    expect(xml).toContain('15.09.2022');
    expect(xml).toContain('15.09.2027');
});
