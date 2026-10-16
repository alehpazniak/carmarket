import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { Building2, ExternalLink, ImagePlus, Loader2, Plus, Trash2, X } from 'lucide-react';
import { deleteDealerImage, getMyDealerPage, saveMyDealerPage, uploadDealerImage } from '../../api/dealers';
import { getMyProfile } from '../../api/users';
import { resizePhoto } from '../../utils/imageResize';
import { slugify, toDealerPageRequest } from '../../utils/dealers';
import type { DayHours, DealerPageRequest, TeamMember } from '../../types/dealers';

const DAY_NAMES = ['Poniedziałek', 'Wtorek', 'Środa', 'Czwartek', 'Piątek', 'Sobota', 'Niedziela'];
const MAX_GALLERY = 12;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

const inputClass = 'w-full bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text placeholder:text-avtovo-muted focus:outline-none focus:border-avtovo-accent';

const EMPTY: DealerPageRequest = {
    slug: '', companyName: '', headline: null, description: null, city: null, street: null, postalCode: null,
    phone: null, website: null, facebookUrl: null, instagramUrl: null, youtubeUrl: null, logoUrl: null,
    bannerUrl: null, galleryUrls: [], openingHours: [], services: [], team: [], published: false,
};

function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: React.ReactNode }) {
    return (
        <label className="block space-y-1">
            <span className="text-xs text-avtovo-text-secondary">{label}</span>
            {children}
            {error ? <span className="block text-xs text-red-400">{error}</span>
                : hint && <span className="block text-xs text-avtovo-muted">{hint}</span>}
        </label>
    );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
    return (
        <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-5 space-y-4">
            <h2 className="font-semibold text-avtovo-text">{title}</h2>
            {children}
        </section>
    );
}

