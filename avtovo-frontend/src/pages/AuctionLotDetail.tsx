import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getLot, calculateImport, calculateMaxBid, getComparables } from '../api/auctions';
import type { AuctionLot, ImportCalculationResult, MaxBidResult, FuelType } from '../types/auctions';
import PhotoGallery from '../components/PhotoGallery';
import { useAuth } from '../context/AuthContext';
import { ChevronLeft, TrendingUp, Loader2, Gauge, MapPin, Gavel, Lock } from 'lucide-react';

const PLN = (v: number | undefined) => v == null ? '—' : `${v.toLocaleString('pl-PL', { maximumFractionDigits: 0 })} zł`;
const USD = (v: number | undefined) => v == null ? '—' : `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;

const STATUS_LABELS: Record<string, { label: string; className: string }> = {
    LIVE: { label: 'Na żywo', className: 'bg-avtovo-accent/15 text-avtovo-accent' },
    SOLD: { label: 'Sprzedane', className: 'bg-emerald-500/15 text-emerald-400' },
    UNSOLD: { label: 'Niesprzedane', className: 'bg-avtovo-border text-avtovo-text-secondary' },
    EXPIRED: { label: 'Wygasłe', className: 'bg-avtovo-border text-avtovo-text-secondary' },
    REMOVED: { label: 'Usunięte', className: 'bg-avtovo-border text-avtovo-text-secondary' },
};

export default function AuctionLotDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { isAuthenticated, loginWithGoogle } = useAuth();
    const [lot, setLot] = useState<AuctionLot | null>(null);
    const [loading, setLoading] = useState(true);

    const [targetSalePricePln, setTargetSalePricePln] = useState('');
    const [estimatedRepairCostPln, setEstimatedRepairCostPln] = useState('');
    const [calcResult, setCalcResult] = useState<ImportCalculationResult | null>(null);
    const [calculating, setCalculating] = useState(false);
    const [calcError, setCalcError] = useState<string | null>(null);
    const [comparables, setComparables] = useState<AuctionLot[] | null>(null);
    const [loadingComparables, setLoadingComparables] = useState(false);

    const [budgetPln, setBudgetPln] = useState('');
    const [maxBidRepairCostPln, setMaxBidRepairCostPln] = useState('');
    const [engineCapacityCm3, setEngineCapacityCm3] = useState('');
    const [fuelType, setFuelType] = useState<FuelType>('PETROL');
    const [suv, setSuv] = useState(false);
    const [maxBidResult, setMaxBidResult] = useState<MaxBidResult | null>(null);
    const [maxBidCalculating, setMaxBidCalculating] = useState(false);
    const [maxBidError, setMaxBidError] = useState<string | null>(null);

    useEffect(() => {
        if (!id) return;
        setLoading(true);
        getLot(id).then(setLot).catch(console.error).finally(() => setLoading(false));
    }, [id]);

    const handleCalculate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!lot || !targetSalePricePln) return;
        setCalculating(true);
        setCalcError(null);
        try {
            const result = await calculateImport(lot.id, {
                targetSalePricePln: Number(targetSalePricePln),
                estimatedRepairCostPln: estimatedRepairCostPln ? Number(estimatedRepairCostPln) : undefined,
                destinationCountry: 'PL',
            });
            setCalcResult(result);
        } catch {
            setCalcError('Nie udało się policzyć kosztu importu');
        } finally {
            setCalculating(false);
        }
    };

    const handleCalculateMaxBid = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!lot || !budgetPln) return;
        setMaxBidCalculating(true);
        setMaxBidError(null);
        try {
            const result = await calculateMaxBid(lot.id, {
                budgetPln: Number(budgetPln),
                estimatedRepairCostPln: maxBidRepairCostPln ? Number(maxBidRepairCostPln) : undefined,
                engineCapacityCm3: engineCapacityCm3 ? Number(engineCapacityCm3) : undefined,
                fuelType,
                suv,
            });
            setMaxBidResult(result);
        } catch {
            setMaxBidError('Nie udało się policzyć maksymalnej ceny');
        } finally {
            setMaxBidCalculating(false);
        }
    };

    const loadComparables = () => {
        if (!lot) return;
        setLoadingComparables(true);
        getComparables(lot.id)
            .then(setComparables)
            .catch(() => setComparables([]))
            .finally(() => setLoadingComparables(false));
    };

    if (loading) return (
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
            <div className="w-8 h-8 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin" />
        </div>
    );

    if (!lot) return (
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
            <p className="text-avtovo-text-secondary">Ogłoszenie aukcyjne nie zostało znalezione</p>
        </div>
    );

    const photos = (lot.imageUrls ?? []).map(u => ({ thumb: u, large: u }));
    const status = STATUS_LABELS[lot.status] ?? STATUS_LABELS.LIVE;

    return (
        <div className="min-h-screen bg-avtovo-bg py-8">
            <div className="max-w-5xl mx-auto px-4">
                <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-avtovo-text-secondary hover:text-avtovo-text mb-6 transition-colors">
                    <ChevronLeft size={18} />
                    Wróć
                </button>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Left: photos + details */}
                    <div className="lg:col-span-2 space-y-4">
                        <PhotoGallery photos={photos} alt={`${lot.year} ${lot.make} ${lot.model}`} />

                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                            <h2 className="text-avtovo-text font-semibold mb-4">Dane techniczne</h2>
                            <div className="grid grid-cols-2 gap-4">
                                {[
                                    { icon: <Gauge size={16} />, label: 'Przebieg', value: lot.odometer != null ? `${lot.odometer.toLocaleString('en-US')} ${lot.odometerUnit ?? 'mi'}` : '—' },
                                    { icon: <Gavel size={16} />, label: 'Uszkodzenia', value: [lot.primaryDamage, lot.secondaryDamage].filter(Boolean).join(' · ') || '—' },
                                    { icon: <MapPin size={16} />, label: 'Lokalizacja', value: lot.auctionLocation ?? '—' },
                                    { icon: <Gauge size={16} />, label: 'Silnik', value: lot.engineCapacity != null ? `${lot.engineCapacity} L` : '—' },
                                    { icon: <Gauge size={16} />, label: 'Paliwo', value: lot.fuelType ?? '—' },
                                    { icon: <Gauge size={16} />, label: 'Skrzynia', value: lot.transmission ?? '—' },
                                ].map(({ icon, label, value }) => (
                                    <div key={label} className="flex items-start gap-3">
                                        <span className="text-avtovo-accent mt-0.5">{icon}</span>
                                        <div>
                                            <p className="text-xs text-avtovo-text-secondary">{label}</p>
                                            <p className="text-avtovo-text font-medium">{value}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* Import calculator */}
                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                            <h2 className="text-avtovo-text font-semibold mb-4 flex items-center gap-2">
                                <TrendingUp size={18} className="text-avtovo-accent" /> Kalkulator kosztu importu
                            </h2>
                            {!isAuthenticated ? (
                                <div className="bg-avtovo-bg border border-avtovo-border rounded-lg p-4 text-center space-y-2">
                                    <Lock size={20} className="text-avtovo-text-secondary mx-auto" />
                                    <p className="text-avtovo-text-secondary text-sm">Zaloguj się, aby policzyć koszt importu tego pojazdu.</p>
                                    <button
                                        onClick={loginWithGoogle}
                                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors"
                                    >
                                        Zaloguj się
                                    </button>
                                </div>
                            ) : (
                            <>
                            <form onSubmit={handleCalculate} className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-end">
                                <div>
                                    <label className="text-xs text-avtovo-text-secondary">Docelowa cena sprzedaży (PLN) *</label>
                                    <input
                                        type="number"
                                        required
                                        value={targetSalePricePln}
                                        onChange={e => setTargetSalePricePln(e.target.value)}
                                        className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                    />
                                </div>
                                <div>
                                    <label className="text-xs text-avtovo-text-secondary">Szacowany koszt naprawy (PLN)</label>
                                    <input
                                        type="number"
                                        value={estimatedRepairCostPln}
                                        onChange={e => setEstimatedRepairCostPln(e.target.value)}
                                        className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                    />
                                </div>
                                <button
                                    type="submit"
                                    disabled={calculating}
                                    className="sm:col-span-2 flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-60 text-white rounded-lg py-2.5 text-sm font-medium transition-colors"
                                >
                                    {calculating && <Loader2 size={15} className="animate-spin" />} Policz koszt importu
                                </button>
                            </form>
                            {calcError && <p className="text-red-400 text-sm mt-2">{calcError}</p>}
                            {calcResult && (
                                <div className="mt-4 bg-avtovo-bg border border-avtovo-border rounded-lg p-4 space-y-1.5 text-sm">
                                    <Row label="Cena aukcji" value={USD(calcResult.auctionPrice)} />
                                    <Row label="Opłata aukcyjna" value={USD(calcResult.auctionFee)} />
                                    <Row label="Transport w USA" value={USD(calcResult.us_delivery)} />
                                    <Row label="Fracht morski" value={USD(calcResult.oceanFreight)} />
                                    <Row label="Opłata portowa (EU)" value={USD(calcResult.euPortFee)} />
                                    <Row label="Akcyza" value={PLN(calcResult.excise)} />
                                    <Row label="Cło" value={PLN(calcResult.customsDuty)} />
                                    <Row label="VAT" value={PLN(calcResult.vat)} />
                                    <Row label="Odprawa celna" value={PLN(calcResult.customsClearance)} />
                                    <Row label="Dostawa w UE" value={PLN(calcResult.euDelivery)} />
                                    <div className="border-t border-avtovo-border my-2" />
                                    <Row label="Suma (PLN)" value={PLN(calcResult.totalPln)} bold />
                                    <Row label="Suma (USD)" value={USD(calcResult.totalUsd)} bold />
                                    <Row label="Szacowany zysk" value={PLN(calcResult.estimatedProfitPln)} bold />
                                    <Row label="Marża" value={`${calcResult.profitMarginPercent?.toFixed(1) ?? '—'}%`} />
                                    <Row label="Ocena opłacalności" value={calcResult.profitRating} />
                                </div>
                            )}
                            </>
                            )}
                        </div>

                        {/* Max affordable price calculator — only meaningful while the auction is still live */}
                        {lot.status === 'LIVE' && (
                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                            <h2 className="text-avtovo-text font-semibold mb-4 flex items-center gap-2">
                                <TrendingUp size={18} className="text-avtovo-accent" /> Ile mogę zaoferować za auto?
                            </h2>
                            {!isAuthenticated ? (
                                <div className="bg-avtovo-bg border border-avtovo-border rounded-lg p-4 text-center space-y-2">
                                    <Lock size={20} className="text-avtovo-text-secondary mx-auto" />
                                    <p className="text-avtovo-text-secondary text-sm">Zaloguj się, aby policzyć maksymalną cenę zakupu.</p>
                                    <button
                                        onClick={loginWithGoogle}
                                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors"
                                    >
                                        Zaloguj się
                                    </button>
                                </div>
                            ) : (
                            <>
                            <p className="text-avtovo-text-secondary text-sm mb-3">
                                Podaj budżet i szacowany koszt naprawy — reszta (transport z Apibara, cło, akcyza, VAT) jest liczona automatycznie,
                                a wynikiem jest maksymalna cena, jaką możesz zapłacić za samo auto na aukcji.
                            </p>
                            <form onSubmit={handleCalculateMaxBid} className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-end">
                                <div>
                                    <label className="text-xs text-avtovo-text-secondary">Twój budżet (PLN) *</label>
                                    <input
                                        type="number"
                                        required
                                        value={budgetPln}
                                        onChange={e => setBudgetPln(e.target.value)}
                                        className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                    />
                                </div>
                                <div>
                                    <label className="text-xs text-avtovo-text-secondary">Szacowany koszt naprawy (PLN)</label>
                                    <input
                                        type="number"
                                        value={maxBidRepairCostPln}
                                        onChange={e => setMaxBidRepairCostPln(e.target.value)}
                                        className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                    />
                                </div>
                                <div>
                                    <label className="text-xs text-avtovo-text-secondary">Pojemność silnika (cm³)</label>
                                    <input
                                        type="number"
                                        value={engineCapacityCm3}
                                        onChange={e => setEngineCapacityCm3(e.target.value)}
                                        placeholder={lot.engineCapacity != null ? String(lot.engineCapacity) : undefined}
                                        className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                    />
                                </div>
                                <div>
                                    <label className="text-xs text-avtovo-text-secondary">Rodzaj napędu</label>
                                    <select
                                        value={fuelType}
                                        onChange={e => setFuelType(e.target.value as FuelType)}
                                        className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                    >
                                        <option value="PETROL">Benzyna</option>
                                        <option value="DIESEL">Diesel</option>
                                        <option value="HYBRID">Hybryda (HEV/MHEV)</option>
                                        <option value="PLUGIN_HYBRID">Hybryda plug-in (PHEV)</option>
                                        <option value="ELECTRIC">Elektryczny</option>
                                    </select>
                                </div>
                                <label className="flex items-center gap-2 text-sm text-avtovo-text-secondary">
                                    <input type="checkbox" checked={suv} onChange={e => setSuv(e.target.checked)} />
                                    To SUV (droższy transport morski)
                                </label>
                                <button
                                    type="submit"
                                    disabled={maxBidCalculating}
                                    className="sm:col-span-2 flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-60 text-white rounded-lg py-2.5 text-sm font-medium transition-colors"
                                >
                                    {maxBidCalculating && <Loader2 size={15} className="animate-spin" />} Policz maksymalną cenę
                                </button>
                            </form>
                            {maxBidError && <p className="text-red-400 text-sm mt-2">{maxBidError}</p>}
                            {maxBidResult && (
                                <div className="mt-4 bg-avtovo-bg border border-avtovo-border rounded-lg p-4 space-y-1.5 text-sm">
                                    <Row label="Transport (Apibara)" value={`${USD(maxBidResult.shippingCostUsd)} (${PLN(maxBidResult.shippingCostPln)})`} />
                                    <Row label="Transport morski / dostawa" value={`${USD(maxBidResult.shippingDeliveryUsd)} (${PLN(maxBidResult.shippingDeliveryPln)})`} />
                                    <Row label={`Akcyza (${(maxBidResult.exciseRate * 100).toFixed(2)}%)`} value={PLN(maxBidResult.excise)} />
                                    <Row label={`Cło (${(maxBidResult.customsDutyRate * 100).toFixed(1)}%)`} value={PLN(maxBidResult.customsDuty)} />
                                    <Row label={`VAT (${(maxBidResult.vatRate * 100).toFixed(0)}%)`} value={PLN(maxBidResult.vat)} />
                                    <Row label="Naprawa" value={PLN(maxBidResult.estimatedRepairCostPln)} />
                                    <div className="border-t border-avtovo-border my-2" />
                                    {maxBidResult.budgetSufficient ? (
                                        <>
                                            <Row label="Maks. cena za auto (PLN)" value={PLN(maxBidResult.maxCarPricePln)} bold />
                                            <Row label="Maks. cena za auto (USD)" value={USD(maxBidResult.maxCarPriceUsd)} bold />
                                        </>
                                    ) : (
                                        <p className="text-red-400 font-medium">Budżet nie wystarcza nawet na transport i naprawę tego auta.</p>
                                    )}
                                </div>
                            )}
                            </>
                            )}
                        </div>
                        )}

                        {/* Comparables */}
                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                            <div className="flex items-center justify-between mb-3">
                                <h2 className="text-avtovo-text font-semibold">Sprzedane porównywalne auta</h2>
                                {comparables === null && (
                                    <button onClick={loadComparables} disabled={loadingComparables} className="text-avtovo-accent text-sm hover:underline">
                                        {loadingComparables ? 'Ładowanie…' : 'Pokaż'}
                                    </button>
                                )}
                            </div>
                            {comparables && (
                                comparables.length === 0 ? (
                                    <p className="text-avtovo-text-secondary text-sm">Brak danych porównawczych</p>
                                ) : (
                                    <div className="space-y-2">
                                        {comparables.map(c => (
                                            <div key={c.id} className="flex items-center justify-between text-sm bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2">
                                                <span className="text-avtovo-text">{c.year} {c.make} {c.model} · {c.primaryDamage ?? '—'}</span>
                                                <span className="text-avtovo-accent font-medium">{USD(c.auctionPrice)}</span>
                                            </div>
                                        ))}
                                    </div>
                                )
                            )}
                        </div>
                    </div>

                    {/* Right: summary */}
                    <div className="space-y-4">
                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 sticky top-20">
                            <div className="flex items-center gap-2 mb-2">
                                <span className={`text-xs font-medium px-2 py-1 rounded-md ${status.className}`}>{status.label}</span>
                                <span className="text-xs font-medium px-2 py-1 rounded-md bg-avtovo-bg border border-avtovo-border text-avtovo-text-secondary">
                                    {lot.source}
                                </span>
                            </div>
                            <h1 className="text-2xl font-bold text-avtovo-text mb-1">{lot.year} {lot.make} {lot.model}</h1>
                            <p className="text-avtovo-text-secondary text-sm mb-4">Lot #{lot.lot_number} · VIN {lot.vin}</p>
                            <p className="text-3xl font-bold text-avtovo-accent mb-1">{USD(lot.auctionPrice)}</p>
                            {lot.buyNowPrice != null && (
                                <p className="text-avtovo-text-secondary text-sm mb-4">Buy now: {USD(lot.buyNowPrice)}</p>
                            )}
                            <div className="bg-avtovo-bg border border-avtovo-border rounded-xl p-4 text-sm space-y-1.5 mt-4">
                                <Row label="Data aukcji" value={lot.auctionDate ?? '—'} />
                                <Row label="Data sprzedaży" value={lot.saleDate ?? '—'} />
                                <Row label="Lokalizacja" value={lot.auctionLocation ?? '—'} />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
    return (
        <div className={`flex items-center justify-between ${bold ? 'text-avtovo-text font-semibold' : 'text-avtovo-text-secondary'}`}>
            <span>{label}</span>
            <span>{value}</span>
        </div>
    );
}
