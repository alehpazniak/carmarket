import {useEffect, useRef, useState} from 'react';
import {Send} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {useChat} from '../hooks/useChat';
import {getConversations, getMessages, markConversationRead} from '../api/chat';
import type {Message} from '../types/chat';

interface ChatWindowProps {
    carId: string;
    sellerId: string;
    conversationId?: string; // if opening an existing thread from the conversations list
}

export function ChatWindow({carId, sellerId, conversationId: initialConversationId}: ChatWindowProps) {
    const {user} = useAuth();
    const {connected, onMessage, sendMessage} = useChat();
    const [messages, setMessages] = useState<Message[]>([]);
    const [draft, setDraft] = useState('');
    const bottomRef = useRef<HTMLDivElement>(null);
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
        onMessage((msg) => {
            if (conversationId && msg.conversationId !== conversationId) return;
            if (!conversationId) setConversationId(msg.conversationId); // thread just created
            if (conversationId && msg.senderId !== user?.id) {
                markConversationRead(conversationId).catch(() => {});
            }
            // Only append messages for this conversation (or the first one being created)
            setMessages((prev) => {
                if (prev.some((m) => m.id === msg.id)) return prev; // dedupe
                return [...prev, msg];
            });
        });
    }, [onMessage, conversationId, user?.id]);

    // Auto-scroll to newest
    useEffect(() => {
        bottomRef.current?.scrollIntoView({behavior: 'smooth'});
    }, [messages]);

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

            <div className="chat-messages">
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
                <div ref={bottomRef}/>
            </div>

            <div className="chat-input">
                <input
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