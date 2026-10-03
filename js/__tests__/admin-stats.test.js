import { test, expect, describe } from 'bun:test';
import { delta, kpis, chartSeries } from '../admin/stats.js';

describe('delta', () => {
    test('percent change with sign', () => {
        expect(delta(112, 100)).toEqual({ text: '+12 %', worse: false });
        expect(delta(96, 100)).toEqual({ text: '−4 %', worse: false });
    });
    test('unchanged value is ±0 %', () => {
        expect(delta(100, 100)).toEqual({ text: '±0 %', worse: false });
    });
    test('no previous data', () => {
        expect(delta(5, 0)).toEqual({ text: 'neu', worse: false });
        expect(delta(0, 0)).toEqual({ text: '±0', worse: false });
    });
});

describe('kpis', () => {
    const t = (ok, failed) => ({ ok, failed });
    const summary = {
        current: { pdf: t(400, 6), clean_pdf: t(30, 1), xml_download: t(120, 0), scenario_save: t(9, 0), scenario_load: t(20, 0) },
        previous: { pdf: t(357, 2), clean_pdf: t(30, 0), xml_download: t(125, 0), scenario_save: t(0, 0), scenario_load: t(0, 0) },
    };

    test('three generation tiles plus a failure tile', () => {
        const tiles = kpis(summary);
        expect(tiles.map((k) => k.label)).toEqual(['PDFs', 'Saubere PDFs', 'XML-Downloads', 'Fehler (1,3 %)']);
        expect(tiles[0]).toEqual({ label: 'PDFs', value: 400, delta: { text: '+12 %', worse: false } });
    });

    test('more failures than before is marked worse', () => {
        expect(kpis(summary)[3].value).toBe(7);
        expect(kpis(summary)[3].delta.worse).toBe(true);
        expect(kpis(summary)[3].delta.text).toBe('+250 %');
    });

    const withFailures = (now, before) => ({
        current: { ...summary.current, pdf: t(10, now), clean_pdf: t(30, 0) },
        previous: { ...summary.previous, pdf: t(10, before) },
    });

    test('fewer or equal failures are not worse', () => {
        const fewer = kpis(withFailures(1, 4))[3];
        expect(fewer.delta).toEqual({ text: '−75 %', worse: false });
        const same = kpis(withFailures(3, 3))[3];
        expect(same.delta).toEqual({ text: '±0 %', worse: false });
    });

    test('first failures after a clean period are worse and "neu"', () => {
        expect(kpis(withFailures(2, 0))[3].delta).toEqual({ text: 'neu', worse: true });
    });

    test('zero attempts gives a 0,0 % failure rate', () => {
        const z = { ok: 0, failed: 0 };
        const empty = { pdf: z, clean_pdf: z, xml_download: z, scenario_save: z, scenario_load: z };
        const tile = kpis({ current: empty, previous: empty })[3];
        expect(tile.label).toBe('Fehler (0,0 %)');
        expect(tile.delta).toEqual({ text: '±0', worse: false });
    });
});

describe('chartSeries', () => {
    test('one value per day per type, including empty days', () => {
        const timeline = {
            entries: [
                { day: '2026-10-01', ok: { pdf: 2, clean_pdf: 0, xml_download: 1 }, failed: 1 },
                { day: '2026-10-02', ok: { pdf: 0, clean_pdf: 0, xml_download: 0 }, failed: 0 },
            ],
        };
        const s = chartSeries(timeline);
        expect(s.days).toEqual(['2026-10-01', '2026-10-02']);
        expect(s.series.map((x) => x.key)).toEqual(['pdf', 'clean_pdf', 'xml_download']);
        expect(s.series[0].values).toEqual([2, 0]);
        expect(s.failed).toEqual([1, 0]);
        expect(s.max).toBe(3);
    });

    test('max is at least 1 so an empty chart does not divide by zero', () => {
        expect(chartSeries({ entries: [{ day: 'd', ok: {}, failed: 0 }] }).max).toBe(1);
    });
});
