import type {VehicleCategory} from '../types';
import {MAKES_BY_CATEGORY, MODELS_BY_CATEGORY} from './vehicleMakes';

/** Makes offered for a category (otomoto has a separate list per category). */
export const makesFor = (category: VehicleCategory): readonly string[] => MAKES_BY_CATEGORY[category];

/** Model list for a make; empty when the category uses a free-text model (everything but cars and motorcycles). */
export const modelsFor = (category: VehicleCategory, make: string): readonly string[] =>
    MODELS_BY_CATEGORY[category]?.[make] ?? [];

/** Whether the category has model lists at all (otherwise the model is typed in, like on otomoto). */
export const hasModelList = (category: VehicleCategory): boolean => category in MODELS_BY_CATEGORY;

export const CATEGORY_LABELS: Record<VehicleCategory, string> = {
    PASSENGER: 'Osobowe',
    TRUCK: 'Ciężarowe',
    CONSTRUCTION: 'Budowlane',
    VAN: 'Dostawcze',
    MOTORCYCLE: 'Motocykle',
    TRAILER: 'Przyczepy',
    AGRICULTURAL: 'Rolnicze',
};

export const VEHICLE_CATEGORIES = Object.keys(CATEGORY_LABELS) as VehicleCategory[];
