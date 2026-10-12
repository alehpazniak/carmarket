import api from './client';
import type {CarDocument, CarListing, CarSummary, Page} from '../types';

export const getCars = (page = 0, size = 20) =>
    api.get<Page<CarListing>>(`/api/cars?page=${page}&size=${size}`).then(r => r.data);

export const getMyCars = () =>
    api.get<Page<CarListing>>('/api/cars/my').then(r => r.data);

/** The owner's own listing in any status (BLOCKED / SUSPENDED too), with the moderation reason. */
export const getMyCar = (id: string) =>
    api.get<CarListing>(`/api/cars/my/${id}`).then(r => r.data);

/** "Zgłoś ogłoszenie": 409 when already reported, 429 after 10 reports a day. */
export const reportCar = (id: string, reason: string, comment?: string) =>
    api.post(`/api/cars/${id}/reports`, { reason, comment: comment || undefined }).then(r => r.data);

export const getCar = (id: string) =>
    api.get<CarListing>(`/api/cars/${id}`).then(r => r.data);

/** car-service's limit per summaries request. */
export const MAX_SUMMARY_IDS = 100;

/** Summaries for up to MAX_SUMMARY_IDS listings, REMOVED included; unknown ids are left out. Requires login. */
export const getCarSummaries = (ids: string[]) =>
    ids.length === 0
        ? Promise.resolve<CarSummary[]>([])
        : api.get<CarSummary[]>('/api/cars/summaries', {params: {ids: ids.join(',')}}).then(r => r.data);

export const createCar = (data: Partial<CarListing>) =>
    api.post<CarListing>('/api/cars', data).then(r => r.data);

export const updateCar = (id: string, data: Partial<CarListing>) =>
    api.put<CarListing>(`/api/cars/${id}`, data).then(r => r.data);

export const deleteCar = (id: string) =>
    api.delete(`/api/cars/${id}`);

export const markCarAsSold = (id: string) =>
    api.patch<CarListing>(`/api/cars/${id}/sold`).then(r => r.data);

export const uploadCarImages = (carId: string, files: File[]) => {
    const formData = new FormData();
    files.forEach(f => formData.append('files', f));
    return api.post<string[]>(`/api/cars/${carId}/images`, formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    }).then(r => r.data);
};

export const setPrimaryCarImage = (carId: string, url: string) =>
    api.patch<CarListing>(`/api/cars/${carId}/images/primary?url=${encodeURIComponent(url)}`)
        .then(r => r.data);

export const deleteCarImage = (carId: string, url: string) =>
    api.delete<CarListing>(`/api/cars/${carId}/images?url=${encodeURIComponent(url)}`)
        .then(r => r.data);

/** Attaches a YouTube, TikTok or Instagram link; the backend rejects anything else with 400. */
export const setCarVideo = (carId: string, url: string) =>
    api.put<CarListing>(`/api/cars/${carId}/video`, {url}).then(r => r.data);

export const deleteCarVideo = (carId: string) =>
    api.delete<CarListing>(`/api/cars/${carId}/video`).then(r => r.data);

export const searchCars = (params: Record<string, string | number> = {}) => {
    const query = new URLSearchParams(
        Object.entries(params)
            .filter(([, v]) => v !== '' && v !== undefined && v !== null)
            .map(([k, v]) => [k, String(v)])
    ).toString();
    return api.get<CarDocument[]>(`/api/search?${query}`).then(r => r.data);
};