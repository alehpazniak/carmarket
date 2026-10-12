import { Route, Routes } from 'react-router-dom';
import AdminLayout from './AdminLayout';
import { ToastProvider } from './components/Toasts';
import AuditLogPage from './pages/AuditLogPage';
import ListingDetailPage from './pages/ListingDetailPage';
import ListingsPage from './pages/ListingsPage';
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
                    <Route index element={<PlaceholderPage title="Pulpit" />} />
                    <Route path="uzytkownicy" element={<UsersPage />} />
                    <Route path="uzytkownicy/:id" element={<UserDetailPage />} />
                    <Route path="ogloszenia" element={<ListingsPage />} />
                    <Route path="ogloszenia/:id" element={<ListingDetailPage />} />
                    <Route path="zgloszenia" element={<ReportsPage />} />
                    <Route path="platnosci/*" element={<PlaceholderPage title="Płatności" />} />
                    <Route path="zlecenia" element={<PlaceholderPage title="Zlecenia importu" />} />
                    <Route path="aukcje" element={<PlaceholderPage title="Aukcje" />} />
                    <Route path="powiadomienia" element={<PlaceholderPage title="Powiadomienia" />} />
                    <Route path="dziennik" element={<AuditLogPage />} />
                    <Route path="system" element={<SystemPage />} />
                    <Route path="*" element={<PlaceholderPage title="Nie znaleziono" />} />
                </Route>
            </Routes>
        </ToastProvider>
    );
}
