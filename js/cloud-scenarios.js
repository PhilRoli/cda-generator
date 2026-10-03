// Pure helpers for cloud-scenario handling (kept out of app.js so they are testable).

/**
 * Decides whether a cloud save updates an existing scenario or creates a new one.
 *
 * Only the scenario that was actually loaded from the cloud is ever updated, and only
 * when it belongs to the current user — merely having one selected in the dropdown, or
 * having loaded someone else's shared scenario, results in a new scenario.
 *
 * @param {{id: string, username: string} | null} loaded - scenario last loaded/saved
 * @param {string} username - current cloud username
 * @param {{asNew?: boolean}} [options] - asNew forces a new scenario ("Als neu speichern")
 * @returns {string | undefined} id to update, or undefined to create a new scenario
 */
export function scenarioIdToUpdate(loaded, username, { asNew = false } = {}) {
    if (asNew || !loaded) return undefined;
    return loaded.username === username.trim() ? loaded.id : undefined;
}

/**
 * Header object attributing a request to the cloud username. HTTP header values must be
 * ISO-8859-1, so the free-text name is percent-encoded (the server decodes it).
 */
export function cloudUserHeader(username) {
    return { 'X-Cda-User': encodeURIComponent(username) };
}
