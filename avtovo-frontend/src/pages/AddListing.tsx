import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {createCar, setPrimaryCarImage, uploadCarImages} from '../api/cars';
import type {CarListing} from '../types';
import {Loader2, Star, Upload, X} from "lucide-react";

const FUEL_TYPES = ['PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID', 'LPG'];
const FUEL_LABELS: Record<string, string> = {
    PETROL: 'Benzyna', DIESEL: 'Diesel', ELECTRIC: 'Elektryczny', HYBRID: 'Hybryda', LPG: 'LPG',
};

const MAKES = ['Audi', 'BMW', 'Ford', 'Honda', 'Hyundai', 'Kia', 'Mazda', 'Mercedes-Benz',
    'Nissan', 'Opel', 'Peugeot', 'Renault', 'Seat', 'Skoda', 'Toyota', 'Volkswagen', 'Volvo', 'Inne'];

export default function AddListing() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [images, setImages] = useState<File[]>([]);
    const [previews, setPreviews] = useState<string[]>([]);
    const [mainIndex, setMainIndex] = useState(0);
    const [form, setForm] = useState({
        make: '', model: '', year: new Date().getFullYear(), price: '',
        mileage: '', fuelType: 'PETROL', transmission: 'MANUAL',
        color: '', city: '', country: 'Polska', description: '',
    });

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        setForm(prev => ({...prev, [e.target.name]: e.target.value}));
    };

    const handleImages = (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(e.target.files || []);
        const newFiles = [...images, ...files].slice(0, 8);
        setImages(newFiles);
        const newPreviews = newFiles.map(f => URL.createObjectURL(f));
        setPreviews(newPreviews);
    };

    const removeImage = (idx: number) => {
        const newImages = images.filter((_, i) => i !== idx);
        const newPreviews = previews.filter((_, i) => i !== idx);
        setImages(newImages);
        setPreviews(newPreviews);
        setMainIndex(prev => {
            if (idx === prev) return 0;
            if (idx < prev) return prev - 1;
            return prev;
        });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        try {
            const car = await createCar({
                ...form,
                year: Number(form.year),
                price: Number(form.price),
                mileage: Number(form.mileage),
                fuelType: form.fuelType as CarListing['fuelType'],
                transmission: form.transmission as CarListing['transmission'],
            });

            if (images.length > 0) {
                const urls = await uploadCarImages(car.id, images);
                const mainUrl = urls[mainIndex] ?? urls[0];
                if (mainUrl) {
                    await setPrimaryCarImage(car.id, mainUrl);
                }
            }

            navigate(`/ogloszenia/${car.id}`);
        } catch (err) {
            console.error(err);
            alert('Błąd podczas dodawania ogłoszenia');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-avtovo-bg py-10">
            <div className="max-w-3xl mx-auto px-4">
                <h1 className="text-2xl font-bold text-avtovo-text mb-8">Dodaj ogłoszenie</h1>

                <form onSubmit={handleSubmit} className="space-y-6">
                    {/* Images */}
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                        <h2 className="text-avtovo-text font-semibold mb-4">Zdjęcia</h2>
                        <div className="grid grid-cols-4 gap-3">
                            {previews.map((src, idx) => (
                                <div key={idx}
                                     className={`relative aspect-square rounded-lg overflow-hidden bg-avtovo-bg ${idx === mainIndex ? 'ring-2 ring-avtovo-accent' : ''}`}>
                                    <img src={src} alt="" className="w-full h-full object-cover"/>
                                    <button
                                        type="button"
                                        onClick={() => setMainIndex(idx)}
                                        title="Ustaw jako główne zdjęcie"
                                        className={`absolute top-1 left-1 rounded-full p-1 transition-colors ${idx === mainIndex ? 'bg-avtovo-accent' : 'bg-black/70 hover:bg-black'}`}
                                    >
                                        <Star size={12} className="text-white" fill={idx === mainIndex ? 'currentColor' : 'none'}/>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => removeImage(idx)}
                                        className="absolute top-1 right-1 bg-black/70 rounded-full p-0.5 hover:bg-black"
                                    >
                                        <X size={12} className="text-white"/>
                                    </button>
                                    {idx === mainIndex && (
                                        <span className="absolute bottom-1 left-1 right-1 bg-avtovo-accent text-white text-[10px] font-medium text-center rounded py-0.5">
                                            Główne
                                        </span>
                                    )}
                                </div>
                            ))}
                            {previews.length < 8 && (
                                <label
                                    className="aspect-square rounded-lg border-2 border-dashed border-avtovo-border hover:border-avtovo-accent cursor-pointer flex flex-col items-center justify-center gap-1 transition-colors">
                                    <Upload size={20} className="text-avtovo-muted"/>
                                    <span className="text-xs text-avtovo-muted">Dodaj</span>
                                    <input type="file" accept="image/*" multiple onChange={handleImages}
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
                                <input name="model" value={form.model} onChange={handleChange} required
                                       placeholder="np. Golf, Corolla"
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
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
                                       placeholder="np. 25000"
                                       className="w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted"/>
                            </div>
                            <div>
                                <label className="block text-sm text-avtovo-text-secondary mb-1">Przebieg (km) *</label>
                                <input name="mileage" type="number" value={form.mileage} onChange={handleChange}
                                       required
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

                    <button type="submit" disabled={loading}
                            className="w-full bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-50 text-white py-3.5 rounded-xl font-semibold transition-colors flex items-center justify-center gap-2">
                        {loading ? <><Loader2 size={18} className="animate-spin"/> Dodawanie...</> : 'Dodaj ogłoszenie'}
                    </button>
                </form>
            </div>
        </div>
    );
}