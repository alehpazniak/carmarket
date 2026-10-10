export type VideoProvider = 'youtube' | 'tiktok' | 'instagram';

export interface VideoEmbed {
    provider: VideoProvider;
    embedUrl: string;
    /** YouTube Shorts, TikTok and Instagram Reels are vertical. */
    vertical: boolean;
}

const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;
const TIKTOK_ID = /\/(?:video\/|v\/)(\d{5,25})/;
// /reel/<code>, /reels/<code>, /p/<code>, /tv/<code>, optionally prefixed by /<username>
const INSTAGRAM_PATH = /^(?:\/[A-Za-z0-9._]{1,30})?\/(reels?|p|tv)\/([A-Za-z0-9_-]{5,40})\/?$/;
const INSTAGRAM_HOSTS = ['instagram.com', 'www.instagram.com', 'm.instagram.com', 'instagr.am'];

const toUrl = (raw: string): URL | null => {
    const trimmed = raw.trim();
    try {
        return new URL(/^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`);
    } catch {
        return null;
    }
};

/** Embed player for a YouTube, TikTok or Instagram video link, or null when the link has no video id. */
export const videoEmbed = (raw: string | null | undefined): VideoEmbed | null => {
    if (!raw) return null;
    const url = toUrl(raw);
    if (!url) return null;
    const host = url.hostname.toLowerCase();

    if (host === 'youtu.be' || host.endsWith('youtube.com')) {
        const shorts = url.pathname.startsWith('/shorts/');
        const id = host === 'youtu.be'
            ? url.pathname.slice(1)
            : url.searchParams.get('v') ?? url.pathname.split('/').filter(Boolean)[1] ?? '';
        if (!YOUTUBE_ID.test(id)) return null;
        return {provider: 'youtube', embedUrl: `https://www.youtube-nocookie.com/embed/${id}`, vertical: shorts};
    }
    if (host === 'tiktok.com' || host.endsWith('.tiktok.com')) {
        const id = url.pathname.match(TIKTOK_ID)?.[1];
        if (!id) return null;
        return {provider: 'tiktok', embedUrl: `https://www.tiktok.com/player/v1/${id}?rel=0`, vertical: true};
    }
    if (INSTAGRAM_HOSTS.includes(host)) {
        const match = url.pathname.match(INSTAGRAM_PATH);
        if (!match) return null;
        const kind = match[1] === 'p' ? 'p' : 'reel';
        return {provider: 'instagram', embedUrl: `https://www.instagram.com/${kind}/${match[2]}/embed`, vertical: true};
    }
    return null;
};

/** Whether the link looks like something car-service accepts (TikTok share links are resolved server-side). */
export const isSupportedVideoLink = (raw: string): boolean => {
    if (videoEmbed(raw)) return true;
    const url = toUrl(raw);
    if (!url) return false;
    const host = url.hostname.toLowerCase();
    return host === 'vm.tiktok.com' || host === 'vt.tiktok.com'
        || ((host === 'tiktok.com' || host === 'www.tiktok.com') && url.pathname.startsWith('/t/'));
};
