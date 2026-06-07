import { test, expect, describe } from 'bun:test';
import { escapeXml, toHl7Time } from '../cda-builder.js';

// ---------------------------------------------------------------------------
// escapeXml
// ---------------------------------------------------------------------------

describe('escapeXml', () => {
    test('escapes & character', () => {
        expect(escapeXml('a & b')).toBe('a &amp; b');
    });

    test('escapes < character', () => {
        expect(escapeXml('<tag>')).toBe('&lt;tag&gt;');
    });

    test('escapes > character', () => {
        expect(escapeXml('a > b')).toBe('a &gt; b');
    });

    test('escapes double-quote', () => {
        expect(escapeXml('say "hello"')).toBe('say &quot;hello&quot;');
    });

    test("escapes single-quote (apostrophe)", () => {
        expect(escapeXml("it's")).toBe("it&apos;s");
    });

    test('escapes all special chars in one string', () => {
        expect(escapeXml('&<>"\'')).toBe('&amp;&lt;&gt;&quot;&apos;');
    });

    test('null returns empty string', () => {
        expect(escapeXml(null)).toBe('');
    });

    test('undefined returns empty string', () => {
        expect(escapeXml(undefined)).toBe('');
    });

    test('plain text is unchanged', () => {
        expect(escapeXml('Hello World 123')).toBe('Hello World 123');
    });
});

// ---------------------------------------------------------------------------
// toHl7Time
// ---------------------------------------------------------------------------

describe('toHl7Time', () => {
    test('empty string returns empty string', () => {
        expect(toHl7Time('')).toBe('');
    });

    test('null returns empty string', () => {
        expect(toHl7Time(null)).toBe('');
    });

    test('undefined returns empty string', () => {
        expect(toHl7Time(undefined)).toBe('');
    });

    test('invalid date string returns empty string', () => {
        expect(toHl7Time('not-a-date')).toBe('');
    });

    test('date-only with withTime=false returns YYYYMMDD (timezone-stable)', () => {
        // Using a date-only string: new Date('2024-06-15') is parsed as UTC midnight.
        // With withTime=false we return the local date components, which are stable
        // when the offset is non-negative (UTC+ timezones, e.g. Europe/Vienna).
        // We assert the format shape with a regex rather than an exact value to
        // avoid failures in negative-offset (Americas) CI environments.
        const result = toHl7Time('2024-06-15', false);
        expect(result).toMatch(/^\d{8}$/);
    });

    test('date-only with withTime=false for a known local-midnight value', () => {
        // Use a datetime string (not just a date) so that the local date is unambiguous.
        // "2024-06-15T12:00" is mid-day locally — local date portion is always 20240615.
        const result = toHl7Time('2024-06-15T12:00', false);
        expect(result).toBe('20240615');
    });

    test('withTime=true produces a 19-char string (YYYYMMDDHHMMSS+HHMM)', () => {
        // "2024-06-15T12:00" — local noon, date/time always stable.
        const result = toHl7Time('2024-06-15T12:00');
        // Format: 8 date chars + 6 time chars + sign + 4 tz chars = 19 chars
        expect(result).toMatch(/^\d{8}\d{6}[+-]\d{4}$/);
    });
});
