import { useEffect, useMemo, useState } from 'react';
import { Link, NavLink, useParams } from 'react-router-dom';
import axios from 'axios';
import { Building2, Clock, Pencil, ExternalLink, Facebook, Globe, Instagram, Loader2, MapPin, Navigation, Phone, Youtube } from 'lucide-react';
import { getDealerPage, getDealerPhone, getSellerListings } from '../../api/dealers';
import { useAuth } from '../../context/AuthContext';
import CarCard from '../../components/CarCard';
import PhotoGallery from '../../components/PhotoGallery';
import type { CarListing, Page } from '../../types';
import type { DayHours, DealerPageView } from '../../types/dealers';
import { offersLabel } from '../../utils/dealers';

export type DealerTab = 'about' | 'offers' | 'contact';

const DAY_NAMES = ['Poniedziałek', 'Wtorek', 'Środa', 'Czwartek', 'Piątek', 'Sobota', 'Niedziela'];

const SORT_OPTIONS = [
    { value: 'createdAt,desc', label: 'Najnowsze' },
    { value: 'price,asc', label: 'Cena: od najniższej' },
    { value: 'price,desc', label: 'Cena: od najwyższej' },
    { value: 'mileage,asc', label: 'Przebieg: od najmniejszego' },
    { value: 'year,desc', label: 'Rok: najnowszy' },
];

function hasAbout(d: DealerPageView): boolean {
    return !!(d.headline || d.description || d.galleryUrls.length || d.services.length || d.team.length
        || d.website || d.facebookUrl || d.instagramUrl || d.youtubeUrl);
}

function fullAddress(d: DealerPageView): string | null {
    const cityLine = [d.postalCode, d.city].filter(Boolean).join(' ');
    const parts = [d.street, cityLine].filter(Boolean);
    return parts.length ? `${parts.join(', ')}, Polska` : null;
}

/** ISO day of week for today: 1 = Monday … 7 = Sunday. */
function isoToday(): number {
    const d = new Date().getDay();
    return d === 0 ? 7 : d;
}

/**
 * Public dealer page, modeled on otomoto's: /dealer/:slug ("O nas", or the offers when the dealer wrote
 * nothing about themselves), /dealer/:slug/oferta and /dealer/:slug/kontakt.
 */
