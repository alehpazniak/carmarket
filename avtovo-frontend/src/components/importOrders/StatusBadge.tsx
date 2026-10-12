import { STATUS_LABELS, statusBadgeClass } from '../../constants/importOrders';
import type { ImportOrderStatus } from '../../types/importOrders';

export default function StatusBadge({ status, className = '' }: { status: ImportOrderStatus | null; className?: string }) {
    return (
        <span className={`inline-block text-xs font-medium px-2 py-1 rounded-md whitespace-nowrap ${statusBadgeClass(status)} ${className}`}>
            {status ? STATUS_LABELS[status] : 'Brak statusu'}
        </span>
    );
}
