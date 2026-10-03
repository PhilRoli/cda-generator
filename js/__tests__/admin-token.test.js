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

// --- first request after a prompt is sent alone ---
import { adminApiJson, forgetAdminToken } from '../admin-token.js';

function fakeDom(tokens) {
    const queue = [...tokens];
    let prompts = 0;
    const input = { value: '' };
    const dialog = {
        returnValue: '',
        listener: null,
        addEventListener(_ev, cb) { this.listener = cb; },
        showModal() {
            prompts++;
            const next = queue.shift();
            setTimeout(() => {
                this.returnValue = next === undefined ? 'cancel' : 'ok';
                input.value = next ?? '';
                this.listener();
            }, 0);
        },
    };
    globalThis.document = { getElementById: (id) => (id === 'admin-token-dialog' ? dialog : input) };
    return { prompts: () => prompts };
}

function fakeFetch(goodToken) {
    const seen = [];
    globalThis.fetch = async (url, opts) => {
        const auth = opts.headers.Authorization;
        seen.push(auth);
        await new Promise((r) => setTimeout(r, 5));
        const ok = auth === `Bearer ${goodToken}`;
        return { ok, status: ok ? 200 : 403, json: async () => ({ message: 'nope' }), text: async () => '{"ok":true}' };
    };
    return seen;
}

test('wrong token with 5 concurrent calls: only one request reaches the server, then a fresh prompt', async () => {
    forgetAdminToken();
    const dom = fakeDom(['bad', 'good']);
    const seen = fakeFetch('good');
    const results = await Promise.allSettled([1, 2, 3, 4, 5].map((i) => adminApiJson(`/api/admin/x${i}`)));
    // the probing caller gets the 403; the four waiters re-prompt and succeed
    expect(results[0].status).toBe('rejected');
    expect(results[0].reason.status).toBe(403);
    expect(results.slice(1).every((r) => r.status === 'fulfilled')).toBe(true);
    expect(seen.filter((a) => a === 'Bearer bad').length).toBe(1);
    expect(dom.prompts()).toBe(2);
    expect(seen.filter((a) => a === 'Bearer good').length).toBe(4);
});

test('confirmed token does not serialise requests', async () => {
    forgetAdminToken();
    fakeDom(['good']);
    const seen = fakeFetch('good');
    await adminApiJson('/api/admin/first');
    seen.length = 0;
    let inflight = 0, peak = 0;
    const base = globalThis.fetch;
    globalThis.fetch = async (...a) => { inflight++; peak = Math.max(peak, inflight); try { return await base(...a); } finally { inflight--; } };
    await Promise.all([1, 2, 3].map((i) => adminApiJson(`/api/admin/y${i}`)));
    expect(peak).toBe(3);
});

test('cancel: no request, err.cancelled for every waiting caller', async () => {
    forgetAdminToken();
    fakeDom([]);
    const seen = fakeFetch('good');
    const res = await Promise.allSettled([adminApiJson('/a'), adminApiJson('/b')]);
    expect(res.every((r) => r.status === 'rejected' && r.reason.cancelled === true)).toBe(true);
    expect(seen.length).toBe(0);
});
