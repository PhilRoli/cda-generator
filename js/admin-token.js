// Admin token for /api/admin/* calls: asked once per page session via a password
// dialog, kept in memory only (never in storage), dropped when the server rejects it.

import { apiJson } from './api.js';

let sessionToken = null;
let pendingToken = null; // one shared prompt for concurrent callers

export function tokenAwareOptions(options, token) {
    return { ...options, headers: { ...(options.headers || {}), Authorization: `Bearer ${token}` } };
}

export function forgetAdminToken() {
    sessionToken = null;
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

/** apiJson for admin endpoints. Rejects with err.cancelled = true if the user cancels the dialog. */
export async function adminApiJson(url, options = {}) {
    let token = sessionToken;
    if (!token) {
        token = await askToken();
        if (token) sessionToken = token;
        else {
            const cancelled = new Error('Abgebrochen.');
            cancelled.cancelled = true;
            throw cancelled;
        }
    }
    try {
        return await apiJson(url, tokenAwareOptions(options, token));
    } catch (err) {
        if (err.status === 403) forgetAdminToken();
        throw err;
    }
}
