import { test, expect, afterEach } from 'bun:test';
import { apiJson } from '../api.js';

const realFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = realFetch; });

const respondWith = (body, init) => { globalThis.fetch = async () => new Response(body, init); };

test('a successful response with an empty body resolves to null', async () => {
    // DELETE /api/admin/scenarios/{id} answers 200 with no body; parsing that as JSON
    // used to throw, so a successful delete was reported as a failure.
    respondWith('', { status: 200 });
    expect(await apiJson('/api/x', { method: 'DELETE' })).toBeNull();
});

test('JSON bodies are parsed', async () => {
    respondWith('{"version":"1.4.0"}', { status: 200, headers: { 'Content-Type': 'application/json' } });
    expect(await apiJson('/api/version')).toEqual({ version: '1.4.0' });
});

test('errors carry the server message and the HTTP status', async () => {
    respondWith('{"message":"Ungültiger Admin-Token."}', { status: 403 });
    const err = await apiJson('/api/admin/x').catch((e) => e);
    expect(err.message).toBe('Ungültiger Admin-Token.');
    expect(err.status).toBe(403);
});
