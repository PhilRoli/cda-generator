import { test, expect, describe } from 'bun:test';
import { buildStateFromAi, fillFromAi } from '../ai-state.js';
import { isValidSvnr } from '../faker.js';

const ai = () => ({
    patient: {
        givenName: 'Franz', familyName: 'Huber', gender: 'M', birthDate: '1947-05-03', phone: '06641234567',
        address: { street: 'Hauptstraße', houseNumber: '12', postalCode: '4020', city: 'Linz' },
    },
    organization: { name: 'Klinikum Linz', phone: '+43(0)732123', address: { street: 'A', houseNumber: '1', postalCode: '4020', city: 'Linz' } },
    author: { title: 'Dr.', givenName: 'Eva', familyName: 'Maier' },
    encounter: { admissionDate: '2026-10-06T14:30', dischargeDate: '2026-10-11T11:00', ward: 'Kardiologie' },
    aufnahmegrund: 'Dekompensierte Herzinsuffizienz.',
    anamnese: 'Dyspnoe.',
    verlauf: 'Rekompensation.',
    empfehlungen: 'Kontrolle.',
    diagnosen: [{ text: 'Herzinsuffizienz' }],
    vorerkrankungen: [],
    medikation: [{ medikament: 'Torasemid 10 mg', schema: '1-0-0' }],
    allergien: [],
    risikofaktoren: [{ faktor: 'Nikotin' }],
    patientenverfuegung: { status: 'keine', hinterlegtBei: '', datum: '', gueltigBis: '', bemerkung: '' },
});

describe('buildStateFromAi', () => {
    test('maps AI fields, derives documentDate from the discharge and generates a valid SVNR', () => {
        const s = buildStateFromAi(ai());
        expect(s.patient.givenName).toBe('Franz');
        expect(s.encounter.type).toBe('IMP');
        expect(s.documentDate).toBe('2026-10-11T11:00');
        expect(isValidSvnr(s.patient.svnr)).toBe(true);
        expect(s.patient.svnr.endsWith('030547')).toBe(true); // DDMMYY of 1947-05-03
        expect(s.patient.address.country).toBe('A');
        expect(s.encounter.caseId).toBe('');
        expect(s.vorerkrankungen).toEqual([]); // an empty AI list replaces the default content
    });

    test('model output that is not M/F or an unknown PV status falls back safely', () => {
        const a = ai();
        a.patient.gender = 'divers';
        a.patientenverfuegung.status = 'irgendwas';
        const s = buildStateFromAi(a);
        expect(s.patient.gender).toBe('F');
        expect(s.patientenverfuegung.status).toBe('unbekannt');
    });

    test('an unparsable birth date still yields a valid SVNR', () => {
        const a = ai();
        a.patient.birthDate = 'gestern';
        expect(isValidSvnr(buildStateFromAi(a).patient.svnr)).toBe(true);
    });

    test('empty list rows are dropped and HTML stays plain text', () => {
        const a = ai();
        a.diagnosen = [{ text: '' }, { text: '<img src=x onerror=alert(1)>' }];
        const s = buildStateFromAi(a);
        expect(s.diagnosen).toEqual([{ text: '<img src=x onerror=alert(1)>' }]);
    });

    test('garbage input yields the defaults instead of throwing', () => {
        expect(() => buildStateFromAi(null)).not.toThrow();
        expect(buildStateFromAi({ patient: 5 }).patient.givenName).toBeTruthy();
    });
});

describe('birth date rollover', () => {
    test('an impossible date like 1947-02-30 is replaced so field and SVNR agree', () => {
        const a = ai();
        a.patient.birthDate = '1947-02-30';
        const s = buildStateFromAi(a);
        expect(s.patient.birthDate).toBe('1960-01-01');
        expect(s.patient.svnr.endsWith('010160')).toBe(true);
        expect(isValidSvnr(s.patient.svnr)).toBe(true);
    });

    test('unparsable text in the birth date field is replaced as well', () => {
        const a = ai();
        a.patient.birthDate = 'gestern';
        expect(buildStateFromAi(a).patient.birthDate).toBe('1960-01-01');
    });

    test('a valid date is kept untouched', () => {
        expect(buildStateFromAi(ai()).patient.birthDate).toBe('1947-05-03');
    });
});

describe('fillFromAi', () => {
    const run = (overrides = {}) => {
        const calls = { applied: [], requests: [] };
        const deps = {
            prompt: 'Herzinsuffizienz',
            headers: { 'X-Cda-User': 'anna' },
            fetchJson: async (url, options) => {
                calls.requests.push({ url, options });
                return { state: ai() };
            },
            isDialogOpen: () => true,
            apply: (state) => calls.applied.push(state),
            ...overrides,
        };
        return { calls, result: fillFromAi(deps) };
    };

    test('posts the prompt with the user header and applies the mapped state', async () => {
        const { calls, result } = run();
        expect(await result).toBe('applied');
        expect(calls.requests[0].url).toBe('/api/ai/fill');
        expect(calls.requests[0].options.headers['X-Cda-User']).toBe('anna');
        expect(JSON.parse(calls.requests[0].options.body)).toEqual({ prompt: 'Herzinsuffizienz' });
        expect(calls.applied).toHaveLength(1);
        expect(calls.applied[0].patient.givenName).toBe('Franz');
    });

    test('a result that arrives after the dialog was closed is discarded, not applied', async () => {
        const { calls, result } = run({ isDialogOpen: () => false });
        expect(await result).toBe('discarded');
        expect(calls.applied).toEqual([]);
    });

    test('request errors propagate and nothing is applied', async () => {
        const { calls, result } = run({ fetchJson: async () => { throw new Error('Tageslimit'); } });
        await expect(result).rejects.toThrow('Tageslimit');
        expect(calls.applied).toEqual([]);
    });
});
