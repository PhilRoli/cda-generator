import { test, expect, describe } from 'bun:test';
import { buildStateFromAi, fillFromAi, pickHospital, nameBareTitles } from '../ai-state.js';
import { readFileSync } from 'node:fs';
import { DEPARTMENTS } from '../hospitals.js';
import { isValidSvnr } from '../faker.js';

const ai = () => ({
    patient: {
        givenName: 'Franz', familyName: 'Huber', gender: 'M', birthDate: '1947-05-03', phone: '06641234567',
    },
    author: { title: 'Dr.', givenName: 'Eva', familyName: 'Maier' },
    encounter: { admissionDate: '2026-10-06T14:30', dischargeDate: '2026-10-11T11:00', ward: 'Kardiologie, Station 2A', department: 'KARDIOLOGIE' },
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
        expect(s.encounter.department).toBeUndefined(); // steering field, not part of the form state
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
            bundesland: 'S',
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
        expect(calls.applied[0].patient.address.postalCode.startsWith('5')).toBe(true); // Salzburg address pool
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

const H = (name, departments, city = 'Salzburg') => ({
    name, phone: '+43 1', street: 'S', houseNumber: '1', postalCode: '5020', city, departments,
});
const hospitals = {
    S: [
        H('Nur Chirurgie', [DEPARTMENTS.ALLGEMEINCHIRURGIE]),
        H('Interne', [DEPARTMENTS.INNERE_MEDIZIN]),
        H('Herzzentrum', [DEPARTMENTS.INNERE_MEDIZIN, DEPARTMENTS.KARDIOLOGIE]),
    ],
    W: [H('Wiener Spital', [DEPARTMENTS.NEUROLOGIE], 'Wien')],
    T: [],
};

describe('pickHospital', () => {
    const first = () => 0;
    test('only hospitals that have the department qualify', () => {
        expect(pickHospital(hospitals, 'S', 'KARDIOLOGIE', first).name).toBe('Herzzentrum');
        expect(pickHospital(hospitals, 'S', 'ALLGEMEINCHIRURGIE', first).name).toBe('Nur Chirurgie');
    });

    test('the random source chooses among several matches', () => {
        const names = [0, 0.99].map((r) => pickHospital(hospitals, 'S', 'INNERE_MEDIZIN', () => r).name);
        expect(names).toEqual(['Interne', 'Herzzentrum']);
    });

    test('an internal sub-specialty without its own department falls back to internal medicine, never to surgery', () => {
        const noCardio = { S: [hospitals.S[0], hospitals.S[1]] };
        expect(pickHospital(noCardio, 'S', 'KARDIOLOGIE', first).name).toBe('Interne');
        expect(pickHospital(noCardio, 'S', 'GASTROENTEROLOGIE', () => 0.99).name).toBe('Interne');
    });

    test('with no hospital for the department any hospital of the Bundesland is better than none', () => {
        expect(pickHospital(hospitals, 'W', 'KARDIOLOGIE', first).name).toBe('Wiener Spital');
    });

    test('unknown or empty Bundesland and unknown department give null or a safe pick', () => {
        expect(pickHospital(hospitals, 'T', 'KARDIOLOGIE', first)).toBeNull();
        expect(pickHospital(hospitals, '', 'KARDIOLOGIE', first)).toBeNull();
        expect(pickHospital(hospitals, 'S', 'GIBTS_NICHT', first)).not.toBeNull();
    });
});

describe('buildStateFromAi with a Bundesland', () => {
    test('organization comes from a real hospital that fits the department, address from the Bundesland pool', () => {
        const s = buildStateFromAi(ai(), { bundesland: 'S', hospitals, random: () => 0 });
        expect(s.organization.name).toBe('Herzzentrum');
        expect(s.organization.address).toEqual({ street: 'S', houseNumber: '1', postalCode: '5020', city: 'Salzburg', country: 'A' });
        expect(s.organization.phone).toBe('+43 1');
        expect(s.patient.address.postalCode.startsWith('5')).toBe(true);
    });

    test('without a Bundesland the organization stays as in the defaults but the patient still gets a pool address', () => {
        const s = buildStateFromAi(ai(), { hospitals });
        expect(s.organization.name).toBe('Universitätsklinikum Salzburg Landeskrankenhaus');
        expect(s.patient.address.street).not.toBe('Linzer Bundesstraße'.repeat(2));
        expect(s.patient.address.country).toBe('A');
        expect(s.patient.address.postalCode).toMatch(/^\d{4}$/);
    });
});

describe('nameBareTitles', () => {
    test('a bare title in front of the age gets the family name', () => {
        expect(nameBareTitles('Herr 78 Jahre alt, wurde aufgenommen.', 'Huber')).toBe('Herr Huber 78 Jahre alt, wurde aufgenommen.');
        expect(nameBareTitles('Frau 81-jährig stellte sich vor.', 'Gruber')).toBe('Frau Gruber 81-jährig stellte sich vor.');
        expect(nameBareTitles('Herr, 78 Jahre alt, kam.', 'Huber')).toBe('Herr Huber, 78 Jahre alt, kam.');
    });

    test('text that already names the patient or does not match is left alone', () => {
        expect(nameBareTitles('Herr Huber, 78 Jahre alt, kam.', 'Huber')).toBe('Herr Huber, 78 Jahre alt, kam.');
        expect(nameBareTitles('Der 78-jährige Herr kam.', 'Huber')).toBe('Der 78-jährige Herr kam.');
        expect(nameBareTitles('', 'Huber')).toBe('');
        expect(nameBareTitles('Herr 78 Jahre alt', '')).toBe('Herr 78 Jahre alt');
    });

    test('is applied to the generated texts', () => {
        const a = ai();
        a.anamnese = 'Herr 78 Jahre alt, bekannte Herzinsuffizienz.';
        a.verlauf = 'Frau, 78 Jahre alt, ...';
        const s = buildStateFromAi(a);
        expect(s.anamnese).toBe('Herr Huber 78 Jahre alt, bekannte Herzinsuffizienz.');
        expect(s.verlauf).toBe('Frau Huber, 78 Jahre alt, ...');
    });
});

describe('department vocabulary', () => {
    test('the Java enum and DEPARTMENTS in hospitals.js list the same constants', () => {
        const java = readFileSync(new URL('../../src/main/java/at/rolinek/cda/ai/AiFillState.java', import.meta.url), 'utf8');
        const block = /enum Department \{([^}]*)\}/.exec(java)[1];
        const names = block.split(',').map((n) => n.trim()).filter(Boolean);
        expect(Object.keys(DEPARTMENTS).sort()).toEqual([...names].sort());
    });
});
