import { test, expect, describe } from 'bun:test';
import { deepMerge } from '../deep-merge.js';

// ---------------------------------------------------------------------------
// Nested partial object fills from defaults
// ---------------------------------------------------------------------------

describe('deepMerge – nested partial object fills from defaults', () => {
    test('missing key in override is filled from base', () => {
        const base = { patient: { givenName: 'Maria', familyName: 'Gruber', address: { city: 'Salzburg', country: 'A' } } };
        const override = { patient: { givenName: 'Hans' } };
        const result = deepMerge(base, override);
        expect(result.patient.givenName).toBe('Hans');
        expect(result.patient.familyName).toBe('Gruber');      // filled from base
        expect(result.patient.address.city).toBe('Salzburg');  // whole nested subtree preserved
        expect(result.patient.address.country).toBe('A');
    });

    test('deeply nested partial object fills missing keys', () => {
        const base = { a: { b: { c: 1, d: 2 } } };
        const override = { a: { b: { c: 99 } } };
        const result = deepMerge(base, override);
        expect(result.a.b.c).toBe(99);  // override wins
        expect(result.a.b.d).toBe(2);   // filled from base
    });

    test('completely missing nested subtree is taken from base', () => {
        const base = { patient: { address: { city: 'Linz', country: 'A' } } };
        const override = {};
        const result = deepMerge(base, override);
        expect(result.patient.address.city).toBe('Linz');
    });
});

// ---------------------------------------------------------------------------
// Array replacement (including empty array stays empty)
// ---------------------------------------------------------------------------

describe('deepMerge – array replacement', () => {
    test('loaded array replaces base array wholesale', () => {
        const base = { diagnosen: [{ text: 'Hypertonie' }, { text: 'Diabetes' }] };
        const override = { diagnosen: [{ text: 'Pneumonie' }] };
        const result = deepMerge(base, override);
        expect(result.diagnosen).toEqual([{ text: 'Pneumonie' }]);
        expect(result.diagnosen).toHaveLength(1);
    });

    test('explicitly empty array in override stays empty (not filled from base)', () => {
        const base = { allergien: [{ substanz: 'Penicillin' }] };
        const override = { allergien: [] };
        const result = deepMerge(base, override);
        expect(result.allergien).toEqual([]);
    });

    test('array elements are not recursively merged', () => {
        const base = { medikation: [{ medikament: 'Ramipril', schema: '1-0-0' }] };
        const override = { medikation: [{ medikament: 'Pantoprazol', schema: '0-1-0' }, { medikament: 'Metformin', schema: '1-0-1' }] };
        const result = deepMerge(base, override);
        expect(result.medikation).toHaveLength(2);
        expect(result.medikation[0].medikament).toBe('Pantoprazol');
    });
});

// ---------------------------------------------------------------------------
// Loaded primitive overrides default object
// ---------------------------------------------------------------------------

describe('deepMerge – loaded primitive overrides default object', () => {
    test('primitive override replaces a nested object', () => {
        const base = { patient: { address: { city: 'Wien' } } };
        const override = { patient: 'anonymous' };
        const result = deepMerge(base, override);
        expect(result.patient).toBe('anonymous');
    });

    test('number primitive wins over default object', () => {
        const base = { nested: { x: 1 } };
        const override = { nested: 42 };
        const result = deepMerge(base, override);
        expect(result.nested).toBe(42);
    });

    test('false wins over default object', () => {
        const base = { config: { enabled: { flag: true } } };
        const override = { config: { enabled: false } };
        const result = deepMerge(base, override);
        expect(result.config.enabled).toBe(false);
    });
});

// ---------------------------------------------------------------------------
// Loaded null handling
// ---------------------------------------------------------------------------

describe('deepMerge – loaded null handling', () => {
    test('null in override wins over default value', () => {
        const base = { patient: { svnr: '1234567890' } };
        const override = { patient: { svnr: null } };
        const result = deepMerge(base, override);
        expect(result.patient.svnr).toBeNull();
    });

    test('null in override wins over default nested object', () => {
        const base = { patient: { address: { city: 'Graz' } } };
        const override = { patient: { address: null } };
        const result = deepMerge(base, override);
        expect(result.patient.address).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// Key present only in override is kept
// ---------------------------------------------------------------------------

describe('deepMerge – key only in override is kept', () => {
    test('extra top-level key from override is present in result', () => {
        const base = { name: 'base' };
        const override = { name: 'override', extra: 'bonus' };
        const result = deepMerge(base, override);
        expect(result.extra).toBe('bonus');
    });

    test('extra nested key from override is present in result', () => {
        const base = { patient: { givenName: 'Maria' } };
        const override = { patient: { givenName: 'Hans', newField: 'new' } };
        const result = deepMerge(base, override);
        expect(result.patient.newField).toBe('new');
        expect(result.patient.givenName).toBe('Hans');
    });
});

// ---------------------------------------------------------------------------
// Key present only in default is kept
// ---------------------------------------------------------------------------

describe('deepMerge – key only in base is kept', () => {
    test('top-level key missing from override is taken from base', () => {
        const base = { aufnahmegrund: 'Sturz', diagnosen: [] };
        const override = { aufnahmegrund: 'Infektion' };
        const result = deepMerge(base, override);
        expect(result.aufnahmegrund).toBe('Infektion');
        expect(result.diagnosen).toEqual([]);  // from base
    });

    test('deeply nested key missing from override is taken from base', () => {
        const base = { org: { address: { street: 'Main St', houseNumber: '1' } } };
        const override = { org: { address: { street: 'Other St' } } };
        const result = deepMerge(base, override);
        expect(result.org.address.houseNumber).toBe('1');
    });
});

// ---------------------------------------------------------------------------
// Immutability — base and override are not mutated
// ---------------------------------------------------------------------------

describe('deepMerge – immutability', () => {
    test('base object is not mutated', () => {
        const base = { patient: { givenName: 'Maria', address: { city: 'Linz' } } };
        const override = { patient: { givenName: 'Hans', address: { city: 'Wien' } } };
        deepMerge(base, override);
        expect(base.patient.givenName).toBe('Maria');
        expect(base.patient.address.city).toBe('Linz');
    });

    test('override object is not mutated', () => {
        const base = { a: 1 };
        const override = { b: 2 };
        deepMerge(base, override);
        expect(Object.keys(override)).toEqual(['b']);
    });
});
