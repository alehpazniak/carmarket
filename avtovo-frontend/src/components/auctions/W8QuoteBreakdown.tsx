import type { W8ShippingQuote } from '../../types/auctions';

const USD = (v: number | undefined) => v == null ? '—' : `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
const PLN = (v: number | undefined) => v == null ? '—' : `${v.toLocaleString('pl-PL', { maximumFractionDigits: 0 })} zł`;
const EUR = (v: number) => `${v.toLocaleString('pl-PL', { maximumFractionDigits: 0 })} €`;

function Line({ label, value, children }: { label: string; value: string; children?: React.ReactNode }) {
    return (
        <div className="flex items-center justify-between gap-3 text-avtovo-text-secondary">
            <span className="flex items-center gap-2">{label}{children}</span>
            <span className="whitespace-nowrap">{value}</span>
        </div>
    );
}

/**
 * W8 Shipping's itemized transport cost, in place of the flat shipping lines. With `showTotal` it also sums
 * everything (to the EU border + after it + W8 fees) for the standalone transport calculator.
 */
export default function W8QuoteBreakdown({ quote, showTotal = false }: { quote: W8ShippingQuote; showTotal?: boolean }) {
    const { chosen } = quote;
    const rotterdamEur = quote.forwarderEur + quote.brokerEur + quote.scanEur;
    const transit = chosen.transitDaysMin != null && chosen.transitDaysMax != null
        ? (chosen.transitDaysMin === chosen.transitDaysMax
            ? `${chosen.transitDaysMax} dni`
            : `${chosen.transitDaysMin}–${chosen.transitDaysMax} dni`)
        : null;
    const totalPln = quote.toEuBorderUsd * quote.usdPlnRate + quote.afterBorderPln + quote.w8FeesUsd * quote.usdPlnRate;
    const others = quote.options.filter(o => o.port !== chosen.port);

    return (
        <div className="space-y-1.5">
            <p className="text-xs text-avtovo-text-secondary">
                Transport W8 Shipping ({quote.containerShare === 'THIRD' ? '1/3' : '1/4'} kontenera)
                {quote.matchType === 'EXACT' && <> · {quote.matchedLocation}</>}
                {quote.matchType === 'OTHER_AUCTION' && <> · cena dla tej samej lokalizacji innej aukcji ({quote.matchedLocation})</>}
                {quote.matchType === 'STATE_ESTIMATE' && <> · szacunek dla stanu {quote.matchedLocation}</>}
            </p>
            <Line label={`Holowanie do portu ${chosen.portLabel}`} value={USD(chosen.towingUsd)} />
            <Line label="Fracht morski do Rotterdamu" value={USD(chosen.oceanFreightUsd)}>
                {chosen.oceanFreightSource !== 'W8_PRICE_LIST' && (
                    <span className="text-[10px] uppercase tracking-wide bg-amber-500/15 text-amber-400 rounded px-1.5 py-0.5">szacunek</span>
                )}
            </Line>
            <Line label="Spedycja + broker + skan (Rotterdam)" value={`${EUR(rotterdamEur)} (${PLN(rotterdamEur * quote.eurPlnRate)})`} />
            <Line label={`Transport do Polski (strefa ${quote.polandZone})`} value={PLN(quote.polandDeliveryPln)} />
            {quote.w8FeesUsd > 0 && <Line label="Opłaty W8 (dostęp + dealer fee)" value={USD(quote.w8FeesUsd)} />}
            {transit && <Line label="Transport morski" value={transit} />}
            {showTotal && (
                <>
                    <div className="border-t border-avtovo-border my-2" />
                    <Line label="Do granicy UE (wlicza się do wartości celnej)" value={USD(quote.toEuBorderUsd)} />
                    <div className="flex items-center justify-between text-avtovo-text font-semibold">
                        <span>Transport razem</span>
                        <span>{PLN(totalPln)}</span>
                    </div>
                </>
            )}
            {others.length > 0 && (
                <div className="text-xs text-avtovo-text-secondary pt-1">
                    Inne porty:{' '}
                    {others.map(o => `${o.portLabel} ${USD(o.toEuBorderUsd)}`).join(' · ')}
                </div>
            )}
            <p className="text-xs text-avtovo-text-secondary pt-1">Postój w porcie: ok. 10 dni gratis, potem 15 €/dzień.</p>
        </div>
    );
}
