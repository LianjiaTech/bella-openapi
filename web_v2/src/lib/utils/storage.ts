export function safeGetItem(key: string): string | null {
    return localStorage.getItem(key)
}

export function safeSetItem(key: string, value: string): void {
    localStorage.setItem(key, value)
}

export function safeGetJSON<T>(key: string): T | null {
    return JSON.parse(localStorage.getItem(key) || 'null')
}

export function safeSetJSON<T>(key: string, value: T): void {
    localStorage.setItem(key, JSON.stringify(value))
}