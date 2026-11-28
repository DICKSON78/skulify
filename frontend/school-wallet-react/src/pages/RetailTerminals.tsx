import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import { newIdempotencyKey, pocketMoneyApi, retailApi, type RetailRow } from '../lib/api';

type Row = RetailRow;

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const asRows = (value: unknown): Row[] => {
    if (Array.isArray(value)) {
        return value.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
    }
    if (value && typeof value === 'object') {
        const wrapped = value as Row;
        const nested = wrapped.content ?? wrapped.items ?? wrapped.rows;
        if (Array.isArray(nested)) {
            return nested.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
        }
    }
    return [];
};

const statusTone = (status: string): string => {
    if (status === 'ACTIVE') return 'badge-success';
    if (['INACTIVE', 'MAINTENANCE', 'REVOKED'].includes(status)) return 'badge-warning';
    return 'badge-info';
};

const timestamp = (value: unknown): string =>
    text(value).slice(0, 16).replace('T', ' ') || '—';

export default function RetailTerminals() {
    const [terminals, setTerminals] = useState<Row[]>([]);
    const [merchants, setMerchants] = useState<Row[]>([]);
    const [merchant, setMerchant] = useState({ code: '', name: '', category: 'GENERAL' });
    const [terminal, setTerminal] = useState({ merchantCode: '', code: '', deviceKey: '' });
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const merchantKey = useRef<string | null>(null);
    const terminalKey = useRef<string | null>(null);

    const load = useCallback(() => {
        retailApi.terminals<Row[]>()
            .then((rows) => {
                const list = asRows(rows);
                setTerminals(list);
                setTerminal((current) => ({
                    ...current,
                    merchantCode: current.merchantCode || text(list[0]?.merchantCode),
                }));
            })
            .catch(() => pocketMoneyApi.terminals<Row[]>()
                .then((rows) => setTerminals(asRows(rows)))
                .catch((reason) => setError(message(reason))));
        pocketMoneyApi.merchants<Row[]>()
            .then((rows) => {
                setMerchants(asRows(rows));
                setTerminal((current) => ({
                    ...current,
                    merchantCode: current.merchantCode || text(asRows(rows)[0]?.code),
                }));
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const registerMerchant = async () => {
        if (!merchant.code.trim() || !merchant.name.trim()) {
            setError('Merchant code and name are required.');
            return;
        }
        setError('');
        setSuccess('');
        setBusy(true);
        try {
            const key = merchantKey.current ?? newIdempotencyKey();
            merchantKey.current = key;
            await pocketMoneyApi.registerMerchant(key, {
                code: merchant.code.trim(),
                name: merchant.name.trim(),
                category: merchant.category,
            });
            merchantKey.current = null;
            setSuccess('Merchant registered for Pocket Money settlements.');
            setMerchant({ code: '', name: '', category: 'GENERAL' });
            load();
        } catch (reason) {
            setError(message(reason));
        } finally {
            setBusy(false);
        }
    };

    const registerTerminal = async () => {
        if (!terminal.merchantCode || !terminal.code.trim() || !terminal.deviceKey.trim()) {
            setError('Merchant, terminal code and device key are required.');
            return;
        }
        setError('');
        setSuccess('');
        setBusy(true);
        try {
            const key = terminalKey.current ?? newIdempotencyKey();
            terminalKey.current = key;
            await pocketMoneyApi.registerTerminal(key, {
                merchantCode: terminal.merchantCode,
                code: terminal.code.trim(),
                deviceKey: terminal.deviceKey.trim(),
            });
            terminalKey.current = null;
            setSuccess('Terminal provisioned and ready for POS checkout.');
            setTerminal((current) => ({ ...current, code: '', deviceKey: '' }));
            load();
        } catch (reason) {
            setError(message(reason));
        } finally {
            setBusy(false);
        }
    };

    const merchantFor = (code: string): string => {
        const row = merchants.find((candidate) => text(candidate.code) === code);
        return row ? text(row.name) || code : code || '—';
    };
    const active = terminals.filter((row) => text(row.status) === 'ACTIVE').length;

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Terminals"
                    subtitle="Pocket Money merchants and the card readers registered for POS"
                    icon="Desktop"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Terminals" value={terminals.length} icon="Desktop" tone="primary" footer="Registered readers" />
                    <StatTile label="Active" value={active} icon="CheckCircle" tone="success" footer="Ready for checkout" />
                    <StatTile label="Merchants" value={merchants.length} icon="Buildings" tone="info" footer="Settlement accounts" />
                    <StatTile label="Last seen" value={timestamp(terminals[0]?.lastSeenAt)} icon="Clock" tone="warning" footer="Most recent terminal" />
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <FormCard title="Register a merchant" icon="Buildings">
                        <p className="text-sm text-gray-500 mb-4">
                            Merchants own the clearing account used when a card payment settles in the Pocket Money ledger.
                        </p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <label className="block">
                                <span className="form-label">Merchant code</span>
                                <input className="form-input w-full" value={merchant.code} onChange={(event) => setMerchant((current) => ({ ...current, code: event.target.value }))} placeholder="e.g. SKUL-SHOP" />
                            </label>
                            <label className="block">
                                <span className="form-label">Merchant name</span>
                                <input className="form-input w-full" value={merchant.name} onChange={(event) => setMerchant((current) => ({ ...current, name: event.target.value }))} placeholder="e.g. School Store" />
                            </label>
                            <label className="block sm:col-span-2">
                                <span className="form-label">Category</span>
                                <input className="form-input w-full" value={merchant.category} onChange={(event) => setMerchant((current) => ({ ...current, category: event.target.value }))} placeholder="GENERAL" />
                            </label>
                        </div>
                        <button type="button" className="btn-primary mt-4" disabled={busy} onClick={() => void registerMerchant()}>
                            <Icon name="Plus" size={16} className="mr-2" /> Register merchant
                        </button>
                    </FormCard>

                    <FormCard title="Provision a terminal" icon="Scan">
                        <p className="text-sm text-gray-500 mb-4">
                            Register the reader code and device key used by POS Checkout before the first card tap.
                        </p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <label className="block sm:col-span-2">
                                <span className="form-label">Merchant</span>
                                <select className="form-input w-full" value={terminal.merchantCode} onChange={(event) => setTerminal((current) => ({ ...current, merchantCode: event.target.value }))}>
                                    <option value="">Choose merchant...</option>
                                    {merchants.map((row) => (
                                        <option key={text(row.id)} value={text(row.code)}>{text(row.name)} · {text(row.code)}</option>
                                    ))}
                                </select>
                            </label>
                            <label className="block">
                                <span className="form-label">Terminal code</span>
                                <input className="form-input w-full" value={terminal.code} onChange={(event) => setTerminal((current) => ({ ...current, code: event.target.value }))} placeholder="e.g. POS-01" />
                            </label>
                            <label className="block">
                                <span className="form-label">Device key</span>
                                <input className="form-input w-full" value={terminal.deviceKey} onChange={(event) => setTerminal((current) => ({ ...current, deviceKey: event.target.value }))} placeholder="Sensor device key" />
                            </label>
                        </div>
                        <button type="button" className="btn-primary mt-4" disabled={busy} onClick={() => void registerTerminal()}>
                            <Icon name="Plus" size={16} className="mr-2" /> Provision terminal
                        </button>
                    </FormCard>
                </div>

                <FormCard title="Registered terminals" icon="ListChecks">
                    <div className="re-table-wrap">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    <th><span className="table-col"><TableIcon label="Terminal" /><span>Terminal</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Merchant" /><span>Merchant</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Last seen" /><span>Last seen</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {terminals.map((row) => {
                                    const status = text(row.status);
                                    return (
                                        <tr key={text(row.id) || text(row.code)}>
                                            <td><div className="table-cell-content"><TableIcon label="Terminal" value={row.code} /><span className="min-w-0 font-mono text-xs font-semibold">{text(row.code) || '—'}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Merchant" value={row.merchantCode} /><span className="min-w-0">{merchantFor(text(row.merchantCode))}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Status" value={status} /><span className="min-w-0"><span className={`badge ${statusTone(status)}`}>{status || 'ACTIVE'}</span></span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Last seen" value={row.lastSeenAt} /><span className="min-w-0">{timestamp(row.lastSeenAt)}</span></div></td>
                                        </tr>
                                    );
                                })}
                                {terminals.length === 0 && (
                                    <TableEmptyState colSpan={4} icon="Desktop" message="No terminals yet. Register a merchant and provision a reader." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>

                <FormCard title="Merchant accounts" icon="Buildings">
                    <div className="re-table-wrap">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    <th><span className="table-col"><TableIcon label="Code" /><span>Code</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Merchant" /><span>Merchant</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Category" /><span>Category</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {merchants.map((row) => (
                                    <tr key={text(row.id) || text(row.code)}>
                                        <td><div className="table-cell-content"><TableIcon label="Code" value={row.code} /><span className="min-w-0 font-mono text-xs font-semibold">{text(row.code) || '—'}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Merchant" value={row.name} /><span className="min-w-0 font-semibold text-gray-900">{text(row.name) || '—'}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Category" value={row.category} /><span className="min-w-0">{text(row.category) || 'GENERAL'}</span></div></td>
                                    </tr>
                                ))}
                                {merchants.length === 0 && (
                                    <TableEmptyState colSpan={3} icon="Buildings" message="No merchants registered for this school." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>
            </div>
        </Layout>
    );
}
