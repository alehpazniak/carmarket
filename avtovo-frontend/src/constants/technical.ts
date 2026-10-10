import type {BodyType, DriveType, TechnicalData, VehicleCategory, VehicleCondition} from '../types';

export const DRIVE_LABELS: Record<DriveType, string> = {
    FRONT: 'Na przednie koła',
    REAR: 'Na tylne koła',
    AWD_AUTO: '4x4 (dołączany automatycznie)',
    AWD_MANUAL: '4x4 (dołączany ręcznie)',
    AWD_PERMANENT: '4x4 (stały)',
};

export const BODY_LABELS: Record<BodyType, string> = {
    MINI: 'Auta małe',
    CITY: 'Auta miejskie',
    COUPE: 'Coupe',
    CABRIO: 'Kabriolet',
    ESTATE: 'Kombi',
    COMPACT: 'Kompakt',
    MINIVAN: 'Minivan',
    SEDAN: 'Sedan',
    SUV: 'SUV',
};

export const CONDITION_LABELS: Record<VehicleCondition, string> = {
    USED: 'Używane',
    NEW: 'Nowe',
};

export const FLAG_LABELS = {
    registeredInPoland: 'Zarejestrowany w Polsce',
    firstOwner: 'Pierwszy właściciel',
    accidentFree: 'Bezwypadkowy',
    asoServiced: 'Serwisowany w ASO',
    damaged: 'Uszkodzony',
} as const;

export type FlagField = keyof typeof FLAG_LABELS;

type OptionalField = 'version' | 'engineCapacity' | 'powerHp' | 'driveType' | 'bodyType' | 'doors' | 'seats';

/** Fields that don't apply to a category are hidden (otomoto shows no doors for a motorcycle, no engine for a trailer). */
const FIELDS_BY_CATEGORY: Record<VehicleCategory, readonly OptionalField[]> = {
    PASSENGER: ['version', 'engineCapacity', 'powerHp', 'driveType', 'bodyType', 'doors', 'seats'],
    VAN: ['version', 'engineCapacity', 'powerHp', 'driveType', 'doors', 'seats'],
    TRUCK: ['version', 'engineCapacity', 'powerHp', 'driveType', 'seats'],
    MOTORCYCLE: ['version', 'engineCapacity', 'powerHp'],
    CONSTRUCTION: ['engineCapacity', 'powerHp', 'driveType'],
    AGRICULTURAL: ['engineCapacity', 'powerHp', 'driveType'],
    TRAILER: [],
};

export const showsField = (category: VehicleCategory, field: OptionalField): boolean =>
    FIELDS_BY_CATEGORY[category].includes(field);

/** Form state: every input is a string (or a checkbox flag), converted by {@link toTechnicalData}. */
export interface TechnicalForm {
    version: string;
    engineCapacity: string;
    powerHp: string;
    driveType: string;
    bodyType: string;
    doors: string;
    seats: string;
    vin: string;
    condition: string;
    originCountry: string;
    firstRegistrationDate: string;
    registeredInPoland: boolean;
    firstOwner: boolean;
    accidentFree: boolean;
    asoServiced: boolean;
    damaged: boolean;
}

export const EMPTY_TECHNICAL_FORM: TechnicalForm = {
    version: '', engineCapacity: '', powerHp: '', driveType: '', bodyType: '', doors: '', seats: '',
    vin: '', condition: 'USED', originCountry: '', firstRegistrationDate: '',
    registeredInPoland: false, firstOwner: false, accidentFree: false, asoServiced: false, damaged: false,
};

export const technicalFormFrom = (data?: TechnicalData | null): TechnicalForm => {
    if (!data) return EMPTY_TECHNICAL_FORM;
    const str = (v: string | number | null | undefined) => (v == null ? '' : String(v));
    return {
        version: str(data.version),
        engineCapacity: str(data.engineCapacity),
        powerHp: str(data.powerHp),
        driveType: str(data.driveType),
        bodyType: str(data.bodyType),
        doors: str(data.doors),
        seats: str(data.seats),
        vin: str(data.vin),
        condition: data.condition ?? 'USED',
        originCountry: str(data.originCountry),
        firstRegistrationDate: str(data.firstRegistrationDate),
        registeredInPoland: !!data.registeredInPoland,
        firstOwner: !!data.firstOwner,
        accidentFree: !!data.accidentFree,
        asoServiced: !!data.asoServiced,
        damaged: !!data.damaged,
    };
};

/** Request payload; empty and category-irrelevant fields are sent as null so the backend clears them. */
export const toTechnicalData = (form: TechnicalForm, category: VehicleCategory): TechnicalData => {
    const text = (v: string) => v.trim() || null;
    const num = (v: string) => (v.trim() === '' ? null : Number(v));
    const only = <T, >(field: OptionalField, value: T): T | null => (showsField(category, field) ? value : null);
    return {
        version: only('version', text(form.version)),
        engineCapacity: only('engineCapacity', num(form.engineCapacity)),
        powerHp: only('powerHp', num(form.powerHp)),
        driveType: only('driveType', (form.driveType || null) as DriveType | null),
        bodyType: only('bodyType', (form.bodyType || null) as BodyType | null),
        doors: only('doors', num(form.doors)),
        seats: only('seats', num(form.seats)),
        vin: text(form.vin.toUpperCase()),
        condition: (form.condition || null) as VehicleCondition | null,
        originCountry: text(form.originCountry),
        firstRegistrationDate: form.firstRegistrationDate || null,
        registeredInPoland: form.registeredInPoland || null,
        firstOwner: form.firstOwner || null,
        accidentFree: form.accidentFree || null,
        asoServiced: form.asoServiced || null,
        damaged: form.damaged || null,
    };
};
