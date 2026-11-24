import type { ImageInput } from '../ai/engine';

/** Downscales a screenshot to a JPEG the on-device model accepts. */
export async function fileToImageInput(file: Blob, maxSide = 1280): Promise<ImageInput> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
  const w = Math.round(bitmap.width * scale);
  const h = Math.round(bitmap.height * scale);
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, w, h);
  ctx.drawImage(bitmap, 0, 0, w, h);
  const dataUrl = canvas.toDataURL('image/jpeg', 0.88);
  return { mimeType: 'image/jpeg', base64: dataUrl.split(',')[1], previewUrl: dataUrl };
}
