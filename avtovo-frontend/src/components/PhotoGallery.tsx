import { useState } from 'react';
import { ChevronLeft, ChevronRight, ImageOff } from 'lucide-react';

export interface GalleryPhoto {
    thumb: string;
    large: string;
}

interface Props {
    photos: GalleryPhoto[];
    alt: string;
}

// Production-style gallery: one large selected photo + a row of small thumbnails (otomoto/mobile.de pattern).
export default function PhotoGallery({ photos, alt }: Props) {
    const [idx, setIdx] = useState(0);

    if (photos.length === 0) {
        return (
            <div className="aspect-[16/10] bg-avtovo-card border border-avtovo-border rounded-xl flex items-center justify-center text-avtovo-border">
                <ImageOff size={48} />
            </div>
        );
    }

    const current = photos[Math.min(idx, photos.length - 1)];

    return (
        <div className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden">
            <div className="relative aspect-[16/10] bg-avtovo-bg">
                <img src={current.large} alt={alt} className="w-full h-full object-cover" />
                {photos.length > 1 && (
                    <>
                        <button
                            onClick={() => setIdx(i => (i - 1 + photos.length) % photos.length)}
                            className="absolute left-3 top-1/2 -translate-y-1/2 bg-black/60 hover:bg-black/80 rounded-full p-2 transition-colors"
                            aria-label="Poprzednie zdjęcie"
                        >
                            <ChevronLeft size={18} className="text-white" />
                        </button>
                        <button
                            onClick={() => setIdx(i => (i + 1) % photos.length)}
                            className="absolute right-3 top-1/2 -translate-y-1/2 bg-black/60 hover:bg-black/80 rounded-full p-2 transition-colors"
                            aria-label="Następne zdjęcie"
                        >
                            <ChevronRight size={18} className="text-white" />
                        </button>
                        <span className="absolute bottom-3 right-3 text-xs font-medium px-2 py-1 rounded-md bg-black/60 text-white">
                            {idx + 1} / {photos.length}
                        </span>
                    </>
                )}
            </div>
            {photos.length > 1 && (
                <div className="flex gap-2 p-3 overflow-x-auto">
                    {photos.map((p, i) => (
                        <button
                            key={i}
                            onClick={() => setIdx(i)}
                            className={`w-16 h-12 rounded-lg overflow-hidden flex-shrink-0 border-2 transition-colors ${
                                i === idx ? 'border-avtovo-accent' : 'border-transparent opacity-70 hover:opacity-100'
                            }`}
                        >
                            <img src={p.thumb} alt="" className="w-full h-full object-cover" />
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
