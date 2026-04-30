/**
 * Parse a server timestamp string into a Date object.
 * Handles both ISO ("yyyy-MM-ddTHH:mm:ss") and legacy ("yyyy-MM-dd HH:mm:ss") formats.
 * Assumes server timestamps without timezone are in Asia/Shanghai (UTC+8).
 */
export function parseServerTime(timeStr) {
    if (!timeStr) return null
    let normalized = String(timeStr).replace(' ', 'T')
    if (!/[+\-Z]/.test(normalized.slice(-6))) {
        normalized += '+08:00'
    }
    return new Date(normalized)
}

/**
 * Format a server timestamp as relative time (e.g., "刚刚", "3分钟前", "2小时前").
 */
export function formatRelativeTime(timeStr) {
    const date = parseServerTime(timeStr)
    if (!date) return ''
    const now = new Date()
    const diff = now.getTime() - date.getTime()

    if (diff < 60 * 1000) return '刚刚'
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))}分钟前`
    if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))}小时前`
    const days = Math.floor(diff / (24 * 60 * 60 * 1000))
    if (days < 30) return `${days}天前`
    return date.toLocaleDateString()
}

/**
 * Format a server timestamp as absolute local date/time string.
 */
export function formatAbsoluteTime(timeStr) {
    const date = parseServerTime(timeStr)
    if (!date) return ''
    return date.toLocaleString()
}

/**
 * Format as date string only.
 */
export function formatDate(timeStr) {
    const date = parseServerTime(timeStr)
    if (!date) return ''
    return date.toLocaleDateString()
}
