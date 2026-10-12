import { Construction } from 'lucide-react';
import PageHeader from '../components/PageHeader';

/** Sections that later PRs fill in. */
export default function PlaceholderPage({ title }: { title: string }) {
    return (
        <>
            <PageHeader title={title} />
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-10 text-center text-sm text-avtovo-text-secondary">
                <Construction size={28} className="mx-auto mb-3 text-avtovo-muted" />
                Ta sekcja jest w przygotowaniu.
            </div>
        </>
    );
}
