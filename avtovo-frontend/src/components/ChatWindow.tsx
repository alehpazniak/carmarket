import {useEffect, useRef, useState} from 'react';
import {Send} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {useChat} from '../context/ChatContext';
import {getConversations, getMessages, markConversationRead} from '../api/chat';
import type {Message} from '../types/chat';

interface ChatWindowProps {
    carId: string;
    sellerId: string;
    conversationId?: string; // if opening an existing thread from the conversations list
    /** Bump to focus the input (e.g. the listing's "Napisz" button); 0 = don't. */
    focusSignal?: number;
}

export function ChatWindow({carId, sellerId, conversationId: initialConversationId, focusSignal = 0}: ChatWindowProps) {
    const {user} = useAuth();
    const {connected, subscribe, sendMessage} = useChat();
    const [messages, setMessages] = useState<Message[]>([]);
    const [draft, setDraft] = useState('');
    const listRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const [conversationId, setConversationId] = useState<string | undefined>(initialConversationId);

    // Opened from a listing page: find an existing thread for this car so history shows
    useEffect(() => {
        if (conversationId || !user) return;
        getConversations()
            .then((list) => {
                const found = list.find((c) => c.carId === carId && c.buyerId === user.id);
                if (found) setConversationId(found.id);
            })
            .catch((err) => console.error('Failed to look up conversation:', err));
    }, [conversationId, carId, user]);

    // Load history if we already have a conversation
    useEffect(() => {
        if (!conversationId) return;
        getMessages(conversationId)
            .then((page) => {
                setMessages(page.content);
                if (page.content.some((m) => m.senderId !== user?.id && !m.readAt)) {
                    markConversationRead(conversationId).catch(() => {});
                }
            })
            .catch((err) => console.error('Failed to load history:', err));
    }, [conversationId, user?.id]);

    // Subscribe to incoming realtime messages
    useEffect(() => {
        return subscribe((msg) => {
            if (conversationId && msg.conversationId !== conversationId) return;
            if (!conversationId) {
                // No thread yet: only adopt this listing's thread with this seller — either the
                // confirmation of my first message or the seller writing. Ignore other threads.
                const fromThisThread = msg.senderId === user?.id || msg.senderId === sellerId;
                if (msg.carId !== carId || !fromThisThread) return;
                setConversationId(msg.conversationId); // thread just created
            }
            if (conversationId && msg.senderId !== user?.id) {
                markConversationRead(conversationId).catch(() => {});
            }
            // Only append messages for this conversation (or the first one being created)
            setMessages((prev) => {
                if (prev.some((m) => m.id === msg.id)) return prev; // dedupe
                return [...prev, msg];
            });
        });
    }, [subscribe, conversationId, carId, sellerId, user?.id]);

    // Auto-scroll to newest — scroll the list itself, not the page, since the chat may sit inside a long page
    useEffect(() => {
        const list = listRef.current;
        if (list) list.scrollTo({top: list.scrollHeight, behavior: 'smooth'});
    }, [messages]);

    // The input is disabled until connected, so focus once both the request and the connection are there
    useEffect(() => {
        // preventScroll: let the section's smooth scrollIntoView finish instead of jumping
        if (focusSignal > 0 && connected) inputRef.current?.focus({preventScroll: true});
    }, [focusSignal, connected]);

    const handleSend = () => {
        const content = draft.trim();
        if (!content) return;
        sendMessage({carId, sellerId, conversationId, content});
        setDraft('');
    };

    return (
        <div className="chat-window">
            <div className="chat-status">
                {connected ? (
                    <span className="chat-online">● Online</span>
                ) : (
                    <span className="chat-offline">● Connecting…</span>
                )}
            </div>

            <div className="chat-messages" ref={listRef}>
                {messages.length === 0 && (
                    <p className="chat-empty">No messages yet. Say hello!</p>
                )}
                {messages.map((msg) => (
                    <div
                        key={msg.id}
                        className={
                            msg.senderId === user?.id
                                ? 'chat-msg chat-msg-mine'
                                : 'chat-msg chat-msg-theirs'
                        }
                    >
                        <div className="chat-msg-content">{msg.content}</div>
                        <div className="chat-msg-time">
                            {new Date(msg.createdAt).toLocaleTimeString([], {
                                hour: '2-digit',
                                minute: '2-digit',
                            })}
                        </div>
                    </div>
                ))}
            </div>

            <div className="chat-input">
                <input
                    ref={inputRef}
                    type="text"
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                    placeholder="Type a message…"
                    disabled={!connected}
                />
                <button onClick={handleSend} disabled={!connected || !draft.trim()}>
                    <Send size={18}/>
                </button>
            </div>
        </div>
    );
}