export default function DealerPage({ tab }: { tab?: DealerTab }) {
    const { slug = '' } = useParams();
    const { user } = useAuth();
    // Keyed by slug, so moving to another dealer shows the spinner without resetting state in the effect
    const [loaded, setLoaded] = useState<{ slug: string; dealer: DealerPageView | null; failed: boolean } | null>(null);
    const [offerCount, setOfferCount] = useState<number | null>(null);

    useEffect(() => {
        let cancelled = false;
        getDealerPage(slug)
            .then(d => {
                if (cancelled) return;
                setLoaded({ slug, dealer: d, failed: false });
                if (d) document.title = `${d.companyName} — avtovo`;
            })
            .catch(err => {
                console.error(err);
                if (!cancelled) setLoaded({ slug, dealer: null, failed: true });
            });
        return () => {
            cancelled = true;
        };
    }, [slug]);

    const current = loaded?.slug === slug ? loaded : null;
    const dealer = current?.dealer ?? null;
    const state = !current ? 'loading' : current.failed ? 'error' : dealer ? 'ready' : 'notFound';

    useEffect(() => {
        if (!dealer) return;
        getSellerListings(dealer.userId, 0, 'createdAt,desc', 1)
            .then(p => setOfferCount(p.totalElements))
            .catch(() => setOfferCount(null));
    }, [dealer]);

    if (state === 'loading') {
        return <div className="min-h-screen bg-avtovo-bg flex items-center justify-center"><Loader2 className="animate-spin text-avtovo-accent" /></div>;
    }
    if (state !== 'ready' || !dealer) {
        return (
            <div className="min-h-screen bg-avtovo-bg flex flex-col items-center justify-center gap-3 px-4 text-center">
                <Building2 size={40} className="text-avtovo-muted" />
                <p className="text-avtovo-text">{state === 'notFound' ? 'Nie znaleziono strony tego dealera.' : 'Nie udało się wczytać strony dealera.'}</p>
                <Link to="/" className="text-avtovo-accent hover:underline text-sm">Wróć na stronę główną</Link>
            </div>
        );
    }

    const about = hasAbout(dealer);
    const active: DealerTab = tab ?? (about ? 'about' : 'offers');
    const base = `/dealer/${dealer.slug}`;
    const tabs: { id: DealerTab; label: string; to: string }[] = [
        ...(about ? [{ id: 'about' as const, label: 'O nas', to: base }] : []),
        { id: 'offers', label: 'Oferta', to: about ? `${base}/oferta` : base },
        { id: 'contact', label: 'Kontakt', to: `${base}/kontakt` },
    ];

    return (
        <div className="min-h-screen bg-avtovo-bg pb-12">
            {/* Header: banner, logo, name, location, "Na avtovo od" */}
            <div className="bg-avtovo-card border-b border-avtovo-border">
                <div className="h-36 sm:h-56 bg-gradient-to-r from-avtovo-accent/30 via-avtovo-card to-avtovo-bg overflow-hidden">
                    {dealer.bannerUrl && <img src={dealer.bannerUrl} alt={`${dealer.companyName} — baner`} className="w-full h-full object-cover" />}
                </div>
                <div className="max-w-6xl mx-auto px-4">
                    <div className="flex flex-col sm:flex-row sm:items-end gap-4 -mt-10 sm:-mt-12 pb-4">
                        <div className="w-20 h-20 sm:w-28 sm:h-28 rounded-xl border-4 border-avtovo-card bg-avtovo-bg overflow-hidden flex items-center justify-center shrink-0">
                            {dealer.logoUrl
                                ? <img src={dealer.logoUrl} alt={`${dealer.companyName} — logo`} className="w-full h-full object-contain bg-white" />
                                : <Building2 size={36} className="text-avtovo-muted" />}
                        </div>
                        <div className="min-w-0 space-y-1">
                            <h1 className="text-2xl sm:text-3xl font-bold text-avtovo-text break-words">{dealer.companyName}</h1>
                            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-avtovo-text-secondary">
                                {dealer.city && <span className="inline-flex items-center gap-1"><MapPin size={14} /> {dealer.city}</span>}
                                {dealer.memberSince && <span>Na avtovo od {new Date(dealer.memberSince).getFullYear()}</span>}
                                {offerCount != null && <span>{offersLabel(offerCount)}</span>}
                            </p>
                        </div>
                        {user?.id === dealer.userId && (
                            <Link to="/panel-dealera/strona"
                                  className="sm:ml-auto inline-flex items-center gap-1.5 self-start sm:self-end px-3 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent">
                                <Pencil size={14} /> Edytuj stronę
                            </Link>
                        )}
                    </div>
                    <nav className="flex gap-1 overflow-x-auto -mb-px">
                        {tabs.map(t => (
                            <NavLink key={t.id} to={t.to} end
                                     className={`px-4 py-3 text-sm font-medium border-b-2 whitespace-nowrap transition-colors ${active === t.id
                                         ? 'border-avtovo-accent text-avtovo-text' : 'border-transparent text-avtovo-text-secondary hover:text-avtovo-text'}`}>
                                {t.label}
                            </NavLink>
                        ))}
                    </nav>
                </div>
            </div>

            <div className="max-w-6xl mx-auto px-4 pt-6">
                {active === 'about' && <AboutTab dealer={dealer} offersHref={`${base}/oferta`} contactHref={`${base}/kontakt`} />}
                {active === 'offers' && <OffersTab dealer={dealer} />}
                {active === 'contact' && <ContactTab dealer={dealer} offersHref={about ? `${base}/oferta` : base} />}
            </div>
        </div>
    );
}

function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
    return (
        <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-5 sm:p-6 space-y-4">
            <div>
                <h2 className="text-lg font-semibold text-avtovo-text">{title}</h2>
                {subtitle && <p className="text-sm text-avtovo-text-secondary">{subtitle}</p>}
            </div>
            {children}
        </section>
    );
}

