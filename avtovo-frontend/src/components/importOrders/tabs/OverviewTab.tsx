import { useState, type FormEvent } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { updateImportOrder } from '../../../api/importOrders';
import { Field, Section, TriStateSelect, inputClass } from '../FormField';
import { SOURCE_LABELS } from '../../../constants/importOrders';
import { apiErrorMessage } from '../../../utils/importOrderUi';
import type { ImportOrderDetail, ImportOrderFields, LotSource } from '../../../types/importOrders';

interface Props {
    order: ImportOrderDetail;
    onChange: (order: ImportOrderDetail) => void;
}

type Form = {
    source: LotSource;
    titleExportOk: boolean | null;
    titleRegistrationOk: boolean | null;
    notifyClient: boolean;
} & Record<'lotNumber' | 'vin' | 'make' | 'model' | 'year' | 'saleDate' | 'purchasePriceUsd' | 'auctionLocation' | 'auctionUrl'
    | 'titleType' | 'clientName' | 'clientEmail' | 'clientPhone' | 'dealerContactName' | 'dealerContactPhone'
    | 'dealerContactEmail', string>;

function toForm(o: ImportOrderDetail): Form {
    return {
        source: o.source,
        lotNumber: o.lotNumber ?? '',
        vin: o.vin ?? '',
        make: o.make ?? '',
        model: o.model ?? '',
        year: o.year != null ? String(o.year) : '',
        saleDate: o.saleDate ?? '',
        purchasePriceUsd: o.purchasePriceUsd != null ? String(o.purchasePriceUsd) : '',
        auctionLocation: o.auctionLocation ?? '',
        auctionUrl: o.auctionUrl ?? '',
        titleType: o.titleType ?? '',
        titleExportOk: o.titleExportOk,
        titleRegistrationOk: o.titleRegistrationOk,
        clientName: o.clientName ?? '',
        clientEmail: o.clientEmail ?? '',
        clientPhone: o.clientPhone ?? '',
        notifyClient: o.notifyClient,
        dealerContactName: o.dealerContactName ?? '',
        dealerContactPhone: o.dealerContactPhone ?? '',
        dealerContactEmail: o.dealerContactEmail ?? '',
    };
}

/** Przegląd: car, client, dealer contact and title flags. */
export default function OverviewTab({ order, onChange }: Props) {
    const [form, setForm] = useState<Form>(() => toForm(order));
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
    const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm(f => ({ ...f, [key]: value }));

    const save = async (e: FormEvent) => {
        e.preventDefault();
        const text = (s: string) => s.trim() || null;
        const fields: ImportOrderFields = {
            source: form.source,
            lotNumber: text(form.lotNumber),
            vin: text(form.vin.replace(/\s+/g, '').toUpperCase()),
            make: text(form.make),
            model: text(form.model),
            year: form.year ? Number(form.year) : null,
            saleDate: form.saleDate || null,
            purchasePriceUsd: form.purchasePriceUsd ? Number(form.purchasePriceUsd.replace(',', '.')) : null,
            auctionLocation: text(form.auctionLocation),
            auctionUrl: text(form.auctionUrl),
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
        };
        setSaving(true);
        setMessage(null);
        try {
            const updated = await updateImportOrder(order.id, fields);
            onChange(updated);
            setForm(toForm(updated));
            setMessage({ ok: true, text: 'Zapisano.' });
        } catch (err) {
            setMessage({ ok: false, text: apiErrorMessage(err, 'Nie udało się zapisać.') });
        } finally {
            setSaving(false);
        }
    };

    return (
        <form onSubmit={save} className="space-y-4">
            {(order.titleExportOk === false || order.titleRegistrationOk === false) && (
                <div className="flex gap-2 rounded-xl bg-amber-500/10 border border-amber-500/30 p-4 text-sm text-amber-300">
                    <AlertTriangle size={18} className="shrink-0" />
                    <div>
                        {order.titleExportOk === false && <p>Dokument własności (title) <strong>nie pozwala na eksport</strong> — sprawdź, czy auto może opuścić USA.</p>}
                        {order.titleRegistrationOk === false && <p>Dokument własności (title) <strong>nie pozwala na rejestrację</strong> — uprzedź klienta.</p>}
                    </div>
                </div>
            )}
            {order.warnings.map(w => (
                <div key={w} className="flex gap-2 rounded-xl bg-amber-500/10 border border-amber-500/30 p-3 text-sm text-amber-300">
                    <AlertTriangle size={16} className="shrink-0 mt-0.5" />
                    <p>{w.includes('check digit') ? `Cyfra kontrolna VIN (9. znak) nie zgadza się z wzorem północnoamerykańskim — sprawdź VIN. (${w})` : w}</p>
                </div>
            ))}

            <Section title="Pojazd">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <Field label="Aukcja">
                        <select value={form.source} onChange={e => set('source', e.target.value as LotSource)} className={inputClass}>
                            {(Object.keys(SOURCE_LABELS) as LotSource[]).map(s => <option key={s} value={s}>{SOURCE_LABELS[s]}</option>)}
                        </select>
                    </Field>
                    <Field label="Numer lotu"><input value={form.lotNumber} onChange={e => set('lotNumber', e.target.value)} className={inputClass} /></Field>
                    <Field label="VIN"><input value={form.vin} onChange={e => set('vin', e.target.value)} className={`${inputClass} font-mono uppercase`} /></Field>
                    <Field label="Marka"><input value={form.make} onChange={e => set('make', e.target.value)} className={inputClass} /></Field>
                    <Field label="Model"><input value={form.model} onChange={e => set('model', e.target.value)} className={inputClass} /></Field>
                    <Field label="Rok"><input type="number" value={form.year} onChange={e => set('year', e.target.value)} className={inputClass} /></Field>
                    <Field label="Data aukcji"><input type="date" value={form.saleDate} onChange={e => set('saleDate', e.target.value)} className={inputClass} /></Field>
                    <Field label="Cena zakupu (USD)"><input inputMode="decimal" value={form.purchasePriceUsd} onChange={e => set('purchasePriceUsd', e.target.value)} className={inputClass} /></Field>
                    <Field label="Lokalizacja aukcji"><input value={form.auctionLocation} onChange={e => set('auctionLocation', e.target.value)} className={inputClass} /></Field>
                    <Field label="Link do aukcji"><input type="url" value={form.auctionUrl} onChange={e => set('auctionUrl', e.target.value)} placeholder="https://www.copart.com/lot/…" className={inputClass} /></Field>
                    <Field label="Dokument (title)"><input value={form.titleType} onChange={e => set('titleType', e.target.value)} className={inputClass} /></Field>
                    <Field label="Title pozwala na eksport"><TriStateSelect value={form.titleExportOk} onChange={v => set('titleExportOk', v)} /></Field>
                    <Field label="Title pozwala na rejestrację"><TriStateSelect value={form.titleRegistrationOk} onChange={v => set('titleRegistrationOk', v)} /></Field>
                </div>
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

            <div className="flex items-center gap-3">
                <button type="submit" disabled={saving}
                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
                    {saving && <Loader2 size={15} className="animate-spin" />} Zapisz
                </button>
                {message && <span className={`text-sm ${message.ok ? 'text-emerald-400' : 'text-red-400'}`}>{message.text}</span>}
            </div>
        </form>
    );
}
