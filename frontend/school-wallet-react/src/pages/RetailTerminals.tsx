import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { Notice, StatTile, text } from '../components/ModuleUi';
import { newIdempotencyKey, pocketMoneyApi, retailApi, type RetailRow } from '../lib/api';

type Row = RetailRow;
type ProvisionTab = 'terminal' | 'merchant';

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
    const [provisionTab, setProvisionTab] = useState<ProvisionTab>('terminal');
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
                    badge={<span className="badge badge-success">{active} active</span>}
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Terminals" value={terminals.length} icon="Desktop" tone="primary" footer="Registered readers" />
                    <StatTile label="Active" value={active} icon="CheckCircle" tone="success" footer="Ready for checkout" />
                    <StatTile label="Merchants" value={merchants.length} icon="Buildings" tone="info" footer="Settlement accounts" />
                    <StatTile label="Last seen" value={timestamp(terminals[0]?.lastSeenAt)} icon="Clock" tone="warning" footer="Most recent terminal" />
                </div>

                <div className="retail-workbench">
                    <section className="retail-panel" aria-label="Registered terminals">
                        <div className="retail-panel-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="Desktop" size={18} /></span>
                                Terminal fleet
                            </h2>
                            <span className="retail-panel-hint">{terminals.length} readers</span>
                        </div>
                        <div className="retail-panel-body">
                            <div className="retail-flow-steps mb-4">
                                <span className="retail-flow-step retail-flow-step-active"><Icon name="Scan" size={14} /> Tap card</span>
                                <Icon name="CaretRight" size={12} />
                                <span className="retail-flow-step"><Icon name="CashRegister" size={14} /> Settle</span>
                                <Icon name="CaretRight" size={12} />
                                <span className="retail-flow-step"><Icon name="Receipt" size={14} /> Receipt</span>
                            </div>
                            <div className="retail-product-grid">
                                {terminals.map((row) => {
                                    const status = text(row.status);
                                    return (
                                        <article className="retail-product-tile" key={text(row.id) || text(row.code)}>
                                            <div className="retail-product-top">
                                                <span className="retail-product-icon"><Icon name="Desktop" size={18} /></span>
                                                <span className={`badge ${statusTone(status)}`}>{status || 'ACTIVE'}</span>
                                            </div>
                                            <div className="min-w-0">
                                                <p className="retail-product-name font-mono">{text(row.code) || 'Terminal'}</p>
                                                <p className="retail-product-meta">{merchantFor(text(row.merchantCode))}</p>
                                            </div>
                                            <div className="retail-product-stock">
                                                <span>Last seen {timestamp(row.lastSeenAt)}</span>
                                                <Icon name="Scan" size={14} />
                                            </div>
                                        </article>
                                    );
                                })}
                                {terminals.length === 0 && (
                                    <div className="retail-alert retail-alert-info col-span-full">
                                        <Icon name="Info" size={16} />
                                        <span>No readers yet. Register a merchant, then provision the first terminal.</span>
                                    </div>
                                )}
                            </div>
                        </div>
                    </section>

                    <section className="retail-panel" aria-label="Terminal provisioning">
                        <div className="retail-panel-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="Plus" size={18} /></span>
                                Provisioning
                            </h2>
                            <span className="retail-panel-hint">Settlement setup</span>
                        </div>
                        <div className="retail-panel-body">
                            <div className="tab-nav" role="tablist" aria-label="Provisioning sections">
                                <button type="button" role="tab" aria-selected={provisionTab === 'terminal'} className={`tab-btn ${provisionTab === 'terminal' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setProvisionTab('terminal')}>
                                    <Icon name="Scan" size={14} className="mr-1" /> Terminal
                                </button>
                                <button type="button" role="tab" aria-selected={provisionTab === 'merchant'} className={`tab-btn ${provisionTab === 'merchant' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setProvisionTab('merchant')}>
                                    <Icon name="Buildings" size={14} className="mr-1" /> Merchant
                                </button>
                            </div>

                            {provisionTab === 'terminal' ? (
                                <div className="grid grid-cols-1 gap-3 mt-4">
                                    <div className="retail-alert retail-alert-info">
                                        <Icon name="Info" size={16} />
                                        <span>Register the reader code and device key used by POS Checkout before the first card tap.</span>
                                    </div>
                                    <label className="block">
                                        <span className="form-label">Merchant</span>
                                        <select className="form-input w-full" value={terminal.merchantCode} onChange={(event) => setTerminal((current) => ({ ...current, merchantCode: event.target.value }))}>
                                            <option value="">Choose merchant...</option>
                                            {merchants.map((row) => (
                                                <option key={text(row.id)} value={text(row.code)}>{text(row.name)} · {text(row.code)}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <div className="grid grid-cols-2 gap-3">
                                        <label className="block">
                                            <span className="form-label">Terminal code</span>
                                            <input className="form-input w-full" value={terminal.code} onChange={(event) => setTerminal((current) => ({ ...current, code: event.target.value }))} placeholder="POS-01" />
                                        </label>
                                        <label className="block">
                                            <span className="form-label">Device key</span>
                                            <input className="form-input w-full" value={terminal.deviceKey} onChange={(event) => setTerminal((current) => ({ ...current, deviceKey: event.target.value }))} placeholder="Sensor device key" />
                                        </label>
                                    </div>
                                    <button type="button" className="btn-primary" disabled={busy} onClick={() => void registerTerminal()}>
                                        <Icon name="Plus" size={16} className="mr-2" /> Provision terminal
                                    </button>
                                </div>
                            ) : (
                                <div className="grid grid-cols-1 gap-3 mt-4">
                                    <div className="retail-alert retail-alert-info">
                                        <Icon name="Info" size={16} />
                                        <span>Merchants own the clearing account used when a card payment settles in the Pocket Money ledger.</span>
                                    </div>
                                    <div className="grid grid-cols-2 gap-3">
                                        <label className="block">
                                            <span className="form-label">Merchant code</span>
                                            <input className="form-input w-full" value={merchant.code} onChange={(event) => setMerchant((current) => ({ ...current, code: event.target.value }))} placeholder="SKUL-SHOP" />
                                        </label>
                                        <label className="block">
                                            <span className="form-label">Merchant name</span>
                                            <input className="form-input w-full" value={merchant.name} onChange={(event) => setMerchant((current) => ({ ...current, name: event.target.value }))} placeholder="School Store" />
                                        </label>
                                    </div>
                                    <label className="block">
                                        <span className="form-label">Category</span>
                                        <input className="form-input w-full" value={merchant.category} onChange={(event) => setMerchant((current) => ({ ...current, category: event.target.value }))} placeholder="GENERAL" />
                                    </label>
                                    <button type="button" className="btn-primary" disabled={busy} onClick={() => void registerMerchant()}>
                                        <Icon name="Plus" size={16} className="mr-2" /> Register merchant
                                    </button>
                                </div>
                            )}
                        </div>
                    </section>
                </div>

                <section className="retail-panel" aria-label="Merchant accounts">
                    <div className="retail-panel-header">
                        <h2 className="retail-panel-title">
                            <span className="icon-tile"><Icon name="Buildings" size={18} /></span>
                            Merchant accounts
                        </h2>
                        <span className="retail-panel-hint">{merchants.length} registered</span>
                    </div>
                    <div className="p-4">
                        <div className="re-table-wrap">
                            <table className="re-table">
                                <thead>
                                    <tr>
                                        <th><span className="table-col"><TableIcon label="Code" /><span>Code</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Merchant" /><span>Merchant</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Category" /><span>Category</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Terminals" /><span>Terminals</span></span></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {merchants.map((row) => {
                                        const code = text(row.code);
                                        return (
                                            <tr key={text(row.id) || code}>
                                                <td><div className="table-cell-content"><TableIcon label="Code" value={code} /><span className="min-w-0 font-mono text-xs font-semibold">{code || '—'}</span></div></td>
                                                <td><div className="table-cell-content"><TableIcon label="Merchant" value={row.name} /><span className="min-w-0 font-semibold text-gray-900">{text(row.name) || '—'}</span></div></td>
                                                <td><div className="table-cell-content"><TableIcon label="Category" value={row.category} /><span className="min-w-0">{text(row.category) || 'GENERAL'}</span></div></td>
                                                <td><div className="table-cell-content"><TableIcon label="Terminals" value={code} /><span className="min-w-0">{terminals.filter((terminalRow) => text(terminalRow.merchantCode) === code).length}</span></div></td>
                                            </tr>
                                        );
                                    })}
                                    {merchants.length === 0 && (
                                        <TableEmptyState colSpan={4} icon="Buildings" message="No merchants registered for this school." />
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>
            </div>
        </Layout>
    );
}
