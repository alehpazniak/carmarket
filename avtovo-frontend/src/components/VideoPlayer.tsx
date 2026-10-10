import {videoEmbed} from '../utils/video';

interface Props {
    url: string;
    title: string;
}

/** Embedded YouTube, TikTok or Instagram player; renders a plain link if the URL can't be embedded. */
export default function VideoPlayer({url, title}: Props) {
    const embed = videoEmbed(url);
    if (!embed) {
        return (
            <a href={url} target="_blank" rel="noopener noreferrer" className="text-avtovo-accent underline break-all">
                {url}
            </a>
        );
    }
    // Instagram's embed adds a header and caption bar around the video, so it needs extra height
    const box = embed.provider === 'instagram'
        ? 'mx-auto w-full max-w-[400px] h-[640px]'
        : embed.vertical ? 'mx-auto w-full max-w-[325px] aspect-[9/16]' : 'w-full aspect-video';
    return (
        <div className={box}>
            <iframe
                src={embed.embedUrl}
                title={title}
                className="w-full h-full rounded-lg bg-black"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                allowFullScreen
                loading="lazy"
                referrerPolicy="strict-origin-when-cross-origin"
            />
        </div>
    );
}
