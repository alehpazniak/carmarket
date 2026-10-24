import { makesFor, modelsFor } from '../constants/cars';

/**
 * Display names for the /aukcje make/model dropdowns. Apibara stores US names in capitals ("MERCEDES-BENZ",
 * "F-150"); when one matches a passenger-car catalog name letter for letter (case, accents, spaces and dashes
 * ignored) we show the catalog spelling ("Mercedes-Benz", "F150"), otherwise the value as is. No aliases:
 * "3 SERIES" stays "3 SERIES", it is not "Seria 3".
 */

const key = (name: string) =>
    name.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().replace(/[^\p{L}\p{N}]/gu, '');

const findIn = (names: readonly string[], value: string) => {
    const k = key(value);
    return names.find(name => name !== 'Inny' && key(name) === k);
};

/** Catalog spelling of a facet make, e.g. TOYOTA → Toyota; undefined when the catalog doesn't have it. */
const catalogMake = (make: string) => findIn(makesFor('PASSENGER'), make);

export const makeDisplayName = (make: string): string => catalogMake(make) ?? make;

export const modelDisplayName = (make: string, model: string): string => {
    const catalog = catalogMake(make);
    return (catalog && findIn(modelsFor('PASSENGER', catalog), model)) || model;
};
