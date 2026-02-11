
/**
 * Compress image file using Canvas
 * @param {File} file Original file
 * @returns {Promise<File>} Compressed file
 */
export const compressImage = (file) => {
    return new Promise((resolve, reject) => {
        // If file is small enough, return original (e.g., < 1MB)
        if (file.size < 1024 * 1024) {
            resolve(file)
            return
        }

        const reader = new FileReader()
        reader.readAsDataURL(file)
        reader.onload = (e) => {
            const img = new Image()
            img.src = e.target.result
            img.onload = () => {
                let width = img.width
                let height = img.height
                let quality = 0.8

                // Graded compression strategy
                const sizeMB = file.size / 1024 / 1024

                if (sizeMB > 5) {
                    // > 5MB: Aggressive compression
                    const maxDim = 1600
                    if (width > maxDim || height > maxDim) {
                        if (width > height) {
                            height = (height * maxDim) / width
                            width = maxDim
                        } else {
                            width = (width * maxDim) / height
                            height = maxDim
                        }
                    }
                    quality = 0.6
                } else if (sizeMB > 1) {
                    // 1MB - 5MB: Moderate compression
                    const maxDim = 1920
                    if (width > maxDim || height > maxDim) {
                        if (width > height) {
                            height = (height * maxDim) / width
                            width = maxDim
                        } else {
                            width = (width * maxDim) / height
                            height = maxDim
                        }
                    }
                    quality = 0.8
                }

                const canvas = document.createElement('canvas')
                canvas.width = width
                canvas.height = height
                const ctx = canvas.getContext('2d')
                ctx.drawImage(img, 0, 0, width, height)

                canvas.toBlob((blob) => {
                    if (!blob) {
                        reject(new Error('Canvas to Blob failed'))
                        return
                    }
                    
                    console.log(`Compression: ${file.name} | Original: ${(file.size/1024/1024).toFixed(2)}MB | Compressed: ${(blob.size/1024/1024).toFixed(2)}MB`)

                    // If compressed blob is somehow larger (rare but possible with low complexity images), return original
                    if (blob.size > file.size) {
                        console.log('Compressed file is larger, using original')
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
