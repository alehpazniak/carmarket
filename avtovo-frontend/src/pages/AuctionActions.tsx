import { useState } from 'react';
import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getLotFacets, searchLots, type LotSearchParams } from '../api/auctions';
import type { AuctionLot, LotMakeFacet } from '../types/auctions';
import { makeDisplayName, modelDisplayName } from '../utils/auctionNames';
import AuctionLotCard from '../components/AuctionLotCard';
import { useAuth } from '../context/AuthContext';
import { OLDEST_YEAR_AUCTIONS, yearOptions } from '../utils/years';
import { Search, ClipboardList } from 'lucide-react';

const hasFilters = (params: LotSearchParams) =>
    params.make != null || params.model != null || params.yearFrom != null || params.yearTo != null;

export default function AuctionActions() {
    const [lots, setLots] = useState<AuctionLot[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [filters, setFilters] = useState<LotSearchParams>({});
    // Filters of the list on screen (not the unsubmitted form), for the empty-state message
    const [appliedFilters, setAppliedFilters] = useState<LotSearchParams>({});
    const [facets, setFacets] = useState<LotMakeFacet[]>([]);
    const [facetsLoaded, setFacetsLoaded] = useState(false);
    const navigate = useNavigate();
    const { hasRole } = useAuth();
    const isDealer = hasRole('DEALER') || hasRole('ADMIN');
    const years = yearOptions(OLDEST_YEAR_AUCTIONS);

    // `loading` starts as true, so the initial load doesn't need to set it
    const loadLots = (params: LotSearchParams, targetPage = 0) => {
        searchLots({ ...params, page: targetPage, size: 12 })
            .then(res => {
                setLots(res.content);
                setAppliedFilters(params);
                setTotalPages(res.totalPages);
                setPage(res.number);
            })
            .catch(console.error)
            .finally(() => setLoading(false));
    };

    const fetchLots = (params: LotSearchParams, targetPage = 0) => {
        setLoading(true);
        loadLots(params, targetPage);
    };

    useEffect(() => {
        loadLots({});
        // Independent of the list: if this fails, the dropdowns only offer "any" and the list still works
        getLotFacets()
            .then(res => { setFacets(res.makes); setFacetsLoaded(true); })
            .catch(console.error);
    }, []);

    const selectedMake = facets.find(m => m.make === filters.make);
    const noLots = facetsLoaded && facets.length === 0;

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchLots(filters, 0);
    };

    return (
        <div className="min-h-screen bg-avtovo-bg py-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
                <div className="flex flex-wrap items-start justify-between gap-4">
                    <div>
                        <h1 className="text-3xl font-bold text-avtovo-text mb-2">Aukcje importowe (USA)</h1>
                        <p className="text-avtovo-text-secondary">
                            Auta z aukcji Copart i IAAI w USA. Koszt importu policzysz na stronie auta.
                        </p>
                    </div>
                    {isDealer && (
                        <Link to="/panel-dealera/zlecenia"
                              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent">
                            <ClipboardList size={16} /> Zlecenia importu
                        </Link>
                    )}
                </div>

                {/* Filters */}
                <form onSubmit={handleSearch} className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 grid grid-cols-2 sm:grid-cols-5 gap-3">
                    <select
                        value={filters.make ?? ''}
                        onChange={e => setFilters(f => ({ ...f, make: e.target.value || undefined, model: undefined }))}
                        disabled={noLots}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <option value="">Wszystkie marki</option>
                        {facets.map(m => (
                            <option key={m.make} value={m.make}>{makeDisplayName(m.make)} ({m.count})</option>
                        ))}
                    </select>
                    <select
                        value={filters.model ?? ''}
                        onChange={e => setFilters(f => ({ ...f, model: e.target.value || undefined }))}
                        disabled={noLots || !selectedMake}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <option value="">{selectedMake ? 'Wszystkie modele' : 'Najpierw wybierz markę'}</option>
                        {selectedMake?.models.map(m => (
                            <option key={m.model} value={m.model}>{modelDisplayName(selectedMake.make, m.model)} ({m.count})</option>
                        ))}
                    </select>
                    <select
                        value={filters.yearFrom ?? ''}
                        onChange={e => setFilters(f => ({ ...f, yearFrom: e.target.value ? Number(e.target.value) : undefined }))}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    >
                        <option value="">Rok od</option>
                        {years.filter(y => filters.yearTo == null || y <= filters.yearTo).map(y => <option key={y} value={y}>{y}</option>)}
                    </select>
                    <select
                        value={filters.yearTo ?? ''}
                        onChange={e => setFilters(f => ({ ...f, yearTo: e.target.value ? Number(e.target.value) : undefined }))}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    >
                        <option value="">Rok do</option>
                        {years.filter(y => filters.yearFrom == null || y >= filters.yearFrom).map(y => <option key={y} value={y}>{y}</option>)}
                    </select>
                    <button
                        type="submit"
                        className="flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors"
                    >
                        <Search size={15} /> Szukaj
                    </button>
                </form>

                {/* Lot grid */}
                {loading ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {Array.from({ length: 6 }).map((_, i) => (
                            <div key={i} className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden animate-pulse">
                                <div className="aspect-[16/10] bg-avtovo-border" />
                                <div className="p-4 space-y-2">
                                    <div className="h-4 bg-avtovo-border rounded w-3/4" />
                                    <div className="h-4 bg-avtovo-border rounded w-1/2" />
                                </div>
                            </div>
                        ))}
                    </div>
                ) : lots.length === 0 ? (
                    <div className="text-center py-16 text-avtovo-text-secondary">
                        {hasFilters(appliedFilters)
                            ? 'Brak aukcji spełniających kryteria'
                            : 'Brak aktualnych aukcji. Lista odświeża się automatycznie.'}
                    </div>
                ) : (
                    <>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                            {lots.map(lot => (
                                <AuctionLotCard key={lot.id} lot={lot} onSelect={l => navigate(`/aukcje/lot/${l.id}`)} />
                            ))}
                        </div>
                        {totalPages > 1 && (
                            <div className="flex items-center justify-center gap-2">
                                <button
                                    disabled={page === 0}
                                    onClick={() => fetchLots(filters, page - 1)}
                                    className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text-secondary disabled:opacity-40 hover:border-gray-600 text-sm"
                                >
                                    Poprzednia
                                </button>
                                <span className="text-avtovo-text-secondary text-sm">{page + 1} / {totalPages}</span>
                                <button
                                    disabled={page >= totalPages - 1}
                                    onClick={() => fetchLots(filters, page + 1)}
                                    className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text-secondary disabled:opacity-40 hover:border-gray-600 text-sm"
                                >
                                    Następna
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
