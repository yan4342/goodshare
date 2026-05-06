
const THUMB_MAX_DIM = 400
const THUMB_QUALITY = 0.7

export const compressImage = (file) => {
    return new Promise((resolve, reject) => {
        if (file.size < 2 * 1024 * 1024) {
            resolve({ file, thumbnail: null })
            return
        }

        const reader = new FileReader()
        reader.readAsDataURL(file)
        reader.onload = (e) => {
            const img = new Image()
            img.src = e.target.result
            img.onload = () => {
                const width = img.width
                const height = img.height

                let quality = 0.92
                if (file.size > 7 * 1024 * 1024) {
                    quality = 0.85
                } else if (file.size > 5 * 1024 * 1024) {
                    quality = 0.88
                }

                const mainCanvas = document.createElement('canvas')
                mainCanvas.width = width
                mainCanvas.height = height
                const mainCtx = mainCanvas.getContext('2d')
                mainCtx.imageSmoothingEnabled = true
                mainCtx.imageSmoothingQuality = 'high'
                mainCtx.drawImage(img, 0, 0, width, height)

                let thumbWidth, thumbHeight
                if (width > THUMB_MAX_DIM || height > THUMB_MAX_DIM) {
                    if (width > height) {
                        thumbWidth = THUMB_MAX_DIM
                        thumbHeight = Math.round(height / width * THUMB_MAX_DIM)
                    } else {
                        thumbHeight = THUMB_MAX_DIM
                        thumbWidth = Math.round(width / height * THUMB_MAX_DIM)
                    }
                } else {
                    thumbWidth = width
                    thumbHeight = height
                }

                const thumbCanvas = document.createElement('canvas')
                thumbCanvas.width = thumbWidth
                thumbCanvas.height = thumbHeight
                const thumbCtx = thumbCanvas.getContext('2d')
                thumbCtx.imageSmoothingEnabled = true
                thumbCtx.imageSmoothingQuality = 'high'
                thumbCtx.drawImage(img, 0, 0, thumbWidth, thumbHeight)

                const thumbNeeded = thumbWidth !== width || thumbHeight !== height

                const promises = [
                    new Promise(res => mainCanvas.toBlob(res, 'image/jpeg', quality))
                ]
                if (thumbNeeded) {
                    promises.push(new Promise(res => thumbCanvas.toBlob(res, 'image/jpeg', THUMB_QUALITY)))
                }

                Promise.all(promises).then(([mainBlob, thumbBlob]) => {
                    if (!mainBlob) {
                        reject(new Error('Canvas to Blob failed'))
                        return
                    }

                    console.log(`Compression: ${file.name} | Original: ${(file.size/1024/1024).toFixed(2)}MB | Compressed: ${(mainBlob.size/1024/1024).toFixed(2)}MB | Quality: ${quality}`)

                    const mainFile = mainBlob.size > file.size * 0.95
                        ? file
                        : new File([mainBlob], file.name, { type: 'image/jpeg', lastModified: Date.now() })

                    let thumbFile = null
                    if (thumbBlob && thumbNeeded) {
                        const thumbName = file.name.replace(/\.[^.]+$/, '_thumb.jpg')
                        thumbFile = new File([thumbBlob], thumbName, { type: 'image/jpeg', lastModified: Date.now() })
                        if (thumbFile.size >= mainFile.size) {
                            console.log('Thumbnail not smaller than main, discarding')
                            thumbFile = null
                        }
                    }

                    resolve({ file: mainFile, thumbnail: thumbFile })
                }).catch(reject)
            }
            img.onerror = (err) => reject(err)
        }
        reader.onerror = (err) => reject(err)
    })
}
