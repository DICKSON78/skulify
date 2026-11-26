export type Page<T> = {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};

export type AuthUser = {
    id: string;
    tenantId: string;
    username: string;
    fullName: string;
    roles: string[];
    mustChangePassword: boolean;
};

export type LoginResult = {
    accessToken: string | null;
    refreshToken: string | null;
    mfaChallengeToken: string | null;
    mfaRequired: boolean;
    expiresIn: number | null;
    mustChangePassword: boolean | null;
};

const BASE = '/api/v1';
const ACCESS_KEY = 'school_wallet_access_token';
const REFRESH_KEY = 'school_wallet_refresh_token';

export const tokenStore = {
    access: () => localStorage.getItem(ACCESS_KEY),
    refresh: () => localStorage.getItem(REFRESH_KEY),
    save(accessToken: string, refreshToken?: string | null) {
        localStorage.setItem(ACCESS_KEY, accessToken);
        if (refreshToken) {
            localStorage.setItem(REFRESH_KEY, refreshToken);
        }
    },
    clear() {
        localStorage.removeItem(ACCESS_KEY);
        localStorage.removeItem(REFRESH_KEY);
    },
};

async function parseError(response: Response): Promise<string> {
    let message = `Request failed (${response.status})`;
    try {
        const body = await response.json();
        message = body.detail ?? body.message ?? body.title ?? message;
    } catch {
        // keep the generic message
    }
    return message;
}

async function refreshSession(): Promise<boolean> {
    const refreshToken = tokenStore.refresh();
    if (!refreshToken) {
        return false;
    }
    try {
        const response = await fetch(`${BASE}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }),
        });
        if (!response.ok) {
            tokenStore.clear();
            return false;
        }
        const data = (await response.json()) as LoginResult;
        if (data.accessToken) {
            tokenStore.save(data.accessToken, data.refreshToken);
            return true;
        }
        return false;
    } catch {
        tokenStore.clear();
        return false;
    }
}

async function request<T>(path: string, init?: RequestInit, retry = true): Promise<T> {
    // FormData must keep its auto-generated multipart boundary; never force JSON here.
    const isForm = typeof FormData !== 'undefined' && init?.body instanceof FormData;
    const headers: Record<string, string> = {
        ...(isForm ? {} : { 'Content-Type': 'application/json' }),
        ...(init?.headers as Record<string, string> | undefined),
    };
    const accessToken = tokenStore.access();
    if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`;
    }

    const response = await fetch(`${BASE}${path}`, { ...init, headers });

    if (response.status === 401 && retry && tokenStore.refresh()) {
        if (await refreshSession()) {
            return request<T>(path, init, false);
        }
    }

    if (!response.ok) {
        throw new Error(await parseError(response));
    }
    if (response.status === 204) {
        return undefined as T;
    }
    return response.json() as Promise<T>;
}

/** Anonymous calls used by the signed online-test link (no stored credentials sent). */
async function publicRequest<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${BASE}${path}`, {
        ...init,
        headers: { 'Content-Type': 'application/json', ...(init?.headers as Record<string, string> | undefined) },
    });
    if (!response.ok) {
        throw new Error(await parseError(response));
    }
    if (response.status === 204) {
        return undefined as T;
    }
    return response.json() as Promise<T>;
}

export const authApi = {
    async login(identifier: string, password: string): Promise<LoginResult> {
        const response = await fetch(`${BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ identifier, password }),
        });
        if (!response.ok) {
            throw new Error(await parseError(response));
        }
        return response.json() as Promise<LoginResult>;
    },

    async verifyMfa(token: string, code: string): Promise<LoginResult> {
        const response = await fetch(`${BASE}/auth/mfa/verify`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token, code }),
        });
        if (!response.ok) {
            throw new Error(await parseError(response));
        }
        return response.json() as Promise<LoginResult>;
    },

    me: () => request<AuthUser>('/me'),
    logout: async () => {
        const refreshToken = tokenStore.refresh();
        if (refreshToken) {
            try {
                await fetch(`${BASE}/auth/logout`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refreshToken }),
                });
            } catch {
                // Local logout still succeeds if the server is unreachable.
            }
        }
        tokenStore.clear();
    },
};

