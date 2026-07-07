import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { getConversations } from '../api/chat';
import { ChatWindow } from '../components/ChatWindow';
import type { Conversation } from '../types/chat';

export default function Messages() {
    const { user } = useAuth();
    const [conversations, setConversations] = useState<Conversation[]>([]);
    const [active, setActive] = useState<Conversation | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        getConversations()
            .then((data) => {
                setConversations(data);
                if (data.length > 0) setActive(data[0]);
            })
            .catch((err) => console.error('Failed to load conversations:', err))
            .finally(() => setLoading(false));
    }, []);

    if (loading) return <div className="messages-page">Loading…</div>;

    return (
        <div className="messages-page">
            <aside className="conversations-list">
                <h2>Messages</h2>
                {conversations.length === 0 && <p>No conversations yet.</p>}
                {conversations.map((c) => {
                    const otherParty =
                        c.buyerId === user?.id ? 'Seller' : 'Buyer';
                    return (
                        <button
                            key={c.id}
                            className={
                                active?.id === c.id
                                    ? 'conversation-item active'
                                    : 'conversation-item'
                            }
                            onClick={() => setActive(c)}
                        >
                            <div className="conversation-title">{otherParty}</div>
                            <div className="conversation-preview">
                                Listing {c.carId.slice(0, 8)}…
                            </div>
                        </button>
                    );
                })}
            </aside>

            <main className="chat-panel">
                {active ? (
                    <ChatWindow
                        key={active.id}
                        carId={active.carId}
                        sellerId={active.sellerId}
                        conversationId={active.id}
                    />
                ) : (
                    <p className="no-chat-selected">Select a conversation</p>
                )}
            </main>
        </div>
    );
}