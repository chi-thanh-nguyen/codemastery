/**
 * Parameterized formatting utilities without hardcoded locale/timezone policies.
 * All formatting policies must be provided by the caller.
 */

export interface DateFormatOptions {
    locale: string;
    options?: Intl.DateTimeFormatOptions;
}

export interface NumberFormatOptions {
    locale: string;
    options?: Intl.NumberFormatOptions;
}

/**
 * Format a Date or timestamp string with caller-supplied locale and options.
 */
export const formatDate = (
    value: Date | string | number | null | undefined,
    config: DateFormatOptions
): string => {
    if (value === null || value === undefined) {
        return '';
    }

    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '';
    }

    return new Intl.DateTimeFormat(config.locale, config.options).format(date);
};

/**
 * Format a number with caller-supplied locale and options (currency, percentages, decimals).
 */
export const formatNumber = (
    value: number | null | undefined,
    config: NumberFormatOptions
): string => {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return '';
    }

    return new Intl.NumberFormat(config.locale, config.options).format(value);
};

/**
 * Format a duration in seconds into human-readable caller-defined parts (e.g., mm:ss or hh:mm:ss).
 */
export const formatDurationSeconds = (
    totalSeconds: number | null | undefined
): { hours: number; minutes: number; seconds: number } => {
    if (totalSeconds === null || totalSeconds === undefined || totalSeconds < 0 || Number.isNaN(totalSeconds)) {
        return { hours: 0, minutes: 0, seconds: 0 };
    }

    const rounded = Math.floor(totalSeconds);
    const hours = Math.floor(rounded / 3600);
    const minutes = Math.floor((rounded % 3600) / 60);
    const seconds = rounded % 60;

    return { hours, minutes, seconds };
};