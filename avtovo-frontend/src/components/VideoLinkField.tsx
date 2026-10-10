import {useState} from 'react';
import {Loader2, Trash2} from 'lucide-react';
import {isSupportedVideoLink} from '../utils/video';
import VideoPlayer from './VideoPlayer';

interface Props {
    /** The attached link, if any. */
    videoUrl?: string | null;
    /** Attach a link; reject with a message to show it under the input. */
    onAdd: (url: string) => Promise<void>;
    onRemove: () => Promise<void>;
}

/** "Film" section of the listing form: one YouTube, TikTok or Instagram link, shown as an embedded player. */
export default function VideoLinkField({videoUrl, onAdd, onRemove}: Props) {
    const [link, setLink] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const run = async (action: () => Promise<void>) => {
        setBusy(true);
        setError(null);
        try {
            await action();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Nie udało się zapisać filmu');
        } finally {
            setBusy(false);
        }
    };

    const handleAdd = () => {
        if (!isSupportedVideoLink(link)) {
            setError('Wklej link do filmu z YouTube, TikToka lub Instagrama');
            return;
        }
        run(async () => {
            await onAdd(link.trim());
            setLink('');
        });
    };

    return (
        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
            <h2 className="text-avtovo-text font-semibold mb-1">Film</h2>
            <p className="text-xs text-avtovo-muted mb-4">Dodaj link do filmu z YouTube, TikToka lub Instagrama (Reels).</p>

            {videoUrl ? (
                <div className="space-y-3">
                    <VideoPlayer url={videoUrl} title="Film z ogłoszenia"/>
                    <button type="button" onClick={() => run(onRemove)} disabled={busy}
                            className="flex items-center gap-2 text-sm text-red-400 hover:text-red-300 disabled:opacity-50">
                        {busy ? <Loader2 size={14} className="animate-spin"/> : <Trash2 size={14}/>}
                        Usuń film
                    </button>
                </div>
            ) : (
                <div className="flex gap-2">
                    <input value={link} onChange={e => setLink(e.target.value)}
                           onKeyDown={e => {
                               if (e.key === 'Enter') {
                                   e.preventDefault();
                                   handleAdd();
                               }
                           }}
                           placeholder="Link z YouTube, TikToka lub Instagrama"
                           className="flex-1 min-w-0 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
                    <button type="button" onClick={handleAdd} disabled={busy || !link.trim()}
                            className="bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-50 text-white px-4 rounded-lg font-medium flex items-center gap-2">
                        {busy && <Loader2 size={14} className="animate-spin"/>}
                        Dodaj
                    </button>
                </div>
            )}
            {error && <p className="text-sm text-red-400 mt-2">{error}</p>}
        </div>
    );
}
