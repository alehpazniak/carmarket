import { Link, useMatch, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Car, Plus, User, LogOut, ChevronDown, Gavel, MessageSquare } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useChat } from '../hooks/useChat';
import { getUnreadCount, UNREAD_CHANGED_EVENT } from '../api/chat';

export default function Navbar() {
    const { user, isAuthenticated, loginWithGoogle, logout } = useAuth();
    const [menuOpen, setMenuOpen] = useState(false);
    const navigate = useNavigate();
    const listingMatch = useMatch('/ogloszenia/:id');
    const messagesPath = listingMatch?.params.id ? `/messages?car=${listingMatch.params.id}` : '/messages';
    const [unread, setUnread] = useState(0);
    const { onMessage } = useChat();

    useEffect(() => {
        if (!isAuthenticated) {
            setUnread(0);
            return;
        }
        const refresh = () => getUnreadCount().then(setUnread).catch(() => {});
        refresh();
        const timer = setInterval(refresh, 30000);
        window.addEventListener(UNREAD_CHANGED_EVENT, refresh);
        return () => {
            clearInterval(timer);
            window.removeEventListener(UNREAD_CHANGED_EVENT, refresh);
        };
    }, [isAuthenticated]);

    // Realtime: bump the badge as soon as someone else's message arrives
    useEffect(() => {
        onMessage((msg) => {
            if (msg.senderId !== user?.id) {
                getUnreadCount().then(setUnread).catch(() => {});
            }
        });
    }, [onMessage, user?.id]);

    const handleLogout = async () => {
        await logout();
        navigate('/');
        setMenuOpen(false);
    };

    return (
        <nav className="sticky top-0 z-50 border-b border-avtovo-border bg-avtovo-bg/95 backdrop-blur">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center justify-between h-16">
                    {/* Logo */}
                    <Link to="/" className="flex items-center gap-2 group">
                        <div className="w-8 h-8 bg-avtovo-accent rounded-lg flex items-center justify-center">
                            <Car size={18} className="text-white" />
                        </div>
                        <span className="text-xl font-bold text-avtovo-text tracking-tight">
              avtovo
            </span>
                    </Link>

                    {/* Right side */}
                    <div className="flex items-center gap-1 sm:gap-3">
                        <Link
                            to="/aukcje"
                            aria-label="Aukcje"
                            className="flex items-center gap-2 text-avtovo-text-secondary hover:text-avtovo-text px-2 sm:px-3 py-2 rounded-lg text-sm font-medium transition-colors"
                        >
                            <Gavel size={16} />
                            <span className="hidden sm:inline">Aukcje</span>
                        </Link>
                        {isAuthenticated ? (
                            <>
                                <Link
                                    to="/dodaj-ogloszenie"
                                    aria-label="Dodaj ogłoszenie"
                                    className="flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-3 sm:px-4 py-2 rounded-lg text-sm font-medium transition-colors whitespace-nowrap"
                                >
                                    <Plus size={16} />
                                    <span className="hidden sm:inline">Dodaj ogłoszenie</span>
                                </Link>
                                <Link
                                    to={messagesPath}
                                    aria-label="Messages"
                                    className="relative flex items-center gap-2 text-avtovo-text-secondary hover:text-avtovo-text px-2 sm:px-3 py-2 rounded-lg text-sm font-medium transition-colors"
                                >
                                    <MessageSquare size={16} />
                                    <span className="hidden sm:inline">Messages</span>
                                    {unread > 0 && (
                                        <span className="min-w-5 h-5 px-1 flex items-center justify-center rounded-full bg-red-500 text-white text-xs font-semibold">
                                            {unread > 99 ? '99+' : unread}
                                        </span>
                                    )}
                                </Link>

                                {/* User menu */}
                                <div className="relative">
                                    <button
                                        onClick={() => setMenuOpen(!menuOpen)}
                                        className="flex items-center gap-2 bg-avtovo-card border border-avtovo-border hover:border-gray-600 px-3 py-2 rounded-lg transition-colors"
                                    >
                                        {user?.picture ? (
                                            <img src={user.picture} alt={user.name} className="w-6 h-6 rounded-full" />
                                        ) : (
                                            <User size={16} className="text-avtovo-text-secondary" />
                                        )}
                                        <span className="text-sm text-avtovo-text hidden sm:block">
                      {user?.name?.split(' ')[0]}
                    </span>
                                        <ChevronDown size={14} className="text-avtovo-text-secondary" />
                                    </button>

                                    {menuOpen && (
                                        <div className="absolute right-0 mt-2 w-48 bg-avtovo-card border border-avtovo-border rounded-xl shadow-xl overflow-hidden">
                                            <Link
                                                to="/moje-ogloszenia"
                                                onClick={() => setMenuOpen(false)}
                                                className="flex items-center gap-2 px-4 py-3 text-sm text-avtovo-text hover:bg-white/5 transition-colors"
                                            >
                                                <Car size={15} />
                                                Moje ogłoszenia
                                            </Link>
                                            <Link
                                                to="/moj-profil"
                                                onClick={() => setMenuOpen(false)}
                                                className="flex items-center gap-2 px-4 py-3 text-sm text-avtovo-text hover:bg-white/5 transition-colors"
                                            >
                                                <User size={15} />
                                                Mój profil
                                            </Link>
                                            <button
                                                onClick={handleLogout}
                                                className="w-full flex items-center gap-2 px-4 py-3 text-sm text-red-400 hover:bg-white/5 transition-colors"
                                            >
                                                <LogOut size={15} />
                                                Wyloguj się
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </>
                        ) : (
                            <button
                                onClick={loginWithGoogle}
                                className="flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium transition-colors"
                            >
                                Zaloguj się
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </nav>
    );
}