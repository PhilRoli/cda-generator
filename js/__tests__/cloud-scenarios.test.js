import { test, expect, describe } from 'bun:test';
import { scenarioIdToUpdate } from '../cloud-scenarios.js';

describe('scenarioIdToUpdate', () => {
    const loaded = { id: 'abc', username: 'philipp' };

    test('updates the loaded scenario when it belongs to the current user', () => {
        expect(scenarioIdToUpdate(loaded, 'philipp')).toBe('abc');
    });

    test('creates a new scenario when the loaded one belongs to someone else', () => {
        // Shared scenarios ("Alle anzeigen") can be loaded, but saving must not try to
        // overwrite another user's scenario (the server would answer 403).
        expect(scenarioIdToUpdate(loaded, 'anna')).toBeUndefined();
    });

    test('creates a new scenario when nothing was loaded from the cloud', () => {
        // Merely having a scenario selected in the dropdown must not cause an overwrite.
        expect(scenarioIdToUpdate(null, 'philipp')).toBeUndefined();
    });

    test('"Als neu speichern" always creates a new scenario', () => {
        expect(scenarioIdToUpdate(loaded, 'philipp', { asNew: true })).toBeUndefined();
    });

    test('usernames are compared after trimming, like the server does', () => {
        expect(scenarioIdToUpdate(loaded, ' philipp ')).toBe('abc');
    });
});
