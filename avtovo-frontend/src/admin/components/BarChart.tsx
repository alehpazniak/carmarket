interface Point {
    date: string;
    value: number;
}

interface Props {
    points: Point[];
    /** How a value is shown in the tooltip and the max label, e.g. money. */
    format?: (value: number) => string;
    label: string;
}

/**
 * Daily bars as plain SVG (no chart library, so nothing is added to any bundle). Scales to the
 * container width; each bar has a native tooltip with the day and value.
 */
export default function BarChart({ points, format = v => String(v), label }: Props) {
    const width = 300;
    const height = 80;
    const max = Math.max(1, ...points.map(p => p.value));
    const gap = 1;
    const barWidth = points.length > 0 ? width / points.length - gap : 0;
    const total = points.reduce((sum, p) => sum + p.value, 0);

    return (
        <figure className="space-y-2">
            <figcaption className="flex items-baseline justify-between gap-3 text-sm">
                <span className="text-avtovo-text">{label}</span>
                <span className="text-avtovo-text-secondary">razem {format(total)}</span>
            </figcaption>
            <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="w-full h-24" role="img" aria-label={label}>
                {points.map((p, i) => {
                    const h = (p.value / max) * (height - 2);
                    return (
                        <rect key={p.date} x={i * (barWidth + gap)} y={height - h} width={Math.max(barWidth, 0.5)} height={Math.max(h, p.value > 0 ? 1 : 0)}
                              className="fill-avtovo-accent/80 hover:fill-avtovo-accent">
                            <title>{`${new Date(p.date).toLocaleDateString('pl-PL')}: ${format(p.value)}`}</title>
                        </rect>
                    );
                })}
                <line x1={0} y1={height - 0.5} x2={width} y2={height - 0.5} className="stroke-avtovo-border" strokeWidth={1} />
            </svg>
            <div className="flex justify-between text-[11px] text-avtovo-muted">
                <span>{points[0] ? new Date(points[0].date).toLocaleDateString('pl-PL') : ''}</span>
                <span>max {format(max)}</span>
                <span>{points.length ? new Date(points[points.length - 1].date).toLocaleDateString('pl-PL') : ''}</span>
            </div>
        </figure>
    );
}
