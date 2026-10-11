import { useCallback, useEffect, useId, useImperativeHandle, useRef, useState, type Ref } from 'react';
import { ChevronDown, MessageCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useChat } from '../context/ChatContext';
import { getConversations, UNREAD_CHANGED_EVENT } from '../api/chat';
import { getUserProfile } from '../api/users';
import { ChatWindow } from './ChatWindow';
import type { Conversation } from '../types/chat';

export interface ListingChatSectionHandle {
    /** Expands the section, scrolls it into view and focuses the message input ("Napisz"). */
    openAndFocus: () => void;
}

interface ListingChatSectionProps {
    carId: string;
    sellerId: string;
    ref?: Ref<ListingChatSectionHandle>;
}

function UnreadBadge({ count }: { count: number }) {
    if (count <= 0) return null;
    return (
        <span className="min-w-5 h-5 px-1 flex items-center justify-center rounded-full bg-red-500 text-white text-xs font-semibold">
            {count > 99 ? '99+' : count}
        </span>
    );
}

/**
 * Collapsible chat at the bottom of a listing. The buyer gets their single thread with the seller;
 * the owner gets every buyer's thread for this listing, each collapsible on its own. ChatWindow is
 * only mounted while expanded, so a hidden chat never marks messages as read.
 */
export function ListingChatSection({ carId, sellerId, ref }: ListingChatSectionProps) {
    const { user, isAuthenticated, loginWithGoogle } = useAuth();
    const { subscribe } = useChat();
    const isOwner = user?.id === sellerId;
    const panelId = useId();
    const sectionRef = useRef<HTMLElement>(null);

    const [expanded, setExpanded] = useState(false);
    const [focusSignal, setFocusSignal] = useState(0);
    // null until the first load, so auto-expanding happens once, on load
    const [conversations, setConversations] = useState<Conversation[] | null>(null);
    const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());
    const [buyerNames, setBuyerNames] = useState<Record<string, string>>({});
    const autoExpanded = useRef(false);

    const userId = user?.id;
    const refresh = useCallback(() => {
        if (!userId) return;
        getConversations()
            .then((all) => {
                const mine = all.filter((c) => c.carId === carId
                    && (isOwner ? c.sellerId === userId : c.buyerId === userId));
                setConversations(mine);
                if (!autoExpanded.current) {
                    autoExpanded.current = true;
                    const unreadRows = mine.filter((c) => c.unreadCount > 0);
                    if (unreadRows.length > 0) {
                        setExpanded(true);
                        setExpandedRows(new Set(unreadRows.map((c) => c.id)));
                    }
                }
            })
            .catch((err) => console.error('Failed to load listing conversations:', err));
    }, [carId, isOwner, userId]);

    // Initial load + keep badges live (mark-as-read anywhere fires UNREAD_CHANGED_EVENT)
    useEffect(() => {
        refresh();
        window.addEventListener(UNREAD_CHANGED_EVENT, refresh);
        return () => window.removeEventListener(UNREAD_CHANGED_EVENT, refresh);
    }, [refresh]);

    // Realtime: any message for this listing may be a new buyer's thread or change the unread count
    useEffect(() => subscribe((msg) => {
        if (msg.carId === carId) refresh();
    }), [subscribe, carId, refresh]);

    // Buyer display names for the owner's rows
    useEffect(() => {
        if (!isOwner || !conversations) return;
        conversations.forEach((c) => {
            if (c.buyerId in buyerNames) return;
            setBuyerNames((prev) => ({ ...prev, [c.buyerId]: '' }));
            getUserProfile(c.buyerId)
                .then((p) => setBuyerNames((prev) => ({ ...prev, [c.buyerId]: p.displayName?.trim() ?? '' })))
                .catch(() => {});
        });
    }, [isOwner, conversations, buyerNames]);

    useImperativeHandle(ref, () => ({
        openAndFocus: () => {
            setExpanded(true);
            setFocusSignal((n) => n + 1);
            sectionRef.current?.scrollIntoView({ behavior: 'smooth' });
        },
    }), []);

    const toggleRow = (id: string) => setExpandedRows((prev) => {
        const next = new Set(prev);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
    });

    const list = isAuthenticated ? conversations ?? [] : [];
    const unread = list.reduce((sum, c) => sum + c.unreadCount, 0);
    const title = isOwner ? `Wiadomości od kupujących (${list.length})` : 'Rozmowa ze sprzedającym';

    let body;
    if (!isAuthenticated) {
        body = (
            <button className="contact-btn" onClick={loginWithGoogle}>
                <MessageCircle size={18} /> Zaloguj się, aby napisać
            </button>
        );
    } else if (!isOwner) {
        body = <ChatWindow carId={carId} sellerId={sellerId} focusSignal={focusSignal} />;
    } else if (conversations && list.length === 0) {
        body = <p className="text-avtovo-text-secondary text-sm">Brak wiadomości do tego ogłoszenia</p>;
    } else {
        body = (
            <ul className="space-y-2">
                {list.map((c, i) => {
                    const open = expandedRows.has(c.id);
                    const rowPanelId = `${panelId}-${c.id}`;
                    return (
                        <li key={c.id} className="border border-avtovo-border rounded-lg overflow-hidden">
                            <button
                                type="button"
                                onClick={() => toggleRow(c.id)}
                                aria-expanded={open}
                                aria-controls={rowPanelId}
                                className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-white/5 transition-colors"
                            >
                                <span className={`flex-1 min-w-0 truncate text-avtovo-text ${c.unreadCount > 0 ? 'font-semibold' : ''}`}>
                                    {buyerNames[c.buyerId] || `Kupujący ${i + 1}`}
                                </span>
                                <UnreadBadge count={c.unreadCount} />
                                {c.lastMessageAt && (
                                    <span className="text-xs text-avtovo-text-secondary whitespace-nowrap">
                                        {new Date(c.lastMessageAt).toLocaleString('pl-PL', {
                                            day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
                                        })}
                                    </span>
                                )}
                                <ChevronDown size={16} className={`text-avtovo-text-secondary transition-transform ${open ? 'rotate-180' : ''}`} />
                            </button>
                            <div id={rowPanelId} hidden={!open} className="px-4 pb-4">
                                {open && <ChatWindow carId={carId} sellerId={sellerId} conversationId={c.id} />}
                            </div>
                        </li>
                    );
                })}
            </ul>
        );
    }

    return (
        <section ref={sectionRef} className="bg-avtovo-card border border-avtovo-border rounded-xl mt-6 scroll-mt-20">
            <button
                type="button"
                onClick={() => setExpanded((v) => !v)}
                aria-expanded={expanded}
                aria-controls={panelId}
                className="w-full flex items-center gap-3 p-6 text-left"
            >
                <MessageCircle size={18} className="text-avtovo-accent" />
                <h2 className="flex-1 text-avtovo-text font-semibold">{title}</h2>
                <UnreadBadge count={unread} />
                <ChevronDown size={18} className={`text-avtovo-text-secondary transition-transform ${expanded ? 'rotate-180' : ''}`} />
            </button>
            <div id={panelId} hidden={!expanded} className="px-6 pb-6">
                {expanded && body}
            </div>
        </section>
    );
}
