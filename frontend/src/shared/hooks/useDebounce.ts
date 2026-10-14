import { useState, useEffect } from 'react';

/**
 * Hook to debounce a value with caller-provided delay in milliseconds.
 * Does not invent default timing policy.
 *
 * @param value The raw value to debounce
 * @param delayMs Caller-specified delay in milliseconds
 */
export function useDebounce<T>(value: T, delayMs: number): T {
    const [debouncedValue, setDebouncedValue] = useState<T>(value);

    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedValue(value);
        }, delayMs);

        return () => {
            clearTimeout(handler);
        };
    }, [value, delayMs]);

    return debouncedValue;
}