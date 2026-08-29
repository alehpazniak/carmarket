import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Car, Plus, User, LogOut, ChevronDown, Gavel } from 'lucide-react';
import { useState } from 'react';

export default function Navbar() {
    const { user, isAuthenticated, loginWithGoogle, logout } = useAuth();
    const [menuOpen, setMenuOpen] = useState(false);
    const navigate = useNavigate();

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
                    <div className="flex items-center gap-3">
                        <Link
                            to="/aukcje"
                            className="hidden sm:flex items-center gap-2 text-avtovo-text-secondary hover:text-avtovo-text px-3 py-2 rounded-lg text-sm font-medium transition-colors"
                        >
                            <Gavel size={16} />
                            Aukcje
                        </Link>
                        {isAuthenticated ? (
                            <>
                                <Link
                                    to="/dodaj-ogloszenie"
                                    className="flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
                                >
                                    <Plus size={16} />
                                    Dodaj ogłoszenie
                                </Link>
                                <Link to="/messages">Messages</Link>

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