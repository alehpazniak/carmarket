import type { LengthClass, PolandZone } from '../../types/auctions';

const SELECT = 'w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent';

/** W8's Poland delivery zone and vehicle length (truck surcharge), shared by every calculator form. */
export default function W8ShippingFields({ zone, length, onZoneChange, onLengthChange }: {
    zone: PolandZone;
    length: LengthClass;
    onZoneChange: (zone: PolandZone) => void;
    onLengthChange: (length: LengthClass) => void;
}) {
    return (
        <>
            <div>
                <label className="text-xs text-avtovo-text-secondary">Strefa dostawy w Polsce</label>
                <select value={zone} onChange={e => onZoneChange(e.target.value as PolandZone)} className={SELECT}>
                    <option value="A">A — reszta kraju</option>
                    <option value="B">B — pas centralny</option>
                    <option value="C">C — okolice Łodzi, Kutna, Konina</option>
                </select>
            </div>
            <div>
                <label className="text-xs text-avtovo-text-secondary">Długość auta</label>
                <select value={length} onChange={e => onLengthChange(e.target.value as LengthClass)} className={SELECT}>
                    <option value="STANDARD">do 5 m</option>
                    <option value="OVER_5M">5–5,3 m</option>
                    <option value="OVER_5_3M">ponad 5,3 m</option>
                </select>
            </div>
        </>
    );
}
