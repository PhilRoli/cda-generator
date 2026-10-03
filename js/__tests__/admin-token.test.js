import { test, expect } from 'bun:test';
import { tokenAwareOptions } from '../admin-token.js';

test('adds the bearer token without dropping existing headers', () => {
    const merged = tokenAwareOptions({ method: 'POST', headers: { 'Content-Type': 'application/json' } }, 'abc');
    expect(merged).toEqual({
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer abc' },
    });
});

test('does not mutate the caller options', () => {
    const options = { headers: {} };
    tokenAwareOptions(options, 'abc');
    expect(options).toEqual({ headers: {} });
});
