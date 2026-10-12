import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { useDebounced } from '../hooks';

interface Props {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
}

/** Text filter that reports changes 300 ms after the user stops typing. */
export default function SearchInput({ value, onChange, placeholder = 'Szukaj…' }: Props) {
    const [text, setText] = useState(value);
    const debounced = useDebounced(text.trim());

    useEffect(() => {
        if (debounced !== value) onChange(debounced);
        // Only the typed text drives this; `value` changing from outside must not re-send it
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [debounced]);

    return (
        <label className="relative flex-1 min-w-[12rem]">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-avtovo-text-secondary" />
            <input
                value={text}
                onChange={e => setText(e.target.value)}
                placeholder={placeholder}
                className="w-full bg-avtovo-card border border-avtovo-border rounded-lg pl-9 pr-3 py-2 text-sm text-avtovo-text placeholder:text-avtovo-muted focus:outline-none focus:border-avtovo-accent"
            />
        </label>
    );
}
