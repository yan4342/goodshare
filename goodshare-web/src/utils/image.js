export const getThumbnailUrl = (url) => {
    if (!url) return ''
    if (url.includes('placehold.co')) return url
    if (url.startsWith('blob:')) return url
    
    // Check if it's a local upload URL (contains /uploads/)
    if (url.includes('/uploads/')) {
        // Avoid double thumbing if already thumb
        if (url.includes('_thumb.')) return url
        
        const lastDotIndex = url.lastIndexOf('.')
        if (lastDotIndex !== -1) {
            return url.substring(0, lastDotIndex) + '_thumb' + url.substring(lastDotIndex)
        }
    }
    return url
}
