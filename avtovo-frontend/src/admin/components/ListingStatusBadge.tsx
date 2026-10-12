import { LISTING_STATUS_LABELS } from '../../constants/reports';
import { Badge } from './Badges';

const TONES: Record<string, 'red' | 'yellow' | 'green' | 'gray'> = {
    ACTIVE: 'green', BLOCKED: 'red', SUSPENDED: 'yellow', SOLD: 'gray', DRAFT: 'gray', REMOVED: 'gray',
};

export default function ListingStatusBadge({ status }: { status: string | null | undefined }) {
    if (!status) return <Badge tone="gray">—</Badge>;
    return <Badge tone={TONES[status] ?? 'gray'}>{LISTING_STATUS_LABELS[status] ?? status}</Badge>;
}
