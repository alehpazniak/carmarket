/**
 * Roles from the access token payload. Decoded only — not verified: the gateway checks the
 * signature on every request, this just decides what to show.
 */
/** The user id (sub) from the access token payload; decoded only, like {@link rolesFromToken}. */
export function userIdFromToken(token: string | null): string | null {
    if (!token) return null;
    try {
        const payload = token.split('.')[1];
        const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '='));
        const sub = JSON.parse(json).sub;
        return typeof sub === 'string' ? sub : null;
    } catch {
        return null;
    }
}

export function rolesFromToken(token: string | null): string[] {
    if (!token) return [];
    try {
        const payload = token.split('.')[1];
        const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '='));
        const roles = JSON.parse(json).roles;
        return Array.isArray(roles) ? roles.filter((r): r is string => typeof r === 'string') : [];
    } catch {
        return [];
    }
}
