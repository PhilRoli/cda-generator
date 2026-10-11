import { test, expect, describe } from 'bun:test';
import { buildStateFromAi } from '../ai-state.js';
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