/** /panel-dealera/strona — the dealer edits their public page /dealer/:slug. */
export default function DealerPageEditor() {
    const [form, setForm] = useState<DealerPageRequest>(EMPTY);
    const [savedSlug, setSavedSlug] = useState<string | null>(null);
    const [savedPublished, setSavedPublished] = useState(false);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [uploading, setUploading] = useState<'logo' | 'banner' | 'gallery' | null>(null);
    // Images to delete from S3 once the page no longer uses them: everything saved or uploaded so far
    const knownImages = useRef(new Set<string>());

    useEffect(() => {
        Promise.all([getMyDealerPage(), getMyProfile().catch(() => null)])
            .then(([page, profile]) => {
                if (page) {
                    setForm(toDealerPageRequest(page));
                    setSavedSlug(page.slug);
                    setSavedPublished(page.published);
                    [page.logoUrl, page.bannerUrl, ...page.galleryUrls].forEach(u => u && knownImages.current.add(u));
                } else {
                    const name = profile?.displayName ?? '';
                    setForm({ ...EMPTY, companyName: name, slug: slugify(name), city: profile?.city ?? null });
                }
            })
            .catch(err => {
                console.error(err);
                setLoadError(axios.isAxiosError(err) && err.response?.status === 403
                    ? 'Strona dealera jest dostępna tylko dla kont dealerów.'
                    : 'Nie udało się wczytać strony dealera.');
            })
            .finally(() => setLoading(false));
    }, []);

    const set = <K extends keyof DealerPageRequest>(key: K, value: DealerPageRequest[K]) =>
        setForm(f => ({ ...f, [key]: value }));
    const text = (key: keyof DealerPageRequest) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
        set(key, (e.target.value === '' ? null : e.target.value) as never);

    const upload = async (target: 'logo' | 'banner' | 'gallery', files: FileList | null) => {
        if (!files?.length) return;
        setUploading(target);
        setMessage(null);
        try {
            const picked = target === 'gallery'
                ? Array.from(files).slice(0, MAX_GALLERY - form.galleryUrls.length)
                : [files[0]];
            const urls: string[] = [];
            for (const original of picked) {
                // Logos keep their format (PNG transparency) unless they're too big
                const file = target === 'logo' && original.size <= MAX_IMAGE_BYTES ? original
                    : await resizePhoto(original, target === 'logo' ? 800 : 2000);
                const url = await uploadDealerImage(file);
                knownImages.current.add(url);
                urls.push(url);
            }
            if (target === 'logo') set('logoUrl', urls[0]);
            else if (target === 'banner') set('bannerUrl', urls[0]);
            else setForm(f => ({ ...f, galleryUrls: [...f.galleryUrls, ...urls].slice(0, MAX_GALLERY) }));
        } catch (err) {
            console.error(err);
            setMessage({ ok: false, text: 'Nie udało się wysłać zdjęcia (JPEG, PNG lub WEBP, do 5 MB).' });
        } finally {
            setUploading(null);
        }
    };

    const setHours = (day: number, patch: Partial<DayHours> | null) => {
        setForm(f => {
            const others = f.openingHours.filter(h => h.day !== day);
            if (patch === null) return { ...f, openingHours: others };
            const current = f.openingHours.find(h => h.day === day) ?? { day, open: '09:00', close: '17:00' };
            return { ...f, openingHours: [...others, { ...current, ...patch }].sort((a, b) => a.day - b.day) };
        });
    };

    /** publish: true = public, false = draft; Enter in a field keeps the current state. */
    const save = async (publish: boolean) => {
        setSaving(true);
        setMessage(null);
        setFieldErrors({});
        try {
            const body: DealerPageRequest = {
                ...form,
                published: publish,
                services: form.services.map(s => s.trim()).filter(Boolean),
                team: form.team.filter(m => m.name.trim()).map(m => ({ name: m.name.trim(), role: m.role?.trim() || null })),
            };
            const saved = await saveMyDealerPage(body);
            setForm(toDealerPageRequest(saved));
            setSavedSlug(saved.slug);
            setSavedPublished(saved.published);
            setMessage({ ok: true, text: saved.published
                ? `Opublikowano. Strona jest widoczna pod adresem /dealer/${saved.slug}.`
                : 'Zapisano jako szkic. Strona nie jest widoczna publicznie — kliknij „Opublikuj stronę”.' });
            // Drop images the saved page no longer uses
            const used = new Set([saved.logoUrl, saved.bannerUrl, ...saved.galleryUrls].filter(Boolean) as string[]);
            for (const url of Array.from(knownImages.current)) {
                if (!used.has(url)) {
                    knownImages.current.delete(url);
                    deleteDealerImage(url).catch(err => console.warn('Could not delete image', err));
                }
            }
        } catch (err) {
            if (axios.isAxiosError(err) && err.response?.status === 400 && err.response.data && !err.response.data.message) {
                setFieldErrors(err.response.data as Record<string, string>);
                setMessage({ ok: false, text: 'Popraw zaznaczone pola.' });
            } else {
                const msg = axios.isAxiosError(err) ? (err.response?.data as { message?: string } | undefined)?.message : undefined;
                setMessage({ ok: false, text: msg ?? 'Nie udało się zapisać strony.' });
            }
        } finally {
            setSaving(false);
        }
    };

    const submit = (e: FormEvent) => {
        e.preventDefault();
        save(savedPublished);
    };

    if (loading) return <div className="min-h-screen bg-avtovo-bg flex items-center justify-center"><Loader2 className="animate-spin text-avtovo-accent" /></div>;
    if (loadError) return <div className="min-h-screen bg-avtovo-bg flex items-center justify-center px-4 text-avtovo-text">{loadError}</div>;

    const publicUrl = `${window.location.origin}/dealer/${form.slug || '…'}`;

    return (
        <div className="min-h-screen bg-avtovo-bg py-6 sm:py-8">
            <form onSubmit={submit} className="max-w-3xl mx-auto px-4 space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                        <Building2 size={22} className="text-avtovo-accent" />
                        <h1 className="text-2xl font-bold text-avtovo-text">Strona dealera</h1>
                    </div>
                    {savedSlug && savedPublished && (
                        <Link to={`/dealer/${savedSlug}`} target="_blank" className="inline-flex items-center gap-1.5 text-sm text-avtovo-accent hover:underline">
                            <ExternalLink size={14} /> Zobacz stronę
                        </Link>
                    )}
                </div>
                <p className="text-sm text-avtovo-text-secondary">
                    Publiczna wizytówka Twojej firmy z zakładkami „O nas”, „Oferta” (Twoje aktywne ogłoszenia) i „Kontakt”.
                </p>

                <Section title="Adres i publikacja">
                    <Field label="Adres strony" error={fieldErrors.slug} hint={publicUrl}>
                        <input value={form.slug} onChange={e => set('slug', e.target.value.toLowerCase())} required maxLength={40} className={inputClass} />
                    </Field>
                    <p className="text-sm">
                        Status:{' '}
                        {savedPublished
                            ? <span className="text-green-400 font-medium">opublikowana — widoczna dla wszystkich</span>
                            : <span className="text-amber-400 font-medium">szkic — niewidoczna publicznie</span>}
                    </p>
                </Section>

                <Section title="O firmie">
                    <Field label="Nazwa firmy" error={fieldErrors.companyName}>
                        <input value={form.companyName} onChange={e => set('companyName', e.target.value)} required maxLength={120} className={inputClass} />
                    </Field>
                    <Field label="Nagłówek sekcji „O nas”" error={fieldErrors.headline} hint="Np. „Sprawdzona firma od 10 lat na rynku importu z USA”">
                        <input value={form.headline ?? ''} onChange={text('headline')} maxLength={150} className={inputClass} />
                    </Field>
                    <Field label="Opis" error={fieldErrors.description}>
                        <textarea value={form.description ?? ''} onChange={text('description')} rows={6} maxLength={5000} className={inputClass} />
                    </Field>
                </Section>

                <Section title="Logo i baner">
                    <div className="grid grid-cols-1 sm:grid-cols-[auto_1fr] gap-4 items-start">
                        <ImageSlot label="Logo" url={form.logoUrl} busy={uploading === 'logo'} className="w-28 h-28"
                                   onPick={files => upload('logo', files)} onRemove={() => set('logoUrl', null)} contain />
                        <ImageSlot label="Baner (szeroki, np. 1600×400)" url={form.bannerUrl} busy={uploading === 'banner'} className="w-full h-28"
                                   onPick={files => upload('banner', files)} onRemove={() => set('bannerUrl', null)} />
                    </div>
                </Section>

                <Section title={`Galeria „Dlaczego my?” (${form.galleryUrls.length}/${MAX_GALLERY})`}>
                    <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
                        {form.galleryUrls.map(url => (
                            <div key={url} className="relative aspect-[4/3] rounded-lg overflow-hidden border border-avtovo-border">
                                <img src={url} alt="" className="w-full h-full object-cover" />
                                <button type="button" onClick={() => set('galleryUrls', form.galleryUrls.filter(u => u !== url))}
                                        className="absolute top-1 right-1 bg-black/60 text-white rounded-full p-1" aria-label="Usuń zdjęcie">
                                    <X size={14} />
                                </button>
                            </div>
                        ))}
                        {form.galleryUrls.length < MAX_GALLERY && (
                            <label className="aspect-[4/3] rounded-lg border border-dashed border-avtovo-border flex flex-col items-center justify-center gap-1 text-xs text-avtovo-text-secondary cursor-pointer hover:border-avtovo-accent">
                                {uploading === 'gallery' ? <Loader2 size={18} className="animate-spin" /> : <ImagePlus size={18} />}
                                Dodaj zdjęcia
                                <input type="file" multiple accept="image/jpeg,image/png,image/webp" className="hidden"
                                       onChange={e => { upload('gallery', e.target.files); e.target.value = ''; }} />
                            </label>
                        )}
                    </div>
                </Section>

                <Section title="Kontakt">
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <div className="sm:col-span-3">
                            <Field label="Ulica i numer" error={fieldErrors.street}>
                                <input value={form.street ?? ''} onChange={text('street')} maxLength={150} className={inputClass} />
                            </Field>
                        </div>
                        <Field label="Kod pocztowy" error={fieldErrors.postalCode}>
                            <input value={form.postalCode ?? ''} onChange={text('postalCode')} maxLength={10} placeholder="00-000" className={inputClass} />
                        </Field>
                        <div className="sm:col-span-2">
                            <Field label="Miasto" error={fieldErrors.city}>
                                <input value={form.city ?? ''} onChange={text('city')} maxLength={80} className={inputClass} />
                            </Field>
                        </div>
                        <Field label="Telefon" error={fieldErrors.phone} hint="Pokazywany po kliknięciu „Wyświetl numer”">
                            <input type="tel" value={form.phone ?? ''} onChange={text('phone')} maxLength={30} className={inputClass} />
                        </Field>
                        <div className="sm:col-span-2">
                            <Field label="Strona internetowa" error={fieldErrors.website}>
                                <input type="url" value={form.website ?? ''} onChange={text('website')} placeholder="https://" className={inputClass} />
                            </Field>
                        </div>
                        <Field label="Facebook" error={fieldErrors.facebookUrl}>
                            <input type="url" value={form.facebookUrl ?? ''} onChange={text('facebookUrl')} placeholder="https://facebook.com/…" className={inputClass} />
                        </Field>
                        <Field label="Instagram" error={fieldErrors.instagramUrl}>
                            <input type="url" value={form.instagramUrl ?? ''} onChange={text('instagramUrl')} placeholder="https://instagram.com/…" className={inputClass} />
                        </Field>
                        <Field label="YouTube" error={fieldErrors.youtubeUrl}>
                            <input type="url" value={form.youtubeUrl ?? ''} onChange={text('youtubeUrl')} placeholder="https://youtube.com/…" className={inputClass} />
                        </Field>
                    </div>
                </Section>

                <Section title="Godziny otwarcia">
                    <div className="space-y-2">
                        {DAY_NAMES.map((name, i) => {
                            const day = i + 1;
                            const h = form.openingHours.find(x => x.day === day);
                            return (
                                <div key={day} className="flex flex-wrap items-center gap-3 text-sm">
                                    <label className="flex items-center gap-2 w-36 text-avtovo-text">
                                        <input type="checkbox" checked={!!h} onChange={e => setHours(day, e.target.checked ? {} : null)} className="accent-avtovo-accent" />
                                        {name}
                                    </label>
                                    {h ? (
                                        <>
                                            <input type="time" value={h.open} onChange={e => setHours(day, { open: e.target.value })} className={`${inputClass} w-28`} />
                                            <span className="text-avtovo-text-secondary">–</span>
                                            <input type="time" value={h.close} onChange={e => setHours(day, { close: e.target.value })} className={`${inputClass} w-28`} />
                                        </>
                                    ) : <span className="text-avtovo-muted">Zamknięte</span>}
                                </div>
                            );
                        })}
                    </div>
                </Section>

                <Section title="Usługi">
                    <ListEditor items={form.services} max={20} placeholder="Np. Import aut z USA i Kanady"
                                onChange={services => set('services', services)} />
                </Section>

                <Section title="Zespół">
                    <div className="space-y-2">
                        {form.team.map((m, i) => (
                            <div key={i} className="flex gap-2">
                                <input value={m.name} placeholder="Imię" maxLength={80}
                                       onChange={e => set('team', form.team.map((x, j) => j === i ? { ...x, name: e.target.value } : x))} className={inputClass} />
                                <input value={m.role ?? ''} placeholder="Stanowisko, np. Opiekun Klienta" maxLength={80}
                                       onChange={e => set('team', form.team.map((x, j) => j === i ? { ...x, role: e.target.value } : x))} className={inputClass} />
                                <button type="button" onClick={() => set('team', form.team.filter((_, j) => j !== i))}
                                        className="p-2 text-avtovo-text-secondary hover:text-red-400" aria-label="Usuń osobę"><Trash2 size={16} /></button>
                            </div>
                        ))}
                        {form.team.length < 20 && (
                            <button type="button" onClick={() => set('team', [...form.team, { name: '', role: null } as TeamMember])}
                                    className="inline-flex items-center gap-1.5 text-sm text-avtovo-accent hover:underline"><Plus size={15} /> Dodaj osobę</button>
                        )}
                    </div>
                </Section>

                {message && <p className={`text-sm ${message.ok ? 'text-green-400' : 'text-red-400'}`}>{message.text}</p>}
                <div className="flex flex-wrap justify-end gap-2">
                    {savedPublished ? (
                        <>
                            <button type="button" onClick={() => save(false)} disabled={saving || uploading !== null}
                                    className="px-4 py-2.5 rounded-lg border border-avtovo-border text-sm text-avtovo-text-secondary hover:text-avtovo-text disabled:opacity-50">
                                Ukryj stronę
                            </button>
                            <button type="button" onClick={() => save(true)} disabled={saving || uploading !== null}
                                    className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2.5 rounded-lg text-sm font-medium disabled:opacity-50">
                                {saving && <Loader2 size={15} className="animate-spin" />} Zapisz zmiany
                            </button>
                        </>
                    ) : (
                        <>
                            <button type="button" onClick={() => save(false)} disabled={saving || uploading !== null}
                                    className="px-4 py-2.5 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent disabled:opacity-50">
                                Zapisz jako szkic
                            </button>
                            <button type="button" onClick={() => save(true)} disabled={saving || uploading !== null}
                                    className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2.5 rounded-lg text-sm font-medium disabled:opacity-50">
                                {saving && <Loader2 size={15} className="animate-spin" />} Opublikuj stronę
                            </button>
                        </>
                    )}
                </div>
            </form>
        </div>
    );
}