function AboutTab({ dealer, offersHref, contactHref }: { dealer: DealerPageView; offersHref: string; contactHref: string }) {
    const photos = useMemo(() => dealer.galleryUrls.map(u => ({ thumb: u, large: u })), [dealer.galleryUrls]);
    const social = [
        { url: dealer.facebookUrl, label: 'Facebook', icon: Facebook },
        { url: dealer.instagramUrl, label: 'Instagram', icon: Instagram },
        { url: dealer.youtubeUrl, label: 'YouTube', icon: Youtube },
        { url: dealer.website, label: 'Strona internetowa', icon: Globe },
    ].filter(s => s.url);

    return (
        <div className="space-y-5">
            <Section title="Dlaczego my?" subtitle={`Dowiedz się więcej o ${dealer.companyName}`}>
                {photos.length > 0 && <PhotoGallery photos={photos} alt={dealer.companyName} />}
                {dealer.headline && <h3 className="text-xl font-semibold text-avtovo-text">{dealer.headline}</h3>}
                {dealer.description && <p className="text-avtovo-text whitespace-pre-line leading-relaxed">{dealer.description}</p>}
                {social.length > 0 && (
                    <div className="flex flex-wrap gap-2 pt-1">
                        {social.map(s => (
                            <a key={s.label} href={s.url!} target="_blank" rel="noopener noreferrer nofollow"
                               className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent">
                                <s.icon size={15} /> {s.label}
                            </a>
                        ))}
                    </div>
                )}
            </Section>

            {dealer.team.length > 0 && (
                <Section title="Zespół" subtitle="Poznaj naszych specjalistów">
                    <p className="text-xs text-avtovo-text-secondary">{dealer.team.length} {dealer.team.length === 1 ? 'członek' : 'członków'}</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                        {dealer.team.map((m, i) => (
                            <div key={i} className="flex items-center gap-3 border border-avtovo-border rounded-lg p-3">
                                <div className="w-10 h-10 rounded-full bg-avtovo-accent/15 text-avtovo-accent flex items-center justify-center font-semibold">
                                    {m.name.charAt(0).toUpperCase()}
                                </div>
                                <div className="min-w-0">
                                    <p className="text-avtovo-text font-medium truncate">{m.name}</p>
                                    {m.role && <p className="text-xs text-avtovo-text-secondary truncate">{m.role}</p>}
                                </div>
                            </div>
                        ))}
                    </div>
                    <Link to={contactHref} className="inline-block text-sm text-avtovo-accent hover:underline">Kontakt</Link>
                </Section>
            )}

            {dealer.services.length > 0 && (
                <Section title="Usługi" subtitle="Sprawdź, co możemy Ci zaoferować">
                    <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        {dealer.services.map((s, i) => (
                            <li key={i} className="flex items-start gap-2 text-avtovo-text">
                                <span className="mt-2 w-1.5 h-1.5 rounded-full bg-avtovo-accent shrink-0" /> {s}
                            </li>
                        ))}
                    </ul>
                </Section>
            )}

            <div className="text-center">
                <Link to={offersHref} className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2.5 rounded-lg text-sm font-medium">
                    Zobacz wszystkie oferty
                </Link>
            </div>
        </div>
    );
}

function OffersTab({ dealer }: { dealer: DealerPageView }) {
    const [sort, setSort] = useState('createdAt,desc');
    const [page, setPage] = useState(0);
    const [data, setData] = useState<Page<CarListing> | null>(null);
    const [failedKey, setFailedKey] = useState<string | null>(null);
    const key = `${dealer.userId}|${page}|${sort}`;
    const error = failedKey === key;

    useEffect(() => {
        getSellerListings(dealer.userId, page, sort)
            .then(setData)
            .catch(err => {
                console.error(err);
                setFailedKey(`${dealer.userId}|${page}|${sort}`);
            });
    }, [dealer.userId, page, sort]);

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-avtovo-text font-medium">{data ? offersLabel(data.totalElements) : ' '}</p>
                <select value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}
                        className="bg-avtovo-card border border-avtovo-border text-avtovo-text rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent">
                    {SORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
            </div>
            {error && <p className="text-sm text-red-400">Nie udało się wczytać ogłoszeń.</p>}
            {!data && !error && <Loader2 className="animate-spin text-avtovo-accent mx-auto" />}
            {data && data.content.length === 0 && (
                <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-8 text-center text-avtovo-text-secondary">
                    Ten dealer nie ma teraz aktywnych ogłoszeń.
                </div>
            )}
            {data && data.content.length > 0 && (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {data.content.map(car => <CarCard key={car.id} car={car} />)}
                </div>
            )}
            {data && data.totalPages > 1 && (
                <div className="flex items-center justify-center gap-3 text-sm">
                    <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                            className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text disabled:opacity-40">Poprzednia</button>
                    <span className="text-avtovo-text-secondary">{page + 1} / {data.totalPages}</span>
                    <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}
                            className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text disabled:opacity-40">Następna</button>
                </div>
            )}
        </div>
    );
}

