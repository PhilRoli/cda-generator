import { test, expect, describe } from 'bun:test';
import { sanitizeState } from '../sanitize-state.js';

// Loaded state comes from localStorage, local JSON files and *shared* cloud scenarios,
// so it is untrusted: a wrong type must never replace the shape the UI relies on.

const defaults = () => ({
    documentDate: '2026-10-01T11:00',
    patient: {
        givenName: 'Maria',
        address: { city: 'Salzburg', country: 'A' },
    },
    anamnese: 'Text',
    diagnosen: [{ text: 'Default-Diagnose' }],
    medikation: [{ medikament: 'A', schema: '1-0-0' }],
});

describe('sanitizeState – shape is enforced', () => {
    test('null object branch falls back to the defaults', () => {
        const result = sanitizeState({ patient: null }, defaults());
        expect(result.patient.givenName).toBe('Maria');
        expect(result.patient.address.city).toBe('Salzburg');
    });

    test('string where an object is expected falls back to the defaults', () => {
        const result = sanitizeState({ patient: { address: 'Linz' } }, defaults());
        expect(result.patient.address).toEqual({ city: 'Salzburg', country: 'A' });
    });

    test('non-string text field falls back to the default', () => {
        const result = sanitizeState({ anamnese: { evil: true } }, defaults());
        expect(result.anamnese).toBe('Text');
    });

    test('numbers in text fields are kept as strings', () => {
        const result = sanitizeState({ patient: { address: { city: 5020 } } }, defaults());
        expect(result.patient.address.city).toBe('5020');
    });

    test('non-array list falls back to the default list', () => {
        const result = sanitizeState({ diagnosen: 'Fraktur' }, defaults());
        expect(result.diagnosen).toEqual([{ text: 'Default-Diagnose' }]);
    });

    test('non-object root yields the defaults', () => {
        expect(sanitizeState(null, defaults())).toEqual(defaults());
        expect(sanitizeState('x', defaults())).toEqual(defaults());
        expect(sanitizeState([], defaults())).toEqual(defaults());
    });
});

describe('sanitizeState – list items', () => {
    test('null and primitive items are dropped', () => {
        const result = sanitizeState({ diagnosen: [null, 'x', { text: 'Echt' }, 3] }, defaults());
        expect(result.diagnosen).toEqual([{ text: 'Echt' }]);
    });

    test('non-string item fields are coerced or dropped', () => {
        const result = sanitizeState(
            { medikation: [{ medikament: 'B', schema: 2, extra: { nested: 1 } }] },
            defaults(),
        );
        expect(result.medikation).toEqual([{ medikament: 'B', schema: '2' }]);
    });

    test('an explicitly empty list stays empty', () => {
        expect(sanitizeState({ diagnosen: [] }, defaults()).diagnosen).toEqual([]);
    });
});

describe('sanitizeState – merging', () => {
    test('valid values override the defaults and missing keys are filled', () => {
        const result = sanitizeState({ patient: { givenName: 'Hans' } }, defaults());
        expect(result.patient.givenName).toBe('Hans');
        expect(result.patient.address.city).toBe('Salzburg');
        expect(result.anamnese).toBe('Text');
    });

    test('unknown keys are kept, with list items cleaned', () => {
        const result = sanitizeState({ termine: [null, { text: 'Kontrolle' }], extra: 'x' }, defaults());
        expect(result.termine).toEqual([{ text: 'Kontrolle' }]);
        expect(result.extra).toBe('x');
    });

    test('the defaults object is not mutated', () => {
        const base = defaults();
        sanitizeState({ patient: { givenName: 'Hans' } }, base);
        expect(base).toEqual(defaults());
    });
});
