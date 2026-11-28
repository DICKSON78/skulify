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

export type ApiError = Error & {
    code?: string;
    details?: unknown;
    status?: number;
};

async function parseError(response: Response): Promise<ApiError> {
    let message = `Request failed (${response.status})`;
    let code: string | undefined;
    let details: unknown;
    try {
        const body = await response.json();
        message = body.detail ?? body.message ?? body.title ?? message;
        code = body.code ?? body.errorCode ?? body.error;
        details = body.details ?? body.data;
    } catch {
        // keep the generic message
    }
    const error = new Error(message) as ApiError;
    error.code = code;
    error.details = details;
    error.status = response.status;
    return error;
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
        throw await parseError(response);
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
        throw await parseError(response);
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
            throw await parseError(response);
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
            throw await parseError(response);
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
            throw await parseError(response);
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
            throw await parseError(response);
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

/** Class-teacher attendance register with semester-week based progress. */
export const classAttendanceApi = {
    overview: <T>(classId?: string, termId?: string, week?: number) => {
        const params = new URLSearchParams();
        if (classId) params.set('classId', classId);
        if (termId) params.set('termId', termId);
        if (week) params.set('week', String(week));
        const query = params.toString();
        return request<T>(`/class-attendance${query ? `?${query}` : ''}`);
    },
    save: <T>(body: Record<string, unknown>) =>
        request<T>('/class-attendance', withBody('POST', body)),
};

/** Ledger-backed Pocket Money API used by the newer money flows. */
export const pocketMoneyApi = {
    wallet: <T>(studentId: string) => request<T>(`/pocket-money/wallets/student/${studentId}`),
    ledger: <T>(walletId: string) => request<T>(`/pocket-money/wallets/${walletId}/ledger`),
    topUps: <T>(studentId?: string, walletId?: string) => {
        const params = new URLSearchParams();
        if (studentId) params.set('studentId', studentId);
        if (walletId) params.set('walletId', walletId);
        const query = params.toString();
        return request<T>(`/pocket-money/topups${query ? `?${query}` : ''}`);
    },
    openWallet: <T>(key: string, body: Record<string, unknown>) =>
        request<T>('/pocket-money/wallets', {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
    topUp: <T>(key: string, body: Record<string, unknown>) =>
        request<T>('/pocket-money/topups', {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
    requestTopUp: <T>(key: string, body: Record<string, unknown>) =>
        request<T>('/pocket-money/topups/request', {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
    approveTopUp: <T>(id: string, key: string) =>
        request<T>(`/pocket-money/topups/${id}/approve`, {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
        }),
    declineTopUp: <T>(id: string, key: string) =>
        request<T>(`/pocket-money/topups/${id}/decline`, {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
        }),
    reconciliation: <T>(walletId: string) =>
        request<T>(`/pocket-money/reconciliation/${walletId}`),
    cards: <T>() => request<T>('/pocket-money/cards'),
    issueCard: <T>(key: string, body: Record<string, unknown>) =>
        request<T>('/pocket-money/cards', {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
    merchants: <T>() => request<T>('/pocket-money/merchants', { cache: 'no-store' }),
    registerMerchant: <T>(key: string, body: Record<string, unknown>) =>
        request<T>('/pocket-money/merchants', {
            method: 'POST',
            cache: 'no-store',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
    terminals: <T>() => request<T>('/pocket-money/terminals', { cache: 'no-store' }),
    registerTerminal: <T>(key: string, body: Record<string, unknown>) =>
        request<T>('/pocket-money/terminals', {
            method: 'POST',
            cache: 'no-store',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
    purchases: <T>(studentId?: string) => {
        const query = studentId ? `?studentId=${encodeURIComponent(studentId)}` : '';
        return request<T>(`/pocket-money/purchases${query}`);
    },
    purchase: <T>(key: string, body: Record<string, unknown>) =>
        request<T>('/pocket-money/purchases', {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
    updateLimits: <T>(walletId: string, key: string, body: Record<string, unknown>) =>
        request<T>(`/pocket-money/wallets/${walletId}/limits`, {
            method: 'PUT',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify(body),
        }),
};

export type RetailRow = Record<string, unknown>;
export type RetailCartItem = { productId: string; quantity: number };
export type RetailPreviewLine = {
    productId: string;
    sku?: string;
    name?: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
    availableStock?: number;
};
export type RetailPreview = {
    pricingFingerprint?: string;
    fingerprint?: string;
    subtotal: number;
    discount: number;
    total: number;
    currency?: string;
    lines: RetailPreviewLine[];
    merchantCode?: string;
    terminalCode?: string;
};

/** Fresh key per business action; callers keep it for retries of that action. */
export function newIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

const retailWrite = (body: Record<string, unknown>, key?: string): RequestInit => ({
    method: 'POST',
    cache: 'no-store',
    headers: key ? { 'Idempotency-Key': key } : {},
    body: JSON.stringify(body),
});

const retailAction = (key: string): RequestInit => ({
    method: 'POST',
    cache: 'no-store',
    headers: { 'Idempotency-Key': key },
});

const retailQuery = (values: Record<string, string | number | undefined>): string => {
    const params = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
            params.set(key, String(value));
        }
    });
    const query = params.toString();
    return query ? `?${query}` : '';
};

/**
 * Retail engine: server-side pricing, FEFO batches and the stock ledger.
 * Money always settles through the existing Pocket Money ledger.
 */
export const retailApi = {
    preview: <T = RetailPreview>(body: Record<string, unknown>) =>
        request<T>('/retail/sales/preview', retailWrite(body)),
    createSale: <T = RetailRow>(key: string, body: Record<string, unknown>) =>
        request<T>('/retail/sales', retailWrite(body, key)),
    sales: <T = RetailRow[]>(studentId?: string, limit?: number) =>
        request<T>(`/retail/sales${retailQuery({ studentId, limit })}`, { cache: 'no-store' }),
    sale: <T = RetailRow>(id: string) =>
        request<T>(`/retail/sales/${encodeURIComponent(id)}`, { cache: 'no-store' }),
    receipt: <T = RetailRow>(id: string) =>
        request<T>(`/retail/sales/${encodeURIComponent(id)}/receipt`, { cache: 'no-store' }),
    printReceipt: async (id: string): Promise<void> => {
        const accessToken = tokenStore.access();
        const response = await fetch(
            `${BASE}/retail/sales/${encodeURIComponent(id)}/receipt/print`,
            {
                cache: 'no-store',
                headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
            },
        );
        if (!response.ok) {
            throw await parseError(response);
        }
        const html = await response.text();
        const target = window.open('', '_blank');
        if (!target) {
            throw new Error('Allow pop-ups to open the printable receipt.');
        }
        target.document.open();
        target.document.write(html);
        target.document.close();
        target.focus();
    },
    returnSale: <T = RetailRow>(id: string, key: string, body: Record<string, unknown>) =>
        request<T>(`/retail/sales/${encodeURIComponent(id)}/return`, retailWrite(body, key)),

    restock: <T = RetailRow>(key: string, body: Record<string, unknown>) =>
        request<T>('/retail/stock/restock', retailWrite(body, key)),
    adjust: <T = RetailRow>(key: string, body: Record<string, unknown>) =>
        request<T>('/retail/stock/adjust', retailWrite(body, key)),
    batches: <T = RetailRow[]>(productId?: string) =>
        request<T>(`/retail/batches${retailQuery({ productId })}`, { cache: 'no-store' }),
    stockMovements: <T = RetailRow[]>(productId?: string) =>
        request<T>(`/retail/stock-movements${retailQuery({ productId })}`, { cache: 'no-store' }),
    reconciliation: <T = RetailRow>() =>
        request<T>('/retail/stock/reconciliation', { cache: 'no-store' }),
    lowStock: <T = RetailRow[]>() =>
        request<T>('/retail/low-stock', { cache: 'no-store' }),

    suppliers: <T = RetailRow[]>() =>
        request<T>('/retail/suppliers', { cache: 'no-store' }),
    createSupplier: <T = RetailRow>(key: string, body: Record<string, unknown>) =>
        request<T>('/retail/suppliers', retailWrite(body, key)),
    discounts: <T = RetailRow[]>() =>
        request<T>('/retail/discounts', { cache: 'no-store' }),
    createDiscount: <T = RetailRow>(key: string, body: Record<string, unknown>) =>
        request<T>('/retail/discounts', retailWrite(body, key)),

    purchaseOrders: <T = RetailRow[]>() =>
        request<T>('/retail/purchase-orders', { cache: 'no-store' }),
    purchaseOrder: <T = RetailRow>(id: string) =>
        request<T>(`/retail/purchase-orders/${encodeURIComponent(id)}`, { cache: 'no-store' }),
    createPurchaseOrder: <T = RetailRow>(key: string, body: Record<string, unknown>) =>
        request<T>('/retail/purchase-orders', retailWrite(body, key)),
    approvePurchaseOrder: <T = RetailRow>(id: string, key: string) =>
        request<T>(`/retail/purchase-orders/${encodeURIComponent(id)}/approve`, retailAction(key)),
    receivePurchaseOrder: <T = RetailRow>(id: string, key: string, body: Record<string, unknown>) =>
        request<T>(`/retail/purchase-orders/${encodeURIComponent(id)}/receive`, retailWrite(body, key)),
    cancelPurchaseOrder: <T = RetailRow>(id: string, key: string) =>
        request<T>(`/retail/purchase-orders/${encodeURIComponent(id)}/cancel`, retailAction(key)),

    terminals: <T = RetailRow[]>() =>
        request<T>('/retail/terminals', { cache: 'no-store' }),
};

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
    pushUssdTopUp: <T>(cardId: string, body: Record<string, unknown>) =>
        request<T>(`/pocket/cards/${cardId}/topups/request`, withBody('POST', body)),
    approveTopUp: <T>(transactionId: string) =>
        request<T>(`/pocket/topups/${transactionId}/approve`, withBody('POST', {})),
    declineTopUp: <T>(transactionId: string) =>
        request<T>(`/pocket/topups/${transactionId}/decline`, withBody('POST', {})),
    cashRequests: <T>() => request<T>('/cash-requests'),
    requestCash: <T>(body: Record<string, unknown>) =>
        request<T>('/cash-requests', withBody('POST', body)),
    decideCashRequest: <T>(id: string, decision: string, note: string) =>
        request<T>(`/cash-requests/${id}/decide`, withBody('POST', { decision, note })),
    payoutCashRequest: <T>(id: string, agentCode: string, note: string) =>
        request<T>(`/cash-requests/${id}/payout`, withBody('POST', { agentCode, note })),
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
            throw await parseError(response);
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