function todayLabel(hours: DayHours[]): string {
    const today = hours.find(h => h.day === isoToday());
    return today ? `Otwarte dziś od ${today.open} do ${today.close}` : 'Dziś zamknięte';
}

function ContactTab({ dealer, offersHref }: { dealer: DealerPageView; offersHref: string }) {
    const [phone, setPhone] = useState<string | null>(null);
    const [phoneState, setPhoneState] = useState<'idle' | 'loading' | 'none' | 'limited' | 'error'>('idle');
    const address = fullAddress(dealer);

    const showPhone = async () => {
        setPhoneState('loading');
        try {
            const p = await getDealerPhone(dealer.slug);
            setPhone(p);
            setPhoneState(p ? 'idle' : 'none');
        } catch (err) {
            setPhoneState(axios.isAxiosError(err) && err.response?.status === 429 ? 'limited' : 'error');
        }
    };

    return (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <Section title="Kontakt" subtitle={dealer.companyName}>
                {address && (
                    <div className="flex items-start gap-2 text-avtovo-text">
                        <MapPin size={18} className="mt-0.5 text-avtovo-text-secondary shrink-0" />
                        <div>
                            <p>{address}</p>
                            <a href={`https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}`}
                               target="_blank" rel="noopener noreferrer"
                               className="inline-flex items-center gap-1 text-sm text-avtovo-accent hover:underline mt-1">
                                <Navigation size={14} /> Pokaż trasę
                            </a>
                        </div>
                    </div>
                )}
                {dealer.hasPhone && (
                    <div className="flex items-center gap-2">
                        <Phone size={18} className="text-avtovo-text-secondary" />
                        {phone ? (
                            <a href={`tel:${phone.replace(/\s/g, '')}`} className="text-avtovo-text font-medium hover:text-avtovo-accent">{phone}</a>
                        ) : (
                            <button onClick={showPhone} disabled={phoneState === 'loading'}
                                    className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-avtovo-accent text-avtovo-accent hover:bg-avtovo-accent/10 text-sm font-medium disabled:opacity-50">
                                {phoneState === 'loading' && <Loader2 size={14} className="animate-spin" />} Wyświetl numer
                            </button>
                        )}
                    </div>
                )}
                {phoneState === 'limited' && <p className="text-sm text-amber-400">Zbyt wiele prób. Spróbuj ponownie za chwilę.</p>}
                {phoneState === 'none' && <p className="text-sm text-avtovo-text-secondary">Numer telefonu jest niedostępny.</p>}
                {phoneState === 'error' && <p className="text-sm text-red-400">Nie udało się pobrać numeru.</p>}
                {dealer.website && (
                    <a href={dealer.website} target="_blank" rel="noopener noreferrer nofollow"
                       className="flex items-center gap-2 text-avtovo-text hover:text-avtovo-accent break-all">
                        <Globe size={18} className="text-avtovo-text-secondary shrink-0" /> {dealer.website.replace(/^https?:\/\//, '')}
                        <ExternalLink size={13} />
                    </a>
                )}
                <Link to={offersHref} className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-4 py-2 rounded-lg text-sm font-medium">
                    Zobacz wszystkie oferty
                </Link>
            </Section>

            <Section title="Godziny otwarcia" subtitle={dealer.openingHours.length ? todayLabel(dealer.openingHours) : undefined}>
                {dealer.openingHours.length === 0 ? (
                    <p className="text-sm text-avtovo-text-secondary">Dealer nie podał godzin otwarcia.</p>
                ) : (
                    <ul className="divide-y divide-avtovo-border/60">
                        {DAY_NAMES.map((name, i) => {
                            const h = dealer.openingHours.find(x => x.day === i + 1);
                            const today = isoToday() === i + 1;
                            return (
                                <li key={name} className={`flex justify-between py-2 text-sm ${today ? 'text-avtovo-text font-medium' : 'text-avtovo-text-secondary'}`}>
                                    <span className="inline-flex items-center gap-1.5">{today && <Clock size={13} />} {name}</span>
                                    <span>{h ? `${h.open}-${h.close}` : 'Zamknięte'}</span>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </Section>
        </div>
    );
}
