export const getThumbnailUrl = (url) => {
    if (!url) return ''
    if (url.includes('placehold.co')) return url
    // If it's a blob URL, it's likely a local preview or a broken link.
    // If it's stored in DB (broken), it will fail to load. 
    // We can't fix the data here, but we can prevent it from being processed as a thumbnail.
    if (url.startsWith('blob:')) return url
    
    // Check if it's a local upload URL (contains /uploads/)
    if (url.includes('/uploads/')) {
        // Avoid double thumbing if already thumb
        if (url.includes('_thumb.')) return url
        
        const lastDotIndex = url.lastIndexOf('.')
        if (lastDotIndex !== -1) {
            return url.substring(0, lastDotIndex) + '_thumb.jpg'
        }
    }
    return url
}
