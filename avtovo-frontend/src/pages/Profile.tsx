import {useEffect, useState} from 'react';
import {isAxiosError} from 'axios';
import {CheckCircle2, Loader2} from 'lucide-react';
import {getMyProfile, updateContactInfo} from '../api/users';
import type {ContactInfo} from '../types';

type Field = keyof ContactInfo;
type Errors = Partial<Record<Field, string>>;

const EMPTY: ContactInfo = {phoneNumber: '', city: '', street: '', houseNumber: ''};
const PHONE_PATTERN = /^\+?[0-9 ()-]{7,20}$/;

const inputClass = 'w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted';

/** Mirrors ContactInfoRequest validation in user-service, so most mistakes never hit the server. */
function validate(form: ContactInfo): Errors {
    const errors: Errors = {};
    if (!form.phoneNumber.trim()) {
        errors.phoneNumber = 'Numer telefonu jest wymagany';
    } else if (!PHONE_PATTERN.test(form.phoneNumber.trim())) {
        errors.phoneNumber = 'Podaj poprawny numer telefonu, np. +48 123 456 789';
    }
    if (!form.city.trim()) errors.city = 'Miasto jest wymagane';
    if (!form.street.trim()) errors.street = 'Ulica jest wymagana';
    return errors;
}

export default function Profile() {
    const [form, setForm] = useState<ContactInfo>(EMPTY);
    const [email, setEmail] = useState('');
    const [errors, setErrors] = useState<Errors>({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [loadError, setLoadError] = useState(false);

    useEffect(() => {
        getMyProfile()
            .then((p) => {
                setEmail(p.email);
                setForm({
                    phoneNumber: p.phoneNumber ?? '',
                    city: p.city ?? '',
                    street: p.street ?? '',
                    houseNumber: p.houseNumber ?? '',
                });
            })
            .catch(() => setLoadError(true))
            .finally(() => setLoading(false));
    }, []);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const name = e.target.name as Field;
        setForm((f) => ({...f, [name]: e.target.value}));
        setErrors((errs) => ({...errs, [name]: undefined}));
        setSaved(false);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        const clientErrors = validate(form);
        setErrors(clientErrors);
        if (Object.keys(clientErrors).length > 0) return;

        setSaving(true);
        try {
            await updateContactInfo(form);
            setSaved(true);
        } catch (err) {
            if (isAxiosError(err) && err.response?.status === 400 && err.response.data) {
                setErrors(err.response.data as Errors);
            } else {
                console.error(err);
                alert('Nie udało się zapisać danych');
            }
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

    if (loadError) {
        return (
            <div className="min-h-screen bg-avtovo-bg py-10">
                <p className="max-w-xl mx-auto px-4 text-avtovo-text-secondary">
                    Nie udało się wczytać profilu. Spróbuj odświeżyć stronę.
                </p>
            </div>
        );
    }

    const renderInput = (name: Field, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
        <div>
            <label htmlFor={name} className="block text-sm text-avtovo-text-secondary mb-1">{label}</label>
            <input id={name} name={name} value={form[name]} onChange={handleChange}
                   aria-invalid={!!errors[name]}
                   className={`${inputClass} ${errors[name] ? 'border-red-500' : ''}`}
                   {...props}/>
            {errors[name] && <p className="text-xs text-red-400 mt-1">{errors[name]}</p>}
        </div>
    );

    return (
        <div className="min-h-screen bg-avtovo-bg py-10">
            <div className="max-w-xl mx-auto px-4">
                <h1 className="text-2xl font-bold text-avtovo-text mb-2">Mój profil</h1>
                {email && <p className="text-sm text-avtovo-text-secondary mb-8">{email}</p>}

                <form onSubmit={handleSubmit} noValidate className="space-y-6">
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 space-y-4">
                        <h2 className="text-avtovo-text font-semibold">Telefon</h2>
                        {renderInput('phoneNumber', 'Numer telefonu *', {
                            type: 'tel', autoComplete: 'tel', placeholder: '+48 123 456 789',
                        })}
                    </div>

                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 space-y-4">
                        <h2 className="text-avtovo-text font-semibold">Adres</h2>
                        {renderInput('city', 'Miasto *', {autoComplete: 'address-level2', placeholder: 'np. Warszawa'})}
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                            <div className="sm:col-span-2">
                                {renderInput('street', 'Ulica *', {autoComplete: 'address-line1', placeholder: 'np. Marszałkowska'})}
                            </div>
                            {renderInput('houseNumber', 'Nr domu / lokalu', {autoComplete: 'address-line2', placeholder: 'np. 12/4'})}
                        </div>
                        <p className="text-xs text-avtovo-muted">
                            Ulica i numer domu są widoczne tylko dla Ciebie. Inni użytkownicy widzą jedynie miasto.
                        </p>
                    </div>

                    <button type="submit" disabled={saving}
                            className="w-full bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-50 text-white py-3.5 rounded-xl font-semibold transition-colors flex items-center justify-center gap-2">
                        {saving ? <><Loader2 size={18} className="animate-spin"/> Zapisywanie...</> : 'Zapisz'}
                    </button>
                    {saved && (
                        <p role="status" className="flex items-center justify-center gap-2 text-sm text-green-400">
                            <CheckCircle2 size={16}/> Dane zostały zapisane
                        </p>
                    )}
                </form>
            </div>
        </div>
    );
}
