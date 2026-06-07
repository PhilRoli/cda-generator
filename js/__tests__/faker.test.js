import { test, expect, describe } from 'bun:test';
import { isValidSvnr, generateRandomPatient } from '../faker.js';

// ---------------------------------------------------------------------------
// isValidSvnr — valid SVNRs
// ---------------------------------------------------------------------------

describe('isValidSvnr – valid inputs', () => {
    test('known-good SVNR from reference XML: 4160270104', () => {
        // Documented in faker.js source comment: check digit 0, born 27.01.2004
        // lfd=416, check=0, dd=27, mm=01, yy=04
        expect(isValidSvnr('4160270104')).toBe(true);
    });

    test('generated patients all have valid SVNRs', () => {
        // generateRandomPatient() uses the same computeSvnrCheckDigit logic → must be valid
        for (let i = 0; i < 20; i++) {
            const p = generateRandomPatient();
            expect(isValidSvnr(p.svnr)).toBe(true);
        }
    });
});

// ---------------------------------------------------------------------------
// isValidSvnr — invalid SVNRs
// ---------------------------------------------------------------------------

describe('isValidSvnr – invalid inputs', () => {
    test('9-digit number returns false (too short)', () => {
        expect(isValidSvnr('123456789')).toBe(false);
    });

    test('11-digit number returns false (too long)', () => {
        expect(isValidSvnr('12345678901')).toBe(false);
    });

    test('non-digit characters return false', () => {
        expect(isValidSvnr('123A567890')).toBe(false);
    });

    test('empty string returns false', () => {
        expect(isValidSvnr('')).toBe(false);
    });

    test('wrong check digit returns false', () => {
        // Start from the known-good SVNR 4160270104 but flip the check digit:
        // original check = 0 (position 3), change to 1 → should fail
        expect(isValidSvnr('4161270104')).toBe(false);
    });

    test('sequentially-incremented digits fail check-digit validation', () => {
        // "1234567890": lfd=123, check=4, dd=56, mm=78, yy=90
        // weights: 3,7,9,5,8,4,2,1,6  → 1*3+2*7+3*9+5*5+6*8+7*4+8*2+9*1+0*6 = 3+14+27+25+48+28+16+9+0 = 170; 170%11=5 ≠ 4
        expect(isValidSvnr('1234567890')).toBe(false);
    });
});
