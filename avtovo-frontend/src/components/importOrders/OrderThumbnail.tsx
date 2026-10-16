import { useEffect, useState } from 'react';
import { Car } from 'lucide-react';
import { fetchAuctionImage } from '../../api/importOrders';

/**
 * An order's cover photo: the uploaded photo (presigned URL) if there is one, otherwise the first
 * auction photo, fetched through the logged-in image proxy because the auction sites block hotlinking.
 */
export default function OrderThumbnail({ thumbnailUrl, auctionPhotoUrl, alt }: {
    thumbnailUrl: string | null;
    auctionPhotoUrl: string | null;
    alt: string;
}) {
    const [proxied, setProxied] = useState<string | null>(null);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        if (thumbnailUrl || !auctionPhotoUrl) return;
        let objectUrl: string | null = null;
        let cancelled = false;
        fetchAuctionImage(auctionPhotoUrl)
            .then(blob => {
                if (cancelled) return;
                objectUrl = URL.createObjectURL(blob);
                setProxied(objectUrl);
            })
            .catch(() => !cancelled && setFailed(true));
        return () => {
            cancelled = true;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [thumbnailUrl, auctionPhotoUrl]);

    const src = thumbnailUrl ?? proxied;
    if (src && !failed) {
        return <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)}
                    className="w-full h-full object-cover" />;
    }
    return (
        <div className="w-full h-full flex items-center justify-center text-avtovo-muted">
            <Car size={36} strokeWidth={1.5} />
        </div>
    );
}
