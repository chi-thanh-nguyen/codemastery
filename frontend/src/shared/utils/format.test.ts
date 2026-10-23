import { describe, expect, it, vi } from 'vitest';
import { formatDate, formatNumber, formatDurationSeconds } from './format';
import type { DateFormatOptions, NumberFormatOptions } from './format';

const utc: DateFormatOptions = { locale: 'en-US', options: { timeZone: 'UTC', year: 'numeric', month: '2-digit', day: '2-digit' } };
const decimal: NumberFormatOptions = { locale: 'en-US', options: {
    style: 'decimal', minimumFractionDigits: 1, maximumFractionDigits: 1, roundingMode: 'halfExpand',
} };

describe('caller-owned date formatting', () => {
    it('formats the same instant in explicitly selected zones', () => {
        const stamp = '2026-10-15T00:30:00Z';
        expect(formatDate(stamp, utc)).toBe('10/15/2026');
        expect(formatDate(stamp, { ...utc, options: { ...utc.options, timeZone: 'America/Los_Angeles' } })).toBe('10/14/2026');
        expect(formatDate('2026-10-15T07:30:00+07:00', utc)).toBe('10/15/2026');
        expect(formatDate(new Date(stamp), utc)).toBe('10/15/2026');
        expect(formatDate(Date.parse(stamp), utc)).toBe('10/15/2026');
    });
    it.each([null, undefined, '2026-10-15', '2026-10-15T00:30:00', '10/15/2026', '2026-02-30T00:00:00Z',
        '2025-02-29T00:00:00Z', '2026-13-01T00:00:00Z', '2026-01-01T24:00:00Z', 'invalid', new Date(NaN)])(
        'returns empty for ambiguous/invalid input %s', (value) => expect(formatDate(value, utc)).toBe(''),
    );
    it('accepts a leap day without normalizing invalid calendar dates', () => {
        expect(formatDate('2024-02-29T12:00:00.123Z', utc)).toBe('02/29/2024');
    });
    it('requires timezone, presentation and a supported locale at runtime', () => {
        for (const config of [
            { locale: 'en-US', options: { year: 'numeric' } },
            { locale: 'en-US', options: { timeZone: 'UTC' } },
            { ...utc, locale: '' }, { ...utc, locale: 'zz-ZZ' },
            { ...utc, options: { ...utc.options, timeZone: 'Invalid/Zone' } },
        ]) expect(() => formatDate(0, config as DateFormatOptions)).toThrow(RangeError);
    });
});

describe('caller-owned number formatting', () => {
    it('respects precision, rounding ties and signs', () => {
        expect(formatNumber(1.25, decimal)).toBe('1.3');
        expect(formatNumber(-1.25, decimal)).toBe('-1.3');
        expect(formatNumber(1.25, { ...decimal, options: { ...decimal.options, roundingMode: 'halfEven' } })).toBe('1.2');
        expect(formatNumber(-1.25, { ...decimal, options: { ...decimal.options, roundingMode: 'halfEven' } })).toBe('-1.2');
        expect(formatNumber(.123456, { locale: 'en-US', options: { style: 'decimal', minimumFractionDigits: 0,
            maximumFractionDigits: 6, roundingMode: 'trunc' } })).toBe('0.123456');
    });
    it('supports significant precision independently of fraction precision', () => {
        expect(formatNumber(1234.5, { locale: 'en-US', options: { style: 'decimal', minimumSignificantDigits: 3,
            maximumSignificantDigits: 3, roundingMode: 'halfExpand', useGrouping: false } })).toBe('1230');
    });
    it('uses caller-selected percent/currency presentation', () => {
        expect(formatNumber(.885, { ...decimal, options: { ...decimal.options, style: 'percent' } })).toBe('88.5%');
        expect(formatNumber(12.5, { ...decimal, options: { ...decimal.options, style: 'currency', currency: 'USD' } })).toBe('$12.5');
        expect(formatNumber(12.5, { ...decimal, locale: 'de-DE' })).toBe('12,5');
    });
    it.each([null, undefined, NaN])('preserves empty output for %s', (value) => expect(formatNumber(value, decimal)).toBe(''));
    it('preserves Intl infinity presentation independently of duration validation', () => {
        expect(formatNumber(Infinity, decimal)).toBe('∞');
    });
    it('rejects missing/mixed precision, missing rounding and unsupported locale', () => {
        for (const config of [
            { locale: 'en-US', options: { style: 'decimal' } },
            { ...decimal, options: { style: 'decimal', minimumFractionDigits: 1, maximumFractionDigits: 1 } },
            { ...decimal, options: { ...decimal.options, maximumSignificantDigits: 3 } },
            { ...decimal, options: { ...decimal.options, minimumFractionDigits: 3, maximumFractionDigits: 1 } },
            { ...decimal, locale: 'zz-ZZ' },
            { ...decimal, options: { ...decimal.options, style: 'currency' } },
        ]) expect(() => formatNumber(1.25, config as NumberFormatOptions)).toThrow(RangeError);
    });
    it('rejects a runtime that silently ignores roundingMode', () => {
        const spy = vi.spyOn(Intl.NumberFormat.prototype, 'resolvedOptions').mockReturnValue({} as Intl.ResolvedNumberFormatOptions);
        try { expect(() => formatNumber(1.25, decimal)).toThrow(RangeError); } finally { spy.mockRestore(); }
    });
});

describe('finite-range duration decomposition', () => {
    it.each([
        [0, [0, 0, 0]], [-0, [0, 0, 0]], [59, [0, 0, 59]], [60, [0, 1, 0]],
        [3599, [0, 59, 59]], [3600, [1, 0, 0]], [3725, [1, 2, 5]], [3725.99, [1, 2, 5]],
        [Number.MAX_SAFE_INTEGER, [2501999792983, 36, 31]],
    ])('decomposes %s into exact parts', (value, expected) => {
        const parts = formatDurationSeconds(value as number);
        expect(Object.values(parts)).toEqual(expected);
        expect(parts.hours * 3600 + parts.minutes * 60 + parts.seconds).toBe(Math.floor(value as number) || 0);
    });
    it.each([null, undefined, NaN, Infinity, -Infinity, -1, -.1, Number.MAX_SAFE_INTEGER + 1, Number.MAX_VALUE])(
        'returns zero parts for %s', (value) => expect(formatDurationSeconds(value)).toEqual({ hours: 0, minutes: 0, seconds: 0 }),
    );
});

// Compile-time regressions: declarations must not restore optional policy.
function typeContract() {
    // @ts-expect-error timezone is required
    const date: DateFormatOptions = { locale: 'en-US', options: { dateStyle: 'full' } };
    // @ts-expect-error both precision bounds and rounding are required
    const number: NumberFormatOptions = { locale: 'en-US', options: { style: 'decimal' } };
    return [date, number];
}
void typeContract;
