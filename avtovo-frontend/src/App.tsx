import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ChatProvider } from './context/ChatContext';
import Navbar from './components/Navbar';
import RouteErrorBoundary from './components/RouteErrorBoundary';
import Home from './pages/Home';
import AuthCallback from './pages/AuthCallback';
import AddListing from './pages/AddListing';
import EditListing from './pages/EditListing';
import ConfirmDealerUpgrade from './pages/ConfirmDealerUpgrade';
import CarDetail from './pages/CarDetail';
import MyListings from './pages/MyListings';
import Messages from './pages/Messages';
import Profile from './pages/Profile';
import AuctionActions from './pages/AuctionActions';
import AuctionLotDetail from './pages/AuctionLotDetail';
import ApibaraVehiclePage from './pages/ApibaraVehiclePage';
import PaymentReturn from './pages/PaymentReturn';
import ImportOrderList from './pages/importOrders/ImportOrderList';
import ImportOrderCreate from './pages/importOrders/ImportOrderCreate';
import ImportOrderDetail from './pages/importOrders/ImportOrderDetail';
import ClientOrderPage from './pages/ClientOrderPage';
import DealerPage from './pages/dealer/DealerPage';
import DealerPageEditor from './pages/dealer/DealerPageEditor';

// Own chunk: admins only, and the main bundle is already large
const AdminApp = lazy(() => import('./admin/AdminApp'));

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

/** Dealer cabinet: DEALER or ADMIN; anyone else goes to the profile, which offers the dealer upgrade. */
function DealerRoute({ children }: { children: React.ReactNode }) {
    const { hasRole } = useAuth();
    return (
        <ProtectedRoute>
            {hasRole('DEALER') || hasRole('ADMIN') ? children : <Navigate to="/moj-profil" replace />}
        </ProtectedRoute>
    );
}

/** Admin panel: ADMIN only (role from the access token); anyone else goes to the home page. */
function AdminRoute({ children }: { children: React.ReactNode }) {
    const { hasRole } = useAuth();
    return (
        <ProtectedRoute>
            {hasRole('ADMIN') ? children : <Navigate to="/" replace />}
        </ProtectedRoute>
    );
}

const spinner = (
    <div className="min-h-[50vh] flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin" />
    </div>
);

function AppRoutes() {
    return (
        <div className="min-h-screen bg-avtovo-bg">
            <Navbar />
            <RouteErrorBoundary>
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
                    <Route path="/moj-profil" element={
                        <ProtectedRoute><Profile /></ProtectedRoute>
                    } />
                    <Route path="/messages" element={
                        <ProtectedRoute><Messages /></ProtectedRoute>
                    } />
                    <Route path="/messages/:conversationId" element={
                        <ProtectedRoute><Messages /></ProtectedRoute>
                    } />
                    {/* Not protected: the emailed link may be opened in a browser that isn't logged in */}
                    <Route path="/konto-dealera/potwierdz" element={<ConfirmDealerUpgrade />} />
                    <Route path="/panel-dealera/zlecenia" element={
                        <DealerRoute><ImportOrderList /></DealerRoute>
                    } />
                    <Route path="/panel-dealera/zlecenia/nowe" element={
                        <DealerRoute><ImportOrderCreate /></DealerRoute>
                    } />
                    <Route path="/panel-dealera/zlecenia/:id" element={
                        <DealerRoute><ImportOrderDetail /></DealerRoute>
                    } />
                    <Route path="/panel-dealera/strona" element={
                        <DealerRoute><DealerPageEditor /></DealerRoute>
                    } />
                    {/* Public dealer pages, like otomoto's: O nas / Oferta / Kontakt */}
                    <Route path="/dealer/:slug" element={<DealerPage />} />
                    <Route path="/dealer/:slug/oferta" element={<DealerPage tab="offers" />} />
                    <Route path="/dealer/:slug/kontakt" element={<DealerPage tab="contact" />} />
                    {/* Public: the client opens the dealer's link with a password, no account needed */}
                    <Route path="/zlecenie/:code" element={<ClientOrderPage />} />
                    <Route path="/admin/*" element={
                        <AdminRoute><Suspense fallback={spinner}><AdminApp /></Suspense></AdminRoute>
                    } />
                    <Route path="/platnosc/:id" element={
                        <ProtectedRoute><PaymentReturn /></ProtectedRoute>
                    } />
                </Routes>
            </RouteErrorBoundary>
        </div>
    );
}

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <ChatProvider>
                    <AppRoutes />
                </ChatProvider>
            </AuthProvider>
        </BrowserRouter>
    );
}