// Backend API client: JSON/PDF requests with the server's error message surfaced.

async function apiRequest(url, options = {}, responseType = 'json') {
    const response = await fetch(url, options);
    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try {
            const body = await response.json();
            if (body?.message) message = body.message;
        } catch {}
        const error = new Error(message);
        error.status = response.status;
        throw error;
    }
    if (responseType === 'blob') return response.blob();
    // Some endpoints (e.g. admin delete) answer 200 with an empty body.
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

export const apiJson = (url, options = {}) => apiRequest(url, options, 'json');
export const apiPdf  = (url, options = {}) => apiRequest(url, options, 'blob');
