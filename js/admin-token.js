// Admin token for /api/admin/* calls: asked once per page session via a password
// dialog, kept in memory only (never in storage), dropped when the server rejects it.

import { apiJson } from './api.js';

let sessionToken = null;
let pendingToken = null; // one shared prompt for concurrent callers
let confirmed = false;   // sessionToken has been accepted by the server at least once
let probe = null;        // the single in-flight request that verifies a fresh token

export function tokenAwareOptions(options, token) {
    return { ...options, headers: { ...(options.headers || {}), Authorization: `Bearer ${token}` } };
}

export function forgetAdminToken() {
    sessionToken = null;
    confirmed = false;
}

function askToken() {
    if (pendingToken) return pendingToken;
    const dialog = document.getElementById('admin-token-dialog');
    const input = document.getElementById('admin-token-input');
    input.value = '';
    pendingToken = new Promise((resolve) => {
        dialog.addEventListener('close', () => {
            pendingToken = null;
            const token = dialog.returnValue === 'ok' ? input.value.trim() : '';
            input.value = '';
            resolve(token || null);
        }, { once: true });
        dialog.returnValue = '';
        dialog.showModal();
    });
    return pendingToken;
}

/**
 * apiJson for admin endpoints. Rejects with err.cancelled = true if the user cancels the dialog.
 *
 * A freshly entered token is unverified, and every 403 counts toward the server's auth-failure
 * throttle. So the first request after a prompt is sent alone; concurrent callers wait for its
 * outcome and then re-evaluate: token confirmed -> proceed in parallel, token rejected (forgotten)
 * -> they share one fresh prompt. A confirmed token never serialises requests.
 */
export async function adminApiJson(url, options = {}) {
    for (;;) {
        if (!sessionToken) {
            const entered = await askToken();
            if (!entered) {
                const cancelled = new Error('Abgebrochen.');
                cancelled.cancelled = true;
                throw cancelled;
            }
            if (!sessionToken) {
                sessionToken = entered;
                confirmed = false;
            }
        }
        const token = sessionToken;
        if (!confirmed && probe) {
            await probe.catch(() => {});
            continue;
        }
        const request = apiJson(url, tokenAwareOptions(options, token)).then(
            (result) => {
                if (sessionToken === token) confirmed = true;
                return result;
            },
            (err) => {
                if (err.status === 403) forgetAdminToken();
                throw err;
            },
        );
        if (confirmed) return request;
        probe = request;
        try {
            return await request;
        } finally {
            probe = null;
        }
    }
}
