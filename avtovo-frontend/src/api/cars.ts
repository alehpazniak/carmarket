import api from './client';
import type {CarDocument, CarListing, Page} from '../types';

export const getCars = (page = 0, size = 20) =>
    api.get<Page<CarListing>>(`/api/cars?page=${page}&size=${size}`).then(r => r.data);

export const getMyCars = () =>
    api.get<Page<CarListing>>('/api/cars/my').then(r => r.data);

export const getCar = (id: string) =>
    api.get<CarListing>(`/api/cars/${id}`).then(r => r.data);

export const createCar = (data: Partial<CarListing>) =>
    api.post<CarListing>('/api/cars', data).then(r => r.data);

export const updateCar = (id: string, data: Partial<CarListing>) =>
    api.put<CarListing>(`/api/cars/${id}`, data).then(r => r.data);

export const deleteCar = (id: string) =>
    api.delete(`/api/cars/${id}`);

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

export const searchCars = (params: Record<string, string | number> = {}) => {
    const query = new URLSearchParams(
        Object.entries(params)
            .filter(([, v]) => v !== '' && v !== undefined && v !== null)
            .map(([k, v]) => [k, String(v)])
    ).toString();
    return api.get<CarDocument[]>(`/api/search?${query}`).then(r => r.data);
};