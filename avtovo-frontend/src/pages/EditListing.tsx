import {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {deleteCarImage, getCar, setPrimaryCarImage, updateCar, uploadCarImages} from '../api/cars';
import {useAuth} from '../context/AuthContext';
import type {CarListing} from '../types';
import {Loader2, Star, Upload, X} from "lucide-react";
import {MAKES, MODELS_BY_MAKE} from '../constants/cars';
import {EQUIPMENT_CATEGORIES} from '../constants/equipment';

const FUEL_TYPES = ['PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID', 'LPG'];
const FUEL_LABELS: Record<string, string> = {
    PETROL: 'Benzyna', DIESEL: 'Diesel', ELECTRIC: 'Elektryczny', HYBRID: 'Hybryda', LPG: 'LPG',
};

export default function EditListing() {
    const {id} = useParams<{ id: string }>();
    const navigate = useNavigate();
    const {user} = useAuth();

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [car, setCar] = useState<CarListing | null>(null);
    const [imageUrls, setImageUrls] = useState<string[]>([]);
    const [primaryImageUrl, setPrimaryImageUrl] = useState<string | undefined>(undefined);
    const [form, setForm] = useState({
        make: '', model: '', year: new Date().getFullYear(), price: '',
        mileage: '', fuelType: 'PETROL', transmission: 'MANUAL',
        color: '', city: '', country: 'Polska', description: '',
    });
    const [equipment, setEquipment] = useState<Set<string>>(new Set());

    useEffect(() => {
        if (!id) return;
        getCar(id).then(data => {
            setCar(data);
            setImageUrls(data.imageUrls ?? []);
            setPrimaryImageUrl(data.primaryImageUrl);
            setForm({
                make: data.make, model: data.model, year: data.year, price: String(data.price),
                mileage: String(data.mileage ?? ''), fuelType: data.fuelType, transmission: data.transmission,
                color: data.color ?? '', city: data.city, country: data.country ?? 'Polska',
                description: data.description ?? '',
            });
            setEquipment(new Set(data.equipment ?? []));
        }).catch(err => {
            console.error(err);
            alert('Nie udało się wczytać ogłoszenia');
            navigate('/moje-ogloszenia');
        }).finally(() => setLoading(false));
    }, [id, navigate]);

    useEffect(() => {
        if (car && user && car.sellerId !== user.id) {
            alert('Nie masz uprawnień do edycji tego ogłoszenia');
            navigate('/moje-ogloszenia');
        }
    }, [car, user, navigate]);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const {name, value} = e.target;
        setForm(prev => ({...prev, [name]: value, ...(name === 'make' ? {model: ''} : {})}));
    };

    const modelsForMake = MODELS_BY_MAKE[form.make] ?? [];
    const modelOptions = form.model && !modelsForMake.includes(form.model)
        ? [form.model, ...modelsForMake]
        : modelsForMake;

    const toggleEquipment = (code: string) => {
        setEquipment(prev => {
            const next = new Set(prev);
            if (next.has(code)) next.delete(code); else next.add(code);
            return next;
        });
    };

    const handleAddImages = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(e.target.files || []);
        if (!id || files.length === 0) return;
        const remaining = 8 - imageUrls.length;
        if (remaining <= 0) return;
        setUploading(true);
        try {
            const urls = await uploadCarImages(id, files.slice(0, remaining));
            setImageUrls(prev => [...prev, ...urls]);
            if (!primaryImageUrl && urls.length > 0) {
                setPrimaryImageUrl(urls[0]);
            }
        } catch (err) {
            console.error(err);
            alert('Błąd podczas przesyłania zdjęć');
        } finally {
            setUploading(false);
            e.target.value = '';
        }
    };

    const handleSetPrimary = async (url: string) => {
        if (!id || url === primaryImageUrl) return;
        try {
            await setPrimaryCarImage(id, url);
            setPrimaryImageUrl(url);
        } catch (err) {
            console.error(err);
            alert('Nie udało się ustawić głównego zdjęcia');
        }
    };

    const handleRemoveImage = async (url: string) => {
        if (!id) return;
        try {
            const updated = await deleteCarImage(id, url);
            setImageUrls(updated.imageUrls ?? []);
            setPrimaryImageUrl(updated.primaryImageUrl);
        } catch (err) {
            console.error(err);
            alert('Nie udało się usunąć zdjęcia');
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!id) return;
        setSaving(true);
        try {
            await updateCar(id, {
                ...form,
                year: Number(form.year),
                price: Number(form.price),
                mileage: Number(form.mileage),
                fuelType: form.fuelType as CarListing['fuelType'],
                transmission: form.transmission as CarListing['transmission'],
                equipment: Array.from(equipment),
            });
            navigate(`/ogloszenia/${id}`);
        } catch (err) {
            console.error(err);
            alert('Błąd podczas zapisywania ogłoszenia');
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
                <Loader2 size={28} className="animate-spin text-avtovo-accent"/>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-avtovo-bg py-10">
            <div className="max-w-3xl mx-auto px-4">
                <h1 className="text-2xl font-bold text-avtovo-text mb-8">Edytuj ogłoszenie</h1>

                <form onSubmit={handleSubmit} className="space-y-6">
                    {/* Images */}
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                        <h2 className="text-avtovo-text font-semibold mb-4">Zdjęcia</h2>
                        <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
                            {imageUrls.map((url) => (
                                <div key={url}
                                     className={`relative aspect-square rounded-lg overflow-hidden bg-avtovo-bg ${url === primaryImageUrl ? 'ring-2 ring-avtovo-accent' : ''}`}>
                                    <img src={url} alt="" className="w-full h-full object-cover"/>
                                    <button
                                        type="button"
                                        onClick={() => handleSetPrimary(url)}
                                        title="Ustaw jako główne zdjęcie"
                                        className={`absolute top-1 left-1 rounded-full p-1 transition-colors ${url === primaryImageUrl ? 'bg-avtovo-accent' : 'bg-black/70 hover:bg-black'}`}
                                    >
                                        <Star size={12} className="text-white" fill={url === primaryImageUrl ? 'currentColor' : 'none'}/>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => handleRemoveImage(url)}
                                        className="absolute top-1 right-1 bg-black/70 rounded-full p-0.5 hover:bg-black"
                                    >
                                        <X size={12} className="text-white"/>
                                    </button>
                                    {url === primaryImageUrl && (
                                        <span className="absolute bottom-1 left-1 right-1 bg-avtovo-accent text-white text-[10px] font-medium text-center rounded py-0.5">
                                            Główne
                                        </span>
                                    )}
                                </div>
                            ))}
                            {imageUrls.length < 8 && (
                                <label
                                    className="aspect-square rounded-lg border-2 border-dashed border-avtovo-border hover:border-avtovo-accent cursor-pointer flex flex-col items-center justify-center gap-1 transition-colors">
                                    {uploading ? (
                                        <Loader2 size={20} className="text-avtovo-muted animate-spin"/>
                                    ) : (
                                        <>
                                            <Upload size={20} className="text-avtovo-muted"/>
                                            <span className="text-xs text-avtovo-muted">Dodaj</span>
                                        </>
                                    )}
                                    <input type="file" accept="image/*" multiple onChange={handleAddImages}
                                           disabled={uploading}
                                           className="hidden"/>
                                </label>
                            )}
                        </div>
                        <p className="text-xs text-avtovo-muted mt-2">Maksymalnie 8 zdjęć. Kliknij gwiazdkę, aby ustawić zdjęcie główne.</p>
                    </div>

                    {/* Basic info */}
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                        <h2 className="text-avtovo-text font-semibold mb-4">Podstawowe informacje</h2>
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Marka *</label>
                                <select name="make" value={form.make} onChange={handleChange} required
                                        className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent">
                                    <option value="">Wybierz markę</option>
                                    {MAKES.map(m => <option key={m} value={m}>{m}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Model *</label>
                                {modelsForMake.length > 0 ? (
                                    <select name="model" value={form.model} onChange={handleChange} required
                                            className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent">
                                        <option value="">Wybierz model</option>
                                        {modelOptions.map(m => <option key={m} value={m}>{m}</option>)}
                                    </select>
                                ) : (
                                    <input name="model" value={form.model} onChange={handleChange} required
                                           placeholder="np. Golf, Corolla"
                                           className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
                                )}
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Rok *</label>
                                <input name="year" type="number" value={form.year} onChange={handleChange} required
                                       min={1900} max={new Date().getFullYear() + 1}
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent"/>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Cena (zł) *</label>
                                <input name="price" type="number" value={form.price} onChange={handleChange} required
                                       step={100} min={0}
                                       placeholder="np. 25000"
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Przebieg (km) *</label>
                                <input name="mileage" type="number" value={form.mileage} onChange={handleChange}
                                       required
                                       step={100} min={0}
                                       placeholder="np. 50000"
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Kolor</label>
                                <input name="color" value={form.color} onChange={handleChange}
                                       placeholder="np. Czarny"
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Paliwo *</label>
                                <select name="fuelType" value={form.fuelType} onChange={handleChange}
                                        className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent">
                                    {FUEL_TYPES.map(f => <option key={f} value={f}>{FUEL_LABELS[f]}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Skrzynia biegów
                                    *</label>
                                <select name="transmission" value={form.transmission} onChange={handleChange}
                                        className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent">
                                    <option value="MANUAL">Manualna</option>
                                    <option value="AUTOMATIC">Automatyczna</option>
                                </select>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Miasto *</label>
                                <input name="city" value={form.city} onChange={handleChange} required
                                       placeholder="np. Warszawa"
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Kraj</label>
                                <input name="country" value={form.country} onChange={handleChange}
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent"/>
                            </div>
                        </div>
                        <div className="mt-4">
                            <label className="block text-sm text-avtovo-text-secondary mb-1">Opis</label>
                            <textarea name="description" value={form.description} onChange={handleChange}
                                      rows={4} placeholder="Opisz swoje auto..."
                                      className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted resize-none"/>
                        </div>
                    </div>

                    {/* Equipment */}
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                        <h2 className="text-avtovo-text font-semibold mb-4">Wyposażenie</h2>
                        <div className="space-y-5">
                            {EQUIPMENT_CATEGORIES.map(cat => (
                                <div key={cat.key}>
                                    <h3 className="text-sm font-medium text-avtovo-text-secondary mb-2">{cat.label}</h3>
                                    <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
                                        {cat.options.map(opt => (
                                            <label key={opt.code}
                                                   className="flex items-center gap-2 text-sm text-avtovo-text bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 cursor-pointer hover:border-avtovo-accent">
                                                <input type="checkbox" checked={equipment.has(opt.code)}
                                                       onChange={() => toggleEquipment(opt.code)}
                                                       className="accent-avtovo-accent"/>
                                                {opt.label}
                                            </label>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    <button type="submit" disabled={saving}
                            className="w-full bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-50 text-white py-3.5 rounded-xl font-semibold transition-colors flex items-center justify-center gap-2">
                        {saving ? <><Loader2 size={18} className="animate-spin"/> Zapisywanie...</> : 'Zapisz zmiany'}
                    </button>
                </form>
            </div>
        </div>
    );
}
