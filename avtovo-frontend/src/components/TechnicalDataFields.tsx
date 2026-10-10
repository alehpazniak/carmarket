import type {VehicleCategory} from '../types';
import {
    BODY_LABELS, CONDITION_LABELS, DRIVE_LABELS, FLAG_LABELS, showsField,
    type FlagField, type TechnicalForm,
} from '../constants/technical';

interface Props {
    category: VehicleCategory;
    value: TechnicalForm;
    onChange: (next: TechnicalForm) => void;
}

const INPUT = 'w-full bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2.5 focus:outline-none focus:border-avtovo-accent placeholder-avtovo-muted';
const LABEL = 'block text-sm text-avtovo-text-secondary mb-1';

/** Otomoto-style "Dane techniczne" inputs; which ones show depends on the vehicle category. */
export default function TechnicalDataFields({category, value, onChange}: Props) {
    const set = <K extends keyof TechnicalForm>(key: K, v: TechnicalForm[K]) => onChange({...value, [key]: v});
    const text = (key: keyof TechnicalForm) =>
        (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => set(key, e.target.value as never);
    const shows = (field: Parameters<typeof showsField>[1]) => showsField(category, field);

    return (
        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
            <h2 className="text-avtovo-text font-semibold mb-4">Dane techniczne</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                    <label className={LABEL}>Stan</label>
                    <select value={value.condition} onChange={text('condition')} className={INPUT}>
                        {Object.entries(CONDITION_LABELS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                    </select>
                </div>
                {shows('version') && (
                    <div>
                        <label className={LABEL}>Wersja</label>
                        <input value={value.version} onChange={text('version')} maxLength={100}
                               placeholder="np. 2.0 TDI Sportback" className={INPUT}/>
                    </div>
                )}
                {shows('engineCapacity') && (
                    <div>
                        <label className={LABEL}>Pojemność skokowa (cm³)</label>
                        <input type="number" min={0} max={20000} value={value.engineCapacity}
                               onChange={text('engineCapacity')} placeholder="np. 1968" className={INPUT}/>
                    </div>
                )}
                {shows('powerHp') && (
                    <div>
                        <label className={LABEL}>Moc (KM)</label>
                        <input type="number" min={0} max={3000} value={value.powerHp}
                               onChange={text('powerHp')} placeholder="np. 150" className={INPUT}/>
                    </div>
                )}
                {shows('driveType') && (
                    <div>
                        <label className={LABEL}>Napęd</label>
                        <select value={value.driveType} onChange={text('driveType')} className={INPUT}>
                            <option value="">Wybierz</option>
                            {Object.entries(DRIVE_LABELS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                        </select>
                    </div>
                )}
                {shows('bodyType') && (
                    <div>
                        <label className={LABEL}>Typ nadwozia</label>
                        <select value={value.bodyType} onChange={text('bodyType')} className={INPUT}>
                            <option value="">Wybierz</option>
                            {Object.entries(BODY_LABELS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                        </select>
                    </div>
                )}
                {shows('doors') && (
                    <div>
                        <label className={LABEL}>Liczba drzwi</label>
                        <input type="number" min={1} max={10} value={value.doors}
                               onChange={text('doors')} className={INPUT}/>
                    </div>
                )}
                {shows('seats') && (
                    <div>
                        <label className={LABEL}>Liczba miejsc</label>
                        <input type="number" min={1} max={100} value={value.seats}
                               onChange={text('seats')} className={INPUT}/>
                    </div>
                )}
                <div>
                    <label className={LABEL}>VIN</label>
                    <input value={value.vin} onChange={e => set('vin', e.target.value.toUpperCase())}
                           maxLength={17} pattern="[A-HJ-NPR-Za-hj-npr-z0-9]{17}"
                           title="17 znaków, bez liter I, O i Q"
                           placeholder="17 znaków" className={`${INPUT} font-mono`}/>
                </div>
                <div>
                    <label className={LABEL}>Kraj pochodzenia</label>
                    <input value={value.originCountry} onChange={text('originCountry')} maxLength={100}
                           placeholder="np. Niemcy" className={INPUT}/>
                </div>
                <div>
                    <label className={LABEL}>Data pierwszej rejestracji</label>
                    <input type="date" value={value.firstRegistrationDate}
                           max={new Date().toISOString().slice(0, 10)}
                           onChange={text('firstRegistrationDate')} className={INPUT}/>
                </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 mt-4">
                {(Object.keys(FLAG_LABELS) as FlagField[]).map(flag => (
                    <label key={flag}
                           className="flex items-center gap-2 text-sm text-avtovo-text bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 cursor-pointer hover:border-avtovo-accent">
                        <input type="checkbox" checked={value[flag]} onChange={e => set(flag, e.target.checked)}
                               className="accent-avtovo-accent"/>
                        {FLAG_LABELS[flag]}
                    </label>
                ))}
            </div>
        </div>
    );
}
