import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Car } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { getConversations, UNREAD_CHANGED_EVENT } from '../api/chat';
import { getCar } from '../api/cars';
import { ChatWindow } from '../components/ChatWindow';
import type { Conversation } from '../types/chat';
import type { CarListing } from '../types';

export default function Messages() {
    const { user } = useAuth();
    const [searchParams, setSearchParams] = useSearchParams();
    const carFilter = searchParams.get('car');
    const [conversations, setConversations] = useState<Conversation[]>([]);
    const [activeId, setActiveId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [cars, setCars] = useState<Record<string, CarListing | null>>({});

    const load = useCallback(() => {
        return getConversations()
            .then(setConversations)
            .catch((err) => console.error('Failed to load conversations:', err));
    }, []);

    useEffect(() => {
        load().finally(() => setLoading(false));
        const timer = setInterval(load, 10000);
        window.addEventListener(UNREAD_CHANGED_EVENT, load);
        return () => {
            clearInterval(timer);
            window.removeEventListener(UNREAD_CHANGED_EVENT, load);
        };
    }, [load]);

    // Load listing details for each conversation (once per car)
    useEffect(() => {
        conversations.forEach((c) => {
            if (c.carId in cars) return;
            setCars((prev) => ({ ...prev, [c.carId]: null }));
            getCar(c.carId)
                .then((car) => setCars((prev) => ({ ...prev, [c.carId]: car })))
                .catch(() => {});
        });
    }, [conversations, cars]);

    // Coming from a listing page: open its conversation if there is exactly one
    const forCar = carFilter ? conversations.filter((c) => c.carId === carFilter) : conversations;
    useEffect(() => {
        if (carFilter && forCar.length === 1) {
            setActiveId(forCar[0].id);
            setSearchParams({}, { replace: true });
        }
    }, [carFilter, forCar, setSearchParams]);

    if (loading) return <div className="messages-page">Loading…</div>;

    const active = conversations.find((c) => c.id === activeId) ?? null;

    if (active) {
        const car = cars[active.carId];
        return (
            <div className="messages-page" style={{ flexDirection: 'column' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <button
                        onClick={() => {
                            setActiveId(null);
                            load();
                        }}
                        style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'transparent', border: 'none', color: '#a1a1aa', cursor: 'pointer' }}
                    >
                        <ArrowLeft size={16} /> All messages
                    </button>
                    <strong>
                        {car ? `${car.make} ${car.model} ${car.year}` : 'Listing'}
                    </strong>
                    <Link to={`/ogloszenia/${active.carId}`} style={{ textDecoration: 'underline', color: '#3b82f6' }}>
                        View listing
                    </Link>
                </div>
                <main className="chat-panel">
                    <ChatWindow
                        key={active.id}
                        carId={active.carId}
                        sellerId={active.sellerId}
                        conversationId={active.id}
                    />
                </main>
            </div>
        );
    }

    return (
        <div className="messages-page" style={{ flexDirection: 'column' }}>
            <h2>Messages</h2>
            {forCar.length === 0 && <p>No conversations yet.</p>}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 16 }}>
                {forCar.map((c) => {
                    const car = cars[c.carId];
                    const photo = car?.primaryImageUrl ?? car?.imageUrls?.[0];
                    const role = c.buyerId === user?.id ? 'Seller' : 'Buyer';
                    return (
                        <div
                            key={c.id}
                            role="button"
                            tabIndex={0}
                            onClick={() => setActiveId(c.id)}
                            onKeyDown={(e) => e.key === 'Enter' && setActiveId(c.id)}
                            style={{ position: 'relative', cursor: 'pointer', background: '#1a1a1a', border: '1px solid #2a2a2a', borderRadius: 12, overflow: 'hidden' }}
                        >
                            <div style={{ height: 150, background: '#111', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                {photo ? (
                                    <img src={photo} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                ) : (
                                    <Car size={36} color="#52525b" />
                                )}
                            </div>
                            {c.unreadCount > 0 && (
                                <span
                                    style={{ position: 'absolute', top: 10, right: 10, minWidth: 24, height: 24, padding: '0 7px', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: 12, background: '#ef4444', color: '#fff', fontSize: 13, fontWeight: 600 }}
                                >
                                    {c.unreadCount}
                                </span>
                            )}
                            <div style={{ padding: 12 }}>
                                <div style={{ fontWeight: c.unreadCount > 0 ? 700 : 500 }}>
                                    {car ? `${car.make} ${car.model} ${car.year}` : `Listing ${c.carId.slice(0, 8)}…`}
                                </div>
                                <div style={{ color: '#a1a1aa', fontSize: 13, marginTop: 4 }}>
                                    {car ? `${car.price.toLocaleString('pl-PL')} zł · ` : ''}
                                    {role}
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