export const api = {
    list: <T>(resource: string, page: number, size: number, q: string) =>
        request<Page<T>>(`/${resource}?page=${page}&size=${size}&q=${encodeURIComponent(q)}`),
    get: <T>(resource: string, id: string) => request<T>(`/${resource}/${id}`),
    create: <T>(resource: string, body: Record<string, unknown>) =>
        request<T>(`/${resource}`, { method: 'POST', body: JSON.stringify(body) }),
    update: <T>(resource: string, id: string, body: Record<string, unknown>) =>
        request<T>(`/${resource}/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (resource: string, id: string) =>
        request<void>(`/${resource}/${id}`, { method: 'DELETE' }),
    summary: <T>() => request<T>('/dashboard'),
    recentStudents: <T>() => request<T[]>('/dashboard/recent-students'),
    downloadReport: async (id: string) => {
        const accessToken = tokenStore.access();
        const response = await fetch(`${BASE}/reports/${id}/generate`, {
            headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
        });
        if (!response.ok) {
            throw new Error(await parseError(response));
        }
        const blob = await response.blob();
        const disposition = response.headers.get('Content-Disposition') ?? '';
        const filename = disposition.match(/filename="([^"]+)"/)?.[1] ?? 'report.csv';
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);
    },
};

export const documentsApi = {
    upload: <T>(form: FormData) =>
        request<T>('/school-documents/upload', { method: 'POST', body: form }),
    download: async (id: string) => {
        const accessToken = tokenStore.access();
        const response = await fetch(`${BASE}/school-documents/${id}/download`, {
            headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
        });
        if (!response.ok) {
            throw new Error(await parseError(response));
        }
        const blob = await response.blob();
        const disposition = response.headers.get('Content-Disposition') ?? '';
        const filename = disposition.match(/filename="([^"]+)"/)?.[1] ?? 'document';
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);
    },
};

const withBody = (method: 'POST' | 'PUT', body: Record<string, unknown>): RequestInit => ({
    method,
    body: JSON.stringify(body),
});

/** Domain actions that sit alongside the generic /api/v1/{resource} CRUD surface. */
export const operationsApi = {
    feeSummary: <T>() => request<T>('/fee-management/summary'),
    issueInvoice: <T>(body: Record<string, unknown>) =>
        request<T>('/fee-management/invoices', withBody('POST', body)),
    issueInvoicesForClass: <T>(body: Record<string, unknown>) =>
        request<T>('/fee-management/invoices/generate', withBody('POST', body)),
    recordPayment: <T>(feeId: string, body: Record<string, unknown>) =>
        request<T>(`/fee-management/fees/${feeId}/payments`, withBody('POST', body)),
    reversePayment: <T>(paymentId: string, reason: string) =>
        request<T>(`/fee-management/payments/${paymentId}/reverse`, withBody('POST', { reason })),

    pocketBalance: <T>(cardId: string) => request<T>(`/pocket/cards/${cardId}/balance`),
    topUp: <T>(cardId: string, body: Record<string, unknown>) =>
        request<T>(`/pocket/cards/${cardId}/topups`, withBody('POST', body)),
    setLimits: <T>(cardId: string, body: Record<string, unknown>) =>
        request<T>(`/pocket/cards/${cardId}/limits`, withBody('PUT', body)),
    setCardStatus: <T>(cardId: string, status: string) =>
        request<T>(`/pocket/cards/${cardId}/status`, withBody('POST', { status })),
    cardTransactions: <T>(cardId: string) => request<T>(`/pocket/cards/${cardId}/transactions`),
    posCharge: <T>(body: Record<string, unknown>) =>
        request<T>('/store/pos/charge', withBody('POST', body)),
    refundPurchase: <T>(transactionId: string, reason: string) =>
        request<T>(`/store/transactions/${transactionId}/refund`, withBody('POST', { reason })),

    checkoutBook: <T>(body: Record<string, unknown>) =>
        request<T>('/library/loans', withBody('POST', body)),
    returnBook: <T>(loanId: string) =>
        request<T>(`/library/loans/${loanId}/return`, withBody('POST', {})),
    renewBook: <T>(loanId: string) => request<T>(`/library/loans/${loanId}/renew`, withBody('POST', {})),
    reserveBook: <T>(body: Record<string, unknown>) =>
        request<T>('/library/reservations', withBody('POST', body)),
    bookLab: <T>(body: Record<string, unknown>) =>
        request<T>('/library/lab-sessions', withBody('POST', body)),
    librarySummary: <T>() => request<T>('/library/summary'),

    operationsSummary: <T>() => request<T>('/operations/summary'),
    recordMovement: <T>(body: Record<string, unknown>) =>
        request<T>('/operations/stock/movements', withBody('POST', body)),
    approvePurchase: <T>(requestId: string, decision: string) =>
        request<T>(`/operations/purchase-requests/${requestId}/approve`, withBody('POST', { decision })),
    receivePurchase: <T>(requestId: string, quantity?: number) =>
        request<T>(
            `/operations/purchase-requests/${requestId}/receive`,
            withBody('POST', quantity ? { quantity } : {}),
        ),
    completeTask: <T>(taskId: string) =>
        request<T>(`/operations/tasks/${taskId}/complete`, withBody('POST', {})),
};

/** Timetable, marks, duty, exam and result automation, alongside generic CRUD. */
export const academicsApi = {
    summary: <T>() => request<T>('/academics/summary'),
    generateTimetable: <T>(body: Record<string, unknown>) =>
        request<T>('/academics/timetable/generate', withBody('POST', body)),
    uploadMarks: <T>(body: Record<string, unknown>) =>
        request<T>('/academics/marks/bulk', withBody('POST', body)),
    generateDuty: <T>(body: Record<string, unknown>) =>
        request<T>('/academics/duty/generate', withBody('POST', body)),
    cancelDuty: <T>(dutyId: string) =>
        request<T>(`/academics/duty/${dutyId}/cancel`, withBody('POST', {})),
    decideLeave: <T>(leaveId: string, decision: string) =>
        request<T>(`/academics/leaves/${leaveId}/decision`, withBody('POST', { decision })),
    scheduleExam: <T>(examId: string, body: Record<string, unknown>) =>
        request<T>(`/academics/exams/${examId}/schedule`, withBody('POST', body)),
    generateResults: <T>(body: Record<string, unknown>) =>
        request<T>('/academics/results/generate', withBody('POST', body)),
    publishResult: <T>(cardId: string) =>
        request<T>(`/academics/results/${cardId}/publish`, withBody('POST', {})),
    runDaily: <T>() => request<T>('/academics/automation/daily', withBody('POST', {})),

    /** Downloads the Excel-compatible CSV template prefilled with the class register. */
    downloadMarksTemplate: async (
        classId: string,
        examType: string,
        term: string,
    ): Promise<void> => {
        const query = new URLSearchParams({ classId, examType, term });
        const accessToken = tokenStore.access();
        const response = await fetch(`${BASE}/academics/marks/template?${query.toString()}`, {
            headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
        });
        if (!response.ok) {
            throw new Error(await parseError(response));
        }
        const blob = await response.blob();
        const disposition = response.headers.get('Content-Disposition') ?? '';
        const filename = disposition.match(/filename="([^"]+)"/)?.[1] ?? 'marks-template.csv';
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
    },
    importMarks: <T>(form: FormData) =>
        request<T>('/academics/marks/import', { method: 'POST', body: form }),
    generateLetters: <T>(examId: string, classId?: string) =>
        request<T>(
            '/academics/results/letters/generate',
            withBody('POST', classId ? { examId, classId } : { examId }),
        ),
};

/** Teacher-side online test preparation and attendance reporting. */
export const testsApi = {
    list: <T>() => request<T>('/tests'),
    create: <T>(body: Record<string, unknown>) =>
        request<T>('/tests', withBody('POST', body)),
    link: <T>(testId: string) => request<T>(`/tests/${testId}/link`),
    publish: <T>(testId: string) => request<T>(`/tests/${testId}/publish`, withBody('POST', {})),
    close: <T>(testId: string) => request<T>(`/tests/${testId}/close`, withBody('POST', {})),
    results: <T>(testId: string) => request<T>(`/tests/${testId}/results`),
};

/** Guardian aggregate: every linked child record in a single authorised call. */
export const parentPortalApi = {
    children: <T>() => request<T>('/parent-portal/children'),
    overview: <T>(studentId?: string) =>
        request<T>(`/parent-portal/overview${studentId ? `?studentId=${encodeURIComponent(studentId)}` : ''}`),
};

/** Student-facing endpoints reached from the signed /test/{token} link. */
export const publicTestApi = {
    preview: <T>(token: string, accessCode: string) =>
        publicRequest<T>(`/public/tests/${encodeURIComponent(token)}/preview?accessCode=${encodeURIComponent(accessCode)}`),
    start: <T>(token: string, body: Record<string, unknown>) =>
        publicRequest<T>(`/public/tests/${encodeURIComponent(token)}/start`, withBody('POST', body)),
    submit: <T>(token: string, body: Record<string, unknown>) =>
        publicRequest<T>(`/public/tests/${encodeURIComponent(token)}/submit`, withBody('POST', body)),
};
