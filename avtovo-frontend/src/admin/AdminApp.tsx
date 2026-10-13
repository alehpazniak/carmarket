import { Route, Routes } from 'react-router-dom';
import AdminLayout from './AdminLayout';
import { ToastProvider } from './components/Toasts';
import AuditLogPage from './pages/AuditLogPage';
import PlaceholderPage from './pages/PlaceholderPage';
import SystemPage from './pages/SystemPage';

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
                    <Route path="uzytkownicy/*" element={<PlaceholderPage title="Użytkownicy" />} />
                    <Route path="ogloszenia/*" element={<PlaceholderPage title="Ogłoszenia" />} />
                    <Route path="zgloszenia" element={<PlaceholderPage title="Zgłoszenia" />} />
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
