
/**
 * Compress image file using Canvas
 * Strategy: Quality priority, NO resizing (unless strictly necessary for browser limits), pure JPEG compression
 * @param {File} file Original file
 * @returns {Promise<File>} Compressed file
 */
export const compressImage = (file) => {
    return new Promise((resolve, reject) => {
        // Increase threshold to 2MB - only compress really large files
        // For files < 2MB, upload original to keep max quality
        if (file.size < 2 * 1024 * 1024) {
            resolve(file)
            return
        }

        const reader = new FileReader()
        reader.readAsDataURL(file)
        reader.onload = (e) => {
            const img = new Image()
            img.src = e.target.result
            img.onload = () => {
                // Keep original dimensions
                const width = img.width
                const height = img.height
                
                // High quality compression only
                let quality = 0.92 

                // For files > 7MB, use lower quality to save space
                if (file.size > 7 * 1024 * 1024) {
                    quality = 0.85
                } else if (file.size > 5 * 1024 * 1024) {
                    quality = 0.88
                }

                const canvas = document.createElement('canvas')
                canvas.width = width
                canvas.height = height
                const ctx = canvas.getContext('2d')
                
                // Best interpolation settings
                ctx.imageSmoothingEnabled = true;
                ctx.imageSmoothingQuality = 'high';
                
                ctx.drawImage(img, 0, 0, width, height)

                canvas.toBlob((blob) => {
                    if (!blob) {
                        reject(new Error('Canvas to Blob failed'))
                        return
                    }
                    
                    console.log(`Compression: ${file.name} | Original: ${(file.size/1024/1024).toFixed(2)}MB | Compressed: ${(blob.size/1024/1024).toFixed(2)}MB | Quality: ${quality}`)
                    console.log(`Size reduction: ${((1 - blob.size/file.size) * 100).toFixed(2)}%`)
                    // Strict check: if compression doesn't save significant space (e.g. < 5% savings)
                    // or actually increases size, return ORIGINAL file
                    if (blob.size > file.size * 0.95) {
                        console.log('Compression inefficient, using original file')
                        resolve(file)
                        return
                    }

                    const newFile = new File([blob], file.name, {
                        type: 'image/jpeg',
                        lastModified: Date.now()
                    })
                    resolve(newFile)
                }, 'image/jpeg', quality)
            }
            img.onerror = (err) => reject(err)
        }
        reader.onerror = (err) => reject(err)
    })
}
