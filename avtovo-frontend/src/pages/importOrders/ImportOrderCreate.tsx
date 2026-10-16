import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ChevronLeft, Link2, Loader2 } from 'lucide-react';
import { createImportOrder, resolveAuctionUrl } from '../../api/importOrders';
import { getApibaraVehicle } from '../../api/auctions';
import { getMyProfile } from '../../api/users';
import CredentialsModal from '../../components/importOrders/CredentialsModal';
import { Field, Section, TriStateSelect, inputClass } from '../../components/importOrders/FormField';
import { apiErrorMessage } from '../../utils/importOrderUi';
import { SOURCE_LABELS, carTitle } from '../../constants/importOrders';
import { fromApibaraVehicle, fromAuctionLot, type ImportOrderPrefill, type PrefillResult } from '../../utils/importOrderPrefill';
import type { CreateImportOrderRequest, CreatedImportOrder, LotSource } from '../../types/importOrders';

interface FormState {
    source: LotSource;
    lotNumber: string;
    vin: string;
    make: string;
    model: string;
    year: string;
    saleDate: string;
    purchasePriceUsd: string;
    auctionLocation: string;
    titleType: string;
    titleExportOk: boolean | null;
    titleRegistrationOk: boolean | null;
    clientName: string;
    clientEmail: string;
    clientPhone: string;
    notifyClient: boolean;
    dealerContactName: string;
    dealerContactPhone: string;
    dealerContactEmail: string;
    internalNotes: string;
}

const EMPTY: FormState = {
    source: 'COPART', lotNumber: '', vin: '', make: '', model: '', year: '', saleDate: '', purchasePriceUsd: '',
    auctionLocation: '', titleType: '', titleExportOk: null, titleRegistrationOk: null, clientName: '', clientEmail: '',
    clientPhone: '', notifyClient: false, dealerContactName: '', dealerContactPhone: '', dealerContactEmail: '', internalNotes: '',
};

function applyPrefill(form: FormState, prefill: PrefillResult): FormState {
    const f = prefill.fields;
    return {
        ...form,
        source: f.source,
        lotNumber: f.lotNumber ?? '',
        vin: f.vin ?? '',
        make: f.make ?? '',
        model: f.model ?? '',
        year: f.year != null ? String(f.year) : '',
        saleDate: f.saleDate ?? '',
        auctionLocation: f.auctionLocation ?? '',
        titleType: f.titleType ?? '',
        titleExportOk: f.titleExportOk ?? null,
        titleRegistrationOk: f.titleRegistrationOk ?? null,
        // The real amount comes from the auction invoice; the last sold price is only a suggestion
        purchasePriceUsd: '',
    };
}

function initialPrefill(state: unknown): PrefillResult | null {
    const prefill = (state as { prefill?: ImportOrderPrefill } | null)?.prefill;
    if (!prefill) return null;
    return prefill.kind === 'apibara' ? fromApibaraVehicle(prefill.vehicle) : fromAuctionLot(prefill.lot);
}

const VIN_FORMAT = /^[A-HJ-NPR-Z0-9]{17}$/;

