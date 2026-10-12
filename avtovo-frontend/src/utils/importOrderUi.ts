/** Polish message for an API error: the service's own text when it has one. */
export function apiErrorMessage(err: unknown, fallback = 'Coś poszło nie tak. Spróbuj ponownie.'): string {
    const e = err as { response?: { status?: number; data?: { error?: string } } };
    const status = e?.response?.status;
    if (status === 503) return 'Przechowywanie plików nie jest skonfigurowane (503).';
    if (status === 413) return 'Plik jest za duży.';
    if (status === 409) return 'Masz już zlecenie dla tego lotu.';
    if (status === 404) return 'Nie znaleziono zlecenia.';
    const message = e?.response?.data?.error;
    return message ? `${fallback} (${message})` : fallback;
}
