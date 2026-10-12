import { Route, Routes } from 'react-router-dom';
import AdminLayout from './AdminLayout';
import { ToastProvider } from './components/Toasts';
import AuctionsPage from './pages/AuctionsPage';
import AuditLogPage from './pages/AuditLogPage';
import DashboardPage from './pages/DashboardPage';
import ImportOrdersPage from './pages/ImportOrdersPage';
import ListingDetailPage from './pages/ListingDetailPage';
import ListingsPage from './pages/ListingsPage';
import NotificationsPage from './pages/NotificationsPage';
import PaymentDetailPage from './pages/PaymentDetailPage';
import PaymentsPage from './pages/PaymentsPage';
import PlaceholderPage from './pages/PlaceholderPage';
import ReportsPage from './pages/ReportsPage';
import SystemPage from './pages/SystemPage';
import UserDetailPage from './pages/UserDetailPage';
import UsersPage from './pages/UsersPage';

/**
 * Everything under /admin/*. Loaded with React.lazy from App.tsx, so the admin code (and any chart
 * code) is its own chunk and never part of the main bundle.
 */
export default function AdminApp() {
    return (
        <ToastProvider>
            <Routes>
                <Route element={<AdminLayout />}>
                    <Route index element={<DashboardPage />} />
                    <Route path="uzytkownicy" element={<UsersPage />} />
                    <Route path="uzytkownicy/:id" element={<UserDetailPage />} />
                    <Route path="ogloszenia" element={<ListingsPage />} />
                    <Route path="ogloszenia/:id" element={<ListingDetailPage />} />
                    <Route path="zgloszenia" element={<ReportsPage />} />
                    <Route path="platnosci" element={<PaymentsPage />} />
                    <Route path="platnosci/:id" element={<PaymentDetailPage />} />
                    <Route path="zlecenia" element={<ImportOrdersPage />} />
                    <Route path="aukcje" element={<AuctionsPage />} />
                    <Route path="powiadomienia" element={<NotificationsPage />} />
                    <Route path="dziennik" element={<AuditLogPage />} />
                    <Route path="system" element={<SystemPage />} />
                    <Route path="*" element={<PlaceholderPage title="Nie znaleziono" />} />
                </Route>
            </Routes>
        </ToastProvider>
    );
}
