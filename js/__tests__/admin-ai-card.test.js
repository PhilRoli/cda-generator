import { test, expect } from 'bun:test';
import { aiCardRows, settleOptional } from '../admin/stats.js';

test('shows fills, tokens, estimated spend and budget in German number format', () => {
    const rows = aiCardRows({ month: '2026-10', fills: 12, inputTokens: 18000, outputTokens: 8400, estimatedUsd: 0.006, budgetUsd: 40 });
    expect(rows).toEqual([
        ['Monat (UTC)', '2026-10'],
        ['Ausfüllungen', '12'],
        ['Tokens (Eingabe / Ausgabe)', `${(18000).toLocaleString('de-AT')} / ${(8400).toLocaleString('de-AT')}`],
        ['Geschätzte Kosten', '0,01 $'],
        ['Monatslimit', '40,00 $'],
    ]);
});

test('zero usage renders without NaN', () => {
    const rows = aiCardRows({ month: '2026-10', fills: 0, inputTokens: 0, outputTokens: 0, estimatedUsd: 0, budgetUsd: 40 });
    expect(rows.flat().join(' ')).not.toContain('NaN');
});

test('an optional request that fails resolves to null instead of failing the tab', async () => {
    expect(await settleOptional(Promise.reject(new Error('HTTP 500')))).toBeNull();
    expect(await settleOptional(Promise.resolve({ fills: 1 }))).toEqual({ fills: 1 });
});

test('a cancelled request (token prompt dismissed) still propagates', async () => {
    const cancelled = Object.assign(new Error('abgebrochen'), { cancelled: true });
    await expect(settleOptional(Promise.reject(cancelled))).rejects.toBe(cancelled);
});
