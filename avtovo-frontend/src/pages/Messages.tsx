import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Car, RotateCw } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { getConversations, UNREAD_CHANGED_EVENT } from '../api/chat';
import { getCarSummaries, MAX_SUMMARY_IDS } from '../api/cars';
import { ChatWindow } from '../components/ChatWindow';
import type { Conversation } from '../types/chat';
import type { CarSummary } from '../types';

/**
 * What we know about a conversation's listing. No entry yet = loading. 'missing' = car-service
 * doesn't know the id at all; REMOVED listings are still 'loaded' (with their status).
 * 'error' entries are retried on the next poll instead of being cached.
 */
type CarEntry =
    | { state: 'loading' }
    | { state: 'loaded'; car: CarSummary }
    | { state: 'missing' }
    | { state: 'error' };

const linkStyle: CSSProperties = { textDecoration: 'underline', color: '#3b82f6' };
const mutedStyle: CSSProperties = { color: '#a1a1aa', fontSize: 13 };

function carTitle(car: CarSummary) {
    return `${car.make} ${car.model} ${car.year}`;
}

function StatusBadge({ car }: { car: CarSummary }) {
    if (car.status !== 'SOLD' && car.status !== 'REMOVED') return null;
    const sold = car.status === 'SOLD';
    return (
        <span style={{ marginLeft: 8, padding: '1px 8px', borderRadius: 10, fontSize: 12, fontWeight: 600, verticalAlign: 'middle', background: sold ? '#1e3a8a' : '#3f3f46', color: sold ? '#bfdbfe' : '#e4e4e7' }}>
            {sold ? 'Sprzedane' : 'Usunięte'}
        </span>
    );
}

function Skeleton({ width }: { width: number }) {
    return <span className="animate-pulse" style={{ display: 'inline-block', width, height: 16, borderRadius: 4, background: '#2a2a2a', verticalAlign: 'middle' }} />;
}

function RetryButton({ onRetry }: { onRetry: () => void }) {
    return (
        <button
            type="button"
            onClick={(e) => {
                e.stopPropagation(); // don't open the conversation card
                onRetry();
            }}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginLeft: 8, padding: '2px 8px', fontSize: 12, background: 'transparent', color: '#3b82f6', border: '1px solid #2a2a2a', borderRadius: 6, cursor: 'pointer' }}
        >
            <RotateCw size={12} /> Spróbuj ponownie
        </button>
    );
}

