/**
 * Downscales a photo in the browser before upload: longest side at most 2560 px, JPEG quality 0.85.
 * Smaller images are re-encoded only if that makes them smaller; anything that can't be decoded is
 * returned unchanged (the server still checks the type).
 */
export async function resizePhoto(file: File, maxSide = 2560, quality = 0.85): Promise<File> {
    let bitmap: ImageBitmap;
    try {
        bitmap = await createImageBitmap(file);
    } catch {
        return file;
    }
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();
    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', quality));
    if (!blob || (scale === 1 && blob.size >= file.size)) return file;
    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg';
    return new File([blob], name, { type: 'image/jpeg' });
}
