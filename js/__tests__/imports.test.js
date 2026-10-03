import { test, expect } from 'bun:test';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const JS_DIR = join(import.meta.dir, '..');

function listModules(dir) {
    return readdirSync(dir).flatMap((name) => {
        const p = join(dir, name);
        if (statSync(p).isDirectory()) return name === '__tests__' ? [] : listModules(p);
        return name.endsWith('.js') ? [p] : [];
    });
}

// Naive comment stripping is enough here: calls mentioned in comments must not count.
const stripComments = (src) => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');

const modules = listModules(JS_DIR).map((path) => ({ path, src: stripComments(readFileSync(path, 'utf8')) }));

const EXPORT_RE = /^export\s+(?:async\s+)?function\s+(\w+)/gm;
const exportsByModule = new Map(
    modules.map(({ path, src }) => [path, [...src.matchAll(EXPORT_RE)].map((m) => m[1])]),
);

function importedNames(src) {
    const names = new Set();
    for (const m of src.matchAll(/import\s*\{([^}]*)\}\s*from/g)) {
        for (const part of m[1].split(',')) {
            const alias = part.trim().split(/\s+as\s+/).pop();
            if (alias) names.add(alias);
        }
    }
    return names;
}

test('every call to a function exported by another js module is defined or imported', () => {
    const problems = [];
    for (const { path, src } of modules) {
        const imported = importedNames(src);
        for (const [otherPath, names] of exportsByModule) {
            if (otherPath === path) continue;
            for (const name of names) {
                const defined = new RegExp(`function\\s+${name}\\b`).test(src);
                const called = new RegExp(`(?<![.\\w])${name}\\(`).test(src);
                if (called && !defined && !imported.has(name)) {
                    problems.push(`${path.slice(JS_DIR.length + 1)} uses ${name}() without importing it`);
                }
            }
        }
    }
    expect(problems).toEqual([]);
});
