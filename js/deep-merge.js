/**
 * deepMerge — recursively merges `override` onto `base`.
 *
 * Rules:
 *  - Returns a new object; neither `base` nor `override` is mutated.
 *  - If both `base[key]` and `override[key]` are plain objects (not arrays,
 *    not null), the key is merged recursively.
 *  - Arrays are replaced wholesale (the loaded array wins as-is, including
 *    an explicitly empty array).
 *  - For every other type (primitives, null, array, undefined) the
 *    `override` value wins.
 *  - Keys present only in `override` are kept in the result.
 *  - Keys present only in `base` are kept in the result.
 *
 * @param {object} base     - The defaults object (must be a plain object).
 * @param {object} override - The loaded / partial object to merge in.
 * @returns {object}        A fresh merged object.
 */
export function deepMerge(base, override) {
    if (!isPlainObject(base) || !isPlainObject(override)) {
        // If either side isn't a plain object, the override wins outright.
        return override !== undefined ? override : base;
    }

    const result = Object.assign({}, base);

    for (const key of Object.keys(override)) {
        const baseVal = base[key];
        const overrideVal = override[key];

        if (isPlainObject(baseVal) && isPlainObject(overrideVal)) {
            result[key] = deepMerge(baseVal, overrideVal);
        } else {
            result[key] = overrideVal;
        }
    }

    return result;
}

function isPlainObject(value) {
    return (
        value !== null &&
        typeof value === 'object' &&
        !Array.isArray(value)
    );
}
