import { test, expect } from 'bun:test';
import { cloudUserHeader } from '../cloud-scenarios.js';

test.each(['Anna \u{1F691}', '\u5F20\u4F1F', 'J\u00FCrgen M\u00FCller', 'plain'])('header value for %s is ASCII and round-trips', (name) => {
    const value = cloudUserHeader(name)['X-Cda-User'];
    for (const ch of value) expect(ch.charCodeAt(0)).toBeLessThanOrEqual(0x7e);
    expect(decodeURIComponent(value)).toBe(name);
});
