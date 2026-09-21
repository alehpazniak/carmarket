import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Navbar from './components/Navbar';
import Home from './pages/Home';
import AuthCallback from './pages/AuthCallback';
import AddListing from './pages/AddListing';
import EditListing from './pages/EditListing';
import CarDetail from './pages/CarDetail';
import MyListings from './pages/MyListings';
import Messages from './pages/Messages';
import AuctionActions from './pages/AuctionActions';
import AuctionLotDetail from './pages/AuctionLotDetail';
import ApibaraVehiclePage from './pages/ApibaraVehiclePage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
    const { isAuthenticated, isLoading } = useAuth();
    if (isLoading) return (
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
            <div className="w-8 h-8 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin" />
        </div>
    );
    if (!isAuthenticated) return <Navigate to="/" replace />;
    return <>{children}</>;
}

function AppRoutes() {
    return (
        <div className="min-h-screen bg-avtovo-bg">
            <Navbar />
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/auth/callback" element={<AuthCallback />} />
                <Route path="/ogloszenia/:id" element={<CarDetail />} />
                <Route path="/aukcje" element={<AuctionActions />} />
                <Route path="/aukcje/lot/:id" element={<AuctionLotDetail />} />
                <Route path="/aukcje/pojazd/:identifier" element={<ApibaraVehiclePage />} />
                <Route path="/dodaj-ogloszenie" element={
                    <ProtectedRoute><AddListing /></ProtectedRoute>
                } />
                <Route path="/ogloszenia/:id/edytuj" element={
                    <ProtectedRoute><EditListing /></ProtectedRoute>
                } />
                <Route path="/moje-ogloszenia" element={
                    <ProtectedRoute><MyListings /></ProtectedRoute>
                } />
                <Route path="/messages" element={<Messages />} />
            </Routes>
        </div>
    );
}

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <AppRoutes />
            </AuthProvider>
        </BrowserRouter>
    );
}