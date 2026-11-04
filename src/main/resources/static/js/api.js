const SESSION_KEY = 'medschedule.session.v1';

export class ApiError extends Error {
    constructor(message, status, payload) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.payload = payload;
    }
}

function readSession() {
    try {
        const raw = sessionStorage.getItem(SESSION_KEY);
        if (!raw) {
            return null;
        }
        const value = JSON.parse(raw);
        if (!value?.token || !value?.user) {
            sessionStorage.removeItem(SESSION_KEY);
            return null;
        }
        if (value.expiresAt && Date.now() >= value.expiresAt) {
            sessionStorage.removeItem(SESSION_KEY);
            return null;
        }
        return value;
    } catch {
        sessionStorage.removeItem(SESSION_KEY);
        return null;
    }
}

export const session = {
    get() {
        return readSession();
    },
    set(loginResponse) {
        const value = {
            token: loginResponse.accessToken,
            user: loginResponse.user,
            expiresAt: Date.now() + Number(loginResponse.expiresIn || 0) * 1000
        };
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
        return value;
    },
    clear() {
        sessionStorage.removeItem(SESSION_KEY);
    }
};

async function readJson(response) {
    const text = await response.text();
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch {
        return { message: text };
    }
}

export async function login(username, password) {
    const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ username, password })
    });
    const data = await readJson(response);

    if (!response.ok) {
        const message = data?.message || 'Не удалось выполнить вход';
        throw new ApiError(message, response.status, data);
    }
    return data;
}

export async function api(path, options = {}) {
    const { method = 'GET', body, params, signal } = options;
    const url = new URL('/api' + path, window.location.origin);

    if (params) {
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.set(key, value);
            }
        });
    }

    const headers = { 'Accept': 'application/json' };
    const current = readSession();
    if (current?.token) {
        headers.Authorization = `Bearer ${current.token}`;
    }
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }

    const response = await fetch(url, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal
    });

    if (response.status === 401) {
        session.clear();
        window.dispatchEvent(new CustomEvent('medschedule:unauthorized'));
    }

    const data = await readJson(response);
    if (!response.ok) {
        const message = data?.message || `Ошибка запроса (${response.status})`;
        throw new ApiError(message, response.status, data);
    }

    return data;
}
