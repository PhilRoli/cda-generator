import { test, expect, describe } from 'bun:test';
import { HOSPITALS_BY_BUNDESLAND, DEPARTMENTS } from '../hospitals.js';

const KEYS = ['W', 'NOE', 'OOE', 'S', 'ST', 'T', 'V', 'K', 'B'];
const LABELS = Object.values(DEPARTMENTS);
// Vorarlberg, Burgenland and Kärnten simply have few acute hospitals.
const MIN = { W: 10, NOE: 8, OOE: 8, S: 8, ST: 8, T: 8, V: 5, K: 5, B: 5 };

describe('curated hospital list', () => {
    test('every Bundesland has a solid list (Wien more)', () => {
        for (const key of KEYS) {
            const min = MIN[key];
            expect({ key, count: HOSPITALS_BY_BUNDESLAND[key]?.length ?? 0 }).toEqual({
                key,
                count: expect.any(Number),
            });
            expect(HOSPITALS_BY_BUNDESLAND[key].length).toBeGreaterThanOrEqual(min);
        }
    });

    test('every entry is complete and well-formed', () => {
        for (const key of KEYS) {
            for (const h of HOSPITALS_BY_BUNDESLAND[key]) {
                const where = `${key}: ${h.name}`;
                expect({ where, name: h.name.trim() !== '' }).toEqual({ where, name: true });
                expect({ where, phone: /^\+43[\d\s()-]+$/.test(h.phone) }).toEqual({ where, phone: true });
                expect({ where, street: h.street.trim() !== '' }).toEqual({ where, street: true });
                expect({ where, houseNumber: String(h.houseNumber).trim() !== '' }).toEqual({ where, houseNumber: true });
                expect({ where, plz: /^\d{4}$/.test(h.postalCode) }).toEqual({ where, plz: true });
                expect({ where, city: h.city.trim() !== '' }).toEqual({ where, city: true });
            }
        }
    });

    test('departments come from the controlled vocabulary, without duplicates', () => {
        for (const key of KEYS) {
            for (const h of HOSPITALS_BY_BUNDESLAND[key]) {
                const where = `${key}: ${h.name}`;
                expect({ where, n: h.departments.length > 0 }).toEqual({ where, n: true });
                expect({ where, unknown: h.departments.filter((d) => !LABELS.includes(d)) }).toEqual({ where, unknown: [] });
                expect({ where, dup: h.departments.length - new Set(h.departments).size }).toEqual({ where, dup: 0 });
            }
        }
    });

    test('no hospital is listed twice', () => {
        const names = KEYS.flatMap((k) => HOSPITALS_BY_BUNDESLAND[k].map((h) => h.name));
        expect(names.length - new Set(names).size).toBe(0);
    });

    test('every Bundesland can serve the common cases: internal medicine and trauma surgery', () => {
        for (const key of KEYS) {
            for (const label of [DEPARTMENTS.INNERE_MEDIZIN, DEPARTMENTS.UNFALLCHIRURGIE]) {
                const has = HOSPITALS_BY_BUNDESLAND[key].some((h) => h.departments.includes(label));
                expect({ key, label, has }).toEqual({ key, label, has: true });
            }
        }
    });
});