/** /panel-dealera/zlecenia/nowe — opened empty or prefilled from an auction page (router state). */
export default function ImportOrderCreate() {
    const navigate = useNavigate();
    const location = useLocation();
    const [prefill, setPrefill] = useState<PrefillResult | null>(() => initialPrefill(location.state));
    const [form, setForm] = useState<FormState>(() => (prefill ? applyPrefill(EMPTY, prefill) : EMPTY));
    const [auctionUrl, setAuctionUrl] = useState('');
    const [resolving, setResolving] = useState(false);
    const [resolveError, setResolveError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [created, setCreated] = useState<CreatedImportOrder | null>(null);

    const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm(f => ({ ...f, [key]: value }));

    // Dealer contact defaults from the dealer's own profile; the dealer can edit them
    useEffect(() => {
        getMyProfile()
            .then(profile => setForm(f => ({
                ...f,
                dealerContactName: f.dealerContactName || profile.displayName || '',
                dealerContactPhone: f.dealerContactPhone || profile.phoneNumber || '',
                dealerContactEmail: f.dealerContactEmail || profile.email || '',
            })))
            .catch(err => console.error('Could not load the dealer profile:', err));
    }, []);

    const resolveLink = async () => {
        if (!auctionUrl.trim()) return;
        setResolving(true);
        setResolveError(null);
        try {
            const resolved = await resolveAuctionUrl(auctionUrl.trim());
            const data = (resolved.data ?? resolved) as Record<string, unknown>;
            const id = [data.vin, data.lot_number, data.lotNumber, data.slug_vin]
                .find(v => typeof v === 'string' || typeof v === 'number');
            if (id == null) throw new Error('No identifier in url-to-details response');
            const vehicle = await getApibaraVehicle(String(id));
            if (!vehicle.data) throw new Error('Vehicle not found');
            const result = fromApibaraVehicle(vehicle.data);
            setPrefill(result);
            setForm(f => applyPrefill(f, result));
        } catch (err) {
            console.error(err);
            setResolveError('Nie udało się odczytać pojazdu z tego linku. Sprawdź link albo wpisz dane ręcznie.');
        } finally {
            setResolving(false);
        }
    };

    const submit = async (e: FormEvent) => {
        e.preventDefault();
        setError(null);
        const vin = form.vin.replace(/\s+/g, '').toUpperCase();
        if (vin && !VIN_FORMAT.test(vin)) {
            setError('VIN musi mieć 17 znaków (cyfry i litery bez I, O, Q).');
            return;
        }
        const text = (s: string) => s.trim() || undefined;
        const body: CreateImportOrderRequest = {
            source: form.source,
            lotNumber: text(form.lotNumber),
            vin: vin || undefined,
            make: text(form.make),
            model: text(form.model),
            year: form.year ? Number(form.year) : undefined,
            saleDate: form.saleDate || undefined,
            purchasePriceUsd: form.purchasePriceUsd ? Number(form.purchasePriceUsd.replace(',', '.')) : undefined,
            auctionLocation: text(form.auctionLocation),
            titleType: text(form.titleType),
            titleExportOk: form.titleExportOk,
            titleRegistrationOk: form.titleRegistrationOk,
            clientName: text(form.clientName),
            clientEmail: text(form.clientEmail),
            clientPhone: text(form.clientPhone),
            notifyClient: form.notifyClient,
            dealerContactName: text(form.dealerContactName),
            dealerContactPhone: text(form.dealerContactPhone),
            dealerContactEmail: text(form.dealerContactEmail),
            internalNotes: text(form.internalNotes),
            auctionSnapshot: prefill?.fields.auctionSnapshot ?? undefined,
            auctionUrl: /^https?:\/\//i.test(auctionUrl.trim()) ? auctionUrl.trim() : undefined,
        };
        setSaving(true);
        try {
            setCreated(await createImportOrder(body));
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się utworzyć zlecenia.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="min-h-screen bg-avtovo-bg py-6 sm:py-8">
            <form onSubmit={submit} className="max-w-3xl mx-auto px-4 space-y-4">
                <Link to="/panel-dealera/zlecenia" className="inline-flex items-center gap-1 text-avtovo-text-secondary hover:text-avtovo-text text-sm">
                    <ChevronLeft size={16} /> Zlecenia importu
                </Link>
                <h1 className="text-2xl font-bold text-avtovo-text">Nowe zlecenie importu</h1>

                <Section title="Pojazd">
                    <div className="space-y-1.5">
                        <span className="text-xs text-avtovo-text-secondary">Wklej link z Copart/IAAI</span>
                        <div className="flex gap-2">
                            <input value={auctionUrl} onChange={e => setAuctionUrl(e.target.value)} placeholder="https://www.copart.com/lot/…"
                                   className={inputClass} />
                            <button type="button" onClick={resolveLink} disabled={resolving || !auctionUrl.trim()}
                                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent disabled:opacity-50 shrink-0">
                                {resolving ? <Loader2 size={15} className="animate-spin" /> : <Link2 size={15} />} Uzupełnij
                            </button>
                        </div>
                        <span className="block text-xs text-avtovo-muted">Zużywa 2 zapytania z limitu Apibara.</span>
                        {resolveError && <p className="text-sm text-red-400">{resolveError}</p>}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <Field label="Aukcja">
                            <select value={form.source} onChange={e => set('source', e.target.value as LotSource)} className={inputClass}>
                                {(Object.keys(SOURCE_LABELS) as LotSource[]).map(s => <option key={s} value={s}>{SOURCE_LABELS[s]}</option>)}
                            </select>
                        </Field>
                        <Field label="Numer lotu"><input value={form.lotNumber} onChange={e => set('lotNumber', e.target.value)} className={inputClass} /></Field>
                        <Field label="VIN"><input value={form.vin} onChange={e => set('vin', e.target.value)} maxLength={20} className={`${inputClass} font-mono uppercase`} /></Field>
                        <Field label="Marka"><input value={form.make} onChange={e => set('make', e.target.value)} className={inputClass} /></Field>
                        <Field label="Model"><input value={form.model} onChange={e => set('model', e.target.value)} className={inputClass} /></Field>
                        <Field label="Rok"><input type="number" min={1900} max={2100} value={form.year} onChange={e => set('year', e.target.value)} className={inputClass} /></Field>
                        <Field label="Data aukcji"><input type="date" value={form.saleDate} onChange={e => set('saleDate', e.target.value)} className={inputClass} /></Field>
                        <Field label="Cena zakupu (USD)" hint={prefill?.suggestedPriceUsd != null
                            ? <>Ostatnia cena z aukcji: ${prefill.suggestedPriceUsd.toLocaleString('en-US')}. <button type="button" className="underline" onClick={() => set('purchasePriceUsd', String(prefill.suggestedPriceUsd))}>Wstaw</button> — potwierdź kwotę z faktury aukcyjnej.</>
                            : 'Kwota z faktury aukcyjnej.'}>
                            <input inputMode="decimal" value={form.purchasePriceUsd} onChange={e => set('purchasePriceUsd', e.target.value)} className={inputClass} />
                        </Field>
                        <Field label="Lokalizacja aukcji"><input value={form.auctionLocation} onChange={e => set('auctionLocation', e.target.value)} className={inputClass} /></Field>
                        <Field label="Dokument (title)"><input value={form.titleType} onChange={e => set('titleType', e.target.value)} className={inputClass} /></Field>
                        <Field label="Title pozwala na eksport"><TriStateSelect value={form.titleExportOk} onChange={v => set('titleExportOk', v)} /></Field>
                        <Field label="Title pozwala na rejestrację"><TriStateSelect value={form.titleRegistrationOk} onChange={v => set('titleRegistrationOk', v)} /></Field>
                    </div>
                    {prefill?.fields.auctionSnapshot && (
                        <p className="text-xs text-avtovo-muted">Dane z aukcji (w tym zdjęcia) zostaną zapisane razem ze zleceniem.</p>
                    )}
                </Section>

                <Section title="Klient">
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <Field label="Imię i nazwisko"><input value={form.clientName} onChange={e => set('clientName', e.target.value)} className={inputClass} /></Field>
                        <Field label="E-mail"><input type="email" value={form.clientEmail} onChange={e => set('clientEmail', e.target.value)} className={inputClass} /></Field>
                        <Field label="Telefon"><input type="tel" value={form.clientPhone} onChange={e => set('clientPhone', e.target.value)} className={inputClass} /></Field>
                    </div>
                    <label className="flex items-center gap-2 text-sm text-avtovo-text">
                        <input type="checkbox" checked={form.notifyClient} onChange={e => set('notifyClient', e.target.checked)} />
                        Powiadamiaj klienta e-mailem o nowych informacjach (bez hasła)
                    </label>
                </Section>

                <Section title="Kontakt dealera (widoczny dla klienta)">
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <Field label="Osoba / firma"><input value={form.dealerContactName} onChange={e => set('dealerContactName', e.target.value)} className={inputClass} /></Field>
                        <Field label="Telefon"><input type="tel" value={form.dealerContactPhone} onChange={e => set('dealerContactPhone', e.target.value)} className={inputClass} /></Field>
                        <Field label="E-mail"><input type="email" value={form.dealerContactEmail} onChange={e => set('dealerContactEmail', e.target.value)} className={inputClass} /></Field>
                    </div>
                </Section>

                <Section title="Notatki wewnętrzne (niewidoczne dla klienta)">
                    <textarea rows={3} value={form.internalNotes} onChange={e => set('internalNotes', e.target.value)} className={inputClass} />
                </Section>

                {error && <p className="text-sm text-red-400">{error}</p>}
                <button type="submit" disabled={saving}
                        className="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-6 py-2.5 rounded-lg text-sm font-medium disabled:opacity-60">
                    {saving && <Loader2 size={16} className="animate-spin" />} Utwórz zlecenie
                </button>
            </form>

            {created && (
                <CredentialsModal
                    credentials={created}
                    carTitle={carTitle(created.order)}
                    title="Zlecenie utworzone — dostęp dla klienta"
                    onClose={() => navigate(`/panel-dealera/zlecenia/${created.order.id}`, { replace: true })}
                />
            )}
        </div>
    );
}
