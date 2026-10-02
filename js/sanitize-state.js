/**
 * sanitizeState — merges loaded (untrusted) form state onto the defaults while keeping
 * the defaults' shape.
 *
 * Loaded state comes from localStorage, local JSON files and shared cloud scenarios.
 * A plain deep merge would let e.g. `"patient": null` replace the whole object, which
 * crashes rendering and — because the broken state is persisted — every later reload.
 *
 * Rules, driven by the type of the default value:
 *  - plain object → merged recursively; a non-object loaded value keeps the default.
 *  - array        → a loaded array replaces it wholesale (empty included); items that
 *                   aren't plain objects are dropped and item fields are coerced to
 *                   strings (numbers/booleans) or dropped. A non-array keeps the default.
 *  - string       → a loaded string or number/boolean (as string) wins, else the default.
 *  - keys only present in the loaded state are kept; arrays among them get their items
 *    cleaned the same way.
 *
 * Neither argument is mutated.
 *
 * @param {unknown} loaded   - parsed JSON from any source
 * @param {object}  defaults - result of defaultState()
 * @returns {object} a fresh, shape-safe state
 */
export function sanitizeState(loaded, defaults) {
    return mergeObject(defaults, isPlainObject(loaded) ? loaded : {});
}

function mergeObject(base, override) {
    const result = {};
    for (const key of Object.keys(base)) {
        result[key] = key in override ? mergeValue(base[key], override[key]) : clone(base[key]);
    }
    for (const key of Object.keys(override)) {
        if (!(key in base)) {
            result[key] = Array.isArray(override[key]) ? cleanList(override[key]) : override[key];
        }
    }
    return result;
}

function mergeValue(base, value) {
    if (isPlainObject(base)) {
        return isPlainObject(value) ? mergeObject(base, value) : clone(base);
    }
    if (Array.isArray(base)) {
        return Array.isArray(value) ? cleanList(value) : clone(base);
    }
    if (typeof base === 'string') {
        const text = asText(value);
        return text !== undefined ? text : base;
    }
    return value === undefined ? base : value;
}

function cleanList(items) {
    return items.filter(isPlainObject).map((item) => {
        const clean = {};
        for (const [key, value] of Object.entries(item)) {
            const text = asText(value);
            if (text !== undefined) clean[key] = text;
        }
        return clean;
    });
}

function asText(value) {
    if (typeof value === 'string') return value;
    if (typeof value === 'number' || typeof value === 'boolean') return String(value);
    return undefined;
}

function clone(value) {
    return structuredClone(value);
}

function isPlainObject(value) {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
}
