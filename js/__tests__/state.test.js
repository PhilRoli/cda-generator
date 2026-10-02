import { test, expect } from 'bun:test';
import { getState, replaceState, defaultState, getByPath, setByPath } from '../state.js';

test('setByPath creates missing intermediate objects', () => {
    const obj = {};
    setByPath(obj, 'patient.address.city', 'Linz');
    expect(obj).toEqual({ patient: { address: { city: 'Linz' } } });
});

test('getByPath returns undefined for a missing path instead of throwing', () => {
    expect(getByPath({ patient: null }, 'patient.address.city')).toBeUndefined();
});

test('replaceState swaps the object every module reads via getState', () => {
    const next = defaultState();
    next.patient.familyName = 'Neu';
    replaceState(next);
    expect(getState().patient.familyName).toBe('Neu');
});
