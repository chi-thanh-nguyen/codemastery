/**
 * Bounded FE01 contract selected under the Leader's 2026-10-23 D1/D2 delegation.
 * Callers own locale, display timezone, presentation and numeric rounding.
 * This is not a platform-wide formatting policy or approval of remaining CT37.
 */
export interface DateFormatOptions {
    locale: string;
    options: Intl.DateTimeFormatOptions & { timeZone: string };
}

export type NumberRoundingMode =
    | 'ceil' | 'floor' | 'expand' | 'trunc'
    | 'halfCeil' | 'halfFloor' | 'halfExpand' | 'halfTrunc' | 'halfEven';

type Precision =
    | { minimumFractionDigits: number; maximumFractionDigits: number;
        minimumSignificantDigits?: never; maximumSignificantDigits?: never }
    | { minimumSignificantDigits: number; maximumSignificantDigits: number;
        minimumFractionDigits?: never; maximumFractionDigits?: never };

export interface NumberFormatOptions {
    locale: string;
    options: Omit<Intl.NumberFormatOptions,
        'minimumFractionDigits' | 'maximumFractionDigits' |
        'minimumSignificantDigits' | 'maximumSignificantDigits'> & Precision & {
        style: 'decimal' | 'percent' | 'currency' | 'unit';
        roundingMode: NumberRoundingMode;
    };
}

const dateFields = [
    'dateStyle', 'timeStyle', 'weekday', 'era', 'year', 'month', 'day',
    'dayPeriod', 'hour', 'minute', 'second', 'fractionalSecondDigits', 'timeZoneName',
] as const;
const roundingModes: readonly string[] = [
    'ceil', 'floor', 'expand', 'trunc', 'halfCeil', 'halfFloor', 'halfExpand', 'halfTrunc', 'halfEven',
];

function requireLocale(locale: string, supported: (locales: string[]) => string[]) {
    if (!locale || supported([locale]).length === 0) {
        throw new RangeError('A supported caller-supplied locale is required.');
    }
}

/** Only complete ISO timestamps with an explicit offset and millisecond precision. */
function parseInstant(value: string): Date {
    const parts = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,3})?(Z|[+-]\d{2}:\d{2})$/.exec(value);
    if (!parts) return new Date(NaN);
    const [, y, m, d, h, min, sec] = parts;
    const year = Number(y), month = Number(m), day = Number(d);
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (month < 1 || month > 12 || day < 1 || day > days[month - 1] ||
        Number(h) > 23 || Number(min) > 59 || Number(sec) > 59) return new Date(NaN);
    return new Date(value);
}

/**
 * Date/epoch milliseconds or an explicit-offset ISO timestamp; ambiguous, invalid
 * and nullish values return ''. Invalid/missing configuration throws RangeError.
 * Locale-derived conventions remain caller-selected through locale/Intl options.
 */
export const formatDate = (
    value: Date | string | number | null | undefined,
    config: DateFormatOptions,
): string => {
    if (value === null || value === undefined) return '';
    const date = value instanceof Date ? value : typeof value === 'string' ? parseInstant(value) : new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    requireLocale(config.locale, Intl.DateTimeFormat.supportedLocalesOf);
    if (!config.options?.timeZone || !dateFields.some((field) => config.options[field] !== undefined)) {
        throw new RangeError('An explicit timezone and date/time presentation are required.');
    }
    return new Intl.DateTimeFormat(config.locale, config.options).format(date);
};

/**
 * One complete fraction/significant precision pair and an explicit rounding mode.
 * Native Intl validates remaining options (including currency/unit identifiers).
 * No fallback rounding, locale or currency is supplied by this helper.
 */
export const formatNumber = (
    value: number | null | undefined,
    config: NumberFormatOptions,
): string => {
    if (value === null || value === undefined || Number.isNaN(value)) return '';
    requireLocale(config.locale, Intl.NumberFormat.supportedLocalesOf);
    const options = config.options;
    const fraction = options?.minimumFractionDigits !== undefined && options.maximumFractionDigits !== undefined;
    const significant = options?.minimumSignificantDigits !== undefined && options.maximumSignificantDigits !== undefined;
    const hasFraction = options?.minimumFractionDigits !== undefined || options?.maximumFractionDigits !== undefined;
    const hasSignificant = options?.minimumSignificantDigits !== undefined || options?.maximumSignificantDigits !== undefined;
    if (!options?.style || !roundingModes.includes(options.roundingMode) ||
        (options.style === 'currency' && !options.currency) || (options.style === 'unit' && !options.unit) ||
        !(fraction && !hasSignificant || significant && !hasFraction)) {
        throw new RangeError('An explicit style, rounding mode and one complete precision pair are required.');
    }
    const formatter = new Intl.NumberFormat(config.locale, options);
    // ES2022 declarations omit this newer Intl option. Verify runtime support;
    // never silently accept a host that ignores the caller's rounding mode.
    const resolved = formatter.resolvedOptions() as Intl.ResolvedNumberFormatOptions & { roundingMode?: string };
    if (resolved.roundingMode !== options.roundingMode) {
        throw new RangeError('This runtime does not support the requested rounding mode.');
    }
    return formatter.format(value);
};

/**
 * Floor supported nonnegative finite seconds and return exact parts. Nullish,
 * negative, nonfinite or unsafe floored values return zero parts. -0 becomes 0.
 * This deliberately preserves the existing zero-fallback return shape.
 */
export const formatDurationSeconds = (
    totalSeconds: number | null | undefined,
): { hours: number; minutes: number; seconds: number } => {
    if (totalSeconds === null || totalSeconds === undefined || !Number.isFinite(totalSeconds) || totalSeconds < 0) {
        return { hours: 0, minutes: 0, seconds: 0 };
    }
    const whole = Math.floor(totalSeconds) || 0;
    if (!Number.isSafeInteger(whole)) return { hours: 0, minutes: 0, seconds: 0 };
    return { hours: Math.floor(whole / 3600), minutes: Math.floor(whole % 3600 / 60), seconds: whole % 60 };
};