export default function Messages() {
    const { user } = useAuth();
    // The open conversation lives in the URL (/messages/:conversationId), so the navbar link
    // and Back always return to the list. A legacy ?car= filter is ignored.
    const { conversationId: activeId } = useParams<{ conversationId: string }>();
    const navigate = useNavigate();
    const [conversations, setConversations] = useState<Conversation[]>([]);
    const [loading, setLoading] = useState(true);
    // carsRef is the source of truth read by the async loader; `cars` mirrors it for rendering
    const carsRef = useRef<Record<string, CarEntry>>({});
    const [cars, setCars] = useState<Record<string, CarEntry>>({});

    const updateCars = useCallback((patch: Record<string, CarEntry>) => {
        carsRef.current = { ...carsRef.current, ...patch };
        setCars(carsRef.current);
    }, []);

    /** One batch request for listings not loaded yet (or that failed last time). */
    const loadCars = useCallback((list: Conversation[]) => {
        const ids = [...new Set(list.map((c) => c.carId))]
            .filter((id) => !carsRef.current[id] || carsRef.current[id].state === 'error');
        for (let i = 0; i < ids.length; i += MAX_SUMMARY_IDS) {
            const batch = ids.slice(i, i + MAX_SUMMARY_IDS);
            updateCars(Object.fromEntries(batch.map((id) => [id, { state: 'loading' } as CarEntry])));
            getCarSummaries(batch)
                .then((found) => {
                    const byId = new Map(found.map((car) => [car.id, car]));
                    updateCars(Object.fromEntries(batch.map((id) => {
                        const car = byId.get(id);
                        return [id, car ? { state: 'loaded', car } : { state: 'missing' }] as [string, CarEntry];
                    })));
                })
                .catch((err) => {
                    console.error('Failed to load listing summaries:', err);
                    updateCars(Object.fromEntries(batch.map((id) => [id, { state: 'error' } as CarEntry])));
                });
        }
    }, [updateCars]);

    const load = useCallback(() => {
        return getConversations()
            .then((list) => {
                setConversations(list);
                loadCars(list);
            })
            .catch((err) => console.error('Failed to load conversations:', err));
    }, [loadCars]);

    useEffect(() => {
        load().finally(() => setLoading(false));
        const timer = setInterval(load, 10000);
        window.addEventListener(UNREAD_CHANGED_EVENT, load);
        return () => {
            clearInterval(timer);
            window.removeEventListener(UNREAD_CHANGED_EVENT, load);
        };
    }, [load]);

    const retry = () => {
        load();
    };

    if (loading) return <div className="messages-page">Ładowanie…</div>;

    const active = conversations.find((c) => c.id === activeId) ?? null;

    if (activeId && !active) {
        return (
            <div className="messages-page" style={{ flexDirection: 'column' }}>
                <p>Nie znaleziono rozmowy.</p>
                <Link to="/messages" style={linkStyle}>
                    Wróć do wiadomości
                </Link>
            </div>
        );
    }

    if (active) {
        const entry = cars[active.carId];
        const removed = entry?.state === 'missing' || (entry?.state === 'loaded' && entry.car.status === 'REMOVED');
        return (
            <div className="messages-page" style={{ flexDirection: 'column' }}>
                <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
                    <button
                        onClick={() => navigate('/messages')}
                        style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'transparent', border: 'none', color: '#a1a1aa', cursor: 'pointer' }}
                    >
                        <ArrowLeft size={16} /> Wszystkie wiadomości
                    </button>
                    {(!entry || entry.state === 'loading') && <Skeleton width={180} />}
                    {entry?.state === 'loaded' && (
                        <span>
                            <strong>{carTitle(entry.car)}</strong>
                            <span style={{ ...mutedStyle, marginLeft: 8 }}>{entry.car.price.toLocaleString('pl-PL')} zł</span>
                            <StatusBadge car={entry.car} />
                        </span>
                    )}
                    {entry?.state === 'error' && (
                        <span style={mutedStyle}>
                            Nie udało się wczytać ogłoszenia
                            <RetryButton onRetry={retry} />
                        </span>
                    )}
                    {removed ? (
                        <span style={mutedStyle}>Ogłoszenie zostało usunięte</span>
                    ) : entry && entry.state !== 'loading' && (
                        <Link to={`/ogloszenia/${active.carId}`} style={linkStyle}>
                            Zobacz ogłoszenie
                        </Link>
                    )}
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
            <h2>Wiadomości</h2>
            {conversations.length === 0 && <p>Brak rozmów.</p>}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 16 }}>
                {conversations.map((c) => {
                    const entry = cars[c.carId];
                    const car = entry?.state === 'loaded' ? entry.car : null;
                    const photo = car && car.status !== 'REMOVED' ? car.primaryImageUrl : null;
                    const role = c.buyerId === user?.id ? 'Sprzedający' : 'Kupujący';
                    return (
                        <div
                            key={c.id}
                            role="button"
                            tabIndex={0}
                            onClick={() => navigate(`/messages/${c.id}`)}
                            onKeyDown={(e) => e.key === 'Enter' && navigate(`/messages/${c.id}`)}
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
                                    {car && <>{carTitle(car)}<StatusBadge car={car} /></>}
                                    {(!entry || entry.state === 'loading') && <Skeleton width={160} />}
                                    {entry?.state === 'missing' && 'Ogłoszenie zostało usunięte'}
                                    {entry?.state === 'error' && (
                                        <span style={{ ...mutedStyle, fontWeight: 400 }}>
                                            Nie udało się wczytać ogłoszenia
                                            <RetryButton onRetry={retry} />
                                        </span>
                                    )}
                                </div>
                                <div style={{ ...mutedStyle, marginTop: 4 }}>
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
