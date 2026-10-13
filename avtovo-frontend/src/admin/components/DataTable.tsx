import type { ReactNode } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { EmptyState, ErrorState, LoadingState } from './States';

export interface Column<T> {
    key: string;
    header: string;
    render: (row: T) => ReactNode;
    /** Server-side sort field; omit for unsortable columns. */
    sortField?: string;
    className?: string;
}

interface Props<T> {
    columns: Column<T>[];
    rows: T[] | undefined;
    rowKey: (row: T) => string;
    loading: boolean;
    error?: unknown;
    onRetry?: () => void;
    emptyText?: string;
    onRowClick?: (row: T) => void;
    /** Spring Data page info (0-based). */
    page?: number;
    totalPages?: number;
    totalElements?: number;
    onPageChange?: (page: number) => void;
    /** "field,asc" | "field,desc" */
    sort?: string;
    onSortChange?: (sort: string) => void;
}

/** Server-paginated, server-sorted table; filters live in the page (and its URL), not here. */
export default function DataTable<T>(props: Props<T>) {
    const { columns, rows, rowKey, loading, error, onRetry, emptyText, onRowClick,
        page = 0, totalPages = 0, totalElements, onPageChange, sort, onSortChange } = props;
    const [sortField, sortDir] = (sort ?? '').split(',');

    const toggleSort = (field: string) => {
        if (!onSortChange) return;
        onSortChange(sortField === field && sortDir === 'desc' ? `${field},asc` : `${field},desc`);
    };

    let body: ReactNode;
    if (loading && !rows) body = <LoadingState />;
    else if (error) body = <ErrorState onRetry={onRetry} />;
    else if (!rows || rows.length === 0) body = <EmptyState label={emptyText} />;

    return (
        <div className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden">
            {body ?? (
                <div className={`overflow-x-auto ${loading ? 'opacity-60' : ''}`}>
                    <table className="w-full text-sm">
                        <thead>
                        <tr className="border-b border-avtovo-border text-left text-xs uppercase tracking-wide text-avtovo-text-secondary">
                            {columns.map(c => (
                                <th key={c.key} className={`px-4 py-3 font-medium whitespace-nowrap ${c.className ?? ''}`}>
                                    {c.sortField && onSortChange ? (
                                        <button onClick={() => toggleSort(c.sortField!)}
                                                className="inline-flex items-center gap-1 hover:text-avtovo-text">
                                            {c.header}
                                            {sortField === c.sortField
                                                ? (sortDir === 'asc' ? <ArrowUp size={12} /> : <ArrowDown size={12} />)
                                                : <ArrowUpDown size={12} className="opacity-40" />}
                                        </button>
                                    ) : c.header}
                                </th>
                            ))}
                        </tr>
                        </thead>
                        <tbody>
                        {rows!.map(row => (
                            <tr key={rowKey(row)}
                                onClick={onRowClick ? () => onRowClick(row) : undefined}
                                className={`border-b border-avtovo-border last:border-0 ${onRowClick ? 'cursor-pointer hover:bg-white/5' : ''}`}>
                                {columns.map(c => (
                                    <td key={c.key} className={`px-4 py-3 text-avtovo-text align-top ${c.className ?? ''}`}>{c.render(row)}</td>
                                ))}
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            {onPageChange && totalPages > 1 && (
                <div className="flex items-center justify-between gap-3 px-4 py-3 border-t border-avtovo-border text-sm text-avtovo-text-secondary">
                    <span>
                        Strona {page + 1} z {totalPages}{totalElements != null ? ` · ${totalElements} pozycji` : ''}
                    </span>
                    <div className="flex gap-2">
                        <button disabled={page <= 0} onClick={() => onPageChange(page - 1)} aria-label="Poprzednia strona"
                                className="p-1.5 rounded-lg border border-avtovo-border disabled:opacity-40 hover:text-avtovo-text">
                            <ChevronLeft size={16} />
                        </button>
                        <button disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)} aria-label="Następna strona"
                                className="p-1.5 rounded-lg border border-avtovo-border disabled:opacity-40 hover:text-avtovo-text">
                            <ChevronRight size={16} />
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
