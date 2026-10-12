import { useState } from 'react';
import { Check, Copy } from 'lucide-react';

export default function CopyButton({ text, label = 'Kopiuj' }: { text: string; label?: string }) {
    const [copied, setCopied] = useState(false);
    const copy = () => {
        navigator.clipboard.writeText(text).then(() => {
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        });
    };
    return (
        <button
            type="button"
            onClick={copy}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg border border-avtovo-border text-avtovo-text-secondary hover:text-avtovo-text hover:border-gray-600 transition-colors shrink-0"
        >
            {copied ? <Check size={14} className="text-emerald-400" /> : <Copy size={14} />}
            {copied ? 'Skopiowano' : label}
        </button>
    );
}