function ImageSlot({ label, url, busy, className, contain, onPick, onRemove }: {
    label: string; url: string | null; busy: boolean; className: string; contain?: boolean;
    onPick: (files: FileList | null) => void; onRemove: () => void;
}) {
    return (
        <div className="space-y-1">
            <span className="text-xs text-avtovo-text-secondary">{label}</span>
            <div className={`relative rounded-lg overflow-hidden border border-dashed border-avtovo-border ${className}`}>
                {url ? (
                    <>
                        <img src={url} alt={label} className={`w-full h-full ${contain ? 'object-contain bg-white' : 'object-cover'}`} />
                        <button type="button" onClick={onRemove} className="absolute top-1 right-1 bg-black/60 text-white rounded-full p-1" aria-label={`Usuń: ${label}`}>
                            <X size={14} />
                        </button>
                    </>
                ) : (
                    <label className="w-full h-full flex flex-col items-center justify-center gap-1 text-xs text-avtovo-text-secondary cursor-pointer hover:text-avtovo-text">
                        {busy ? <Loader2 size={18} className="animate-spin" /> : <ImagePlus size={18} />}
                        Wybierz plik
                        <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden"
                               onChange={e => { onPick(e.target.files); e.target.value = ''; }} />
                    </label>
                )}
            </div>
        </div>
    );
}

function ListEditor({ items, max, placeholder, onChange }: {
    items: string[]; max: number; placeholder: string; onChange: (items: string[]) => void;
}) {
    return (
        <div className="space-y-2">
            {items.map((item, i) => (
                <div key={i} className="flex gap-2">
                    <input value={item} placeholder={placeholder} maxLength={120}
                           onChange={e => onChange(items.map((x, j) => j === i ? e.target.value : x))} className={inputClass} />
                    <button type="button" onClick={() => onChange(items.filter((_, j) => j !== i))}
                            className="p-2 text-avtovo-text-secondary hover:text-red-400" aria-label="Usuń"><Trash2 size={16} /></button>
                </div>
            ))}
            {items.length < max && (
                <button type="button" onClick={() => onChange([...items, ''])}
                        className="inline-flex items-center gap-1.5 text-sm text-avtovo-accent hover:underline"><Plus size={15} /> Dodaj</button>
            )}
        </div>
    );
}
