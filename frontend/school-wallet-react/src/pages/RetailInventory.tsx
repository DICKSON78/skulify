import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { Notice, StatTile, money, text } from '../components/ModuleUi';
import { api, retailApi, type RetailRow } from '../lib/api';

type Row = RetailRow;
type Tab = 'onhand' | 'batches' | 'reconciliation';

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const num = (value: unknown): number => Number(value) || 0;

const rowDelta = (row: Row): number => {
    const cached = row.cachedStock ?? row.cachedQuantity ?? row.cached ?? row.stockQuantity;
    const ledger = row.ledgerStock ?? row.ledgerQuantity ?? row.ledger;
    if (cached != null && ledger != null) return num(cached) - num(ledger);
    const legacy = row.delta ?? row.difference ?? row.variance ?? row.discrepancy;
    return legacy == null ? 0 : num(legacy);
};

const asRows = (value: unknown): Row[] => {
    if (Array.isArray(value)) {
        return value.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
    }
    if (value && typeof value === 'object') {
        const wrapped = value as Row;
        const nested = wrapped.content ?? wrapped.items ?? wrapped.rows ?? wrapped.products ?? wrapped.details;
        if (Array.isArray(nested)) {
            return nested.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
        }
    }
    return [];
};

const productName = (id: string, items: Row[]): string => {
    const item = items.find((row) => text(row.id) === id);
    return item ? text(item.name) || text(item.sku) : 'Product';
};

const productSku = (id: string, items: Row[]): string => {
    const item = items.find((row) => text(row.id) === id);
    return item ? text(item.sku) : '';
};

const expiryInfo = (date: string): { tone: string; label: string } => {
    if (!date) return { tone: 'badge-gray', label: 'No expiry' };
    const days = Math.ceil((new Date(date).getTime() - Date.now()) / 86_400_000);
    if (Number.isNaN(days) || days < 0) return { tone: 'badge-danger', label: 'Expired' };
    if (days <= 30) return { tone: 'badge-warning', label: `${days}d left` };
    return { tone: 'badge-success', label: text(date) };
};

export default function RetailInventory() {
    const [tab, setTab] = useState<Tab>('onhand');
    const [items, setItems] = useState<Row[]>([]);
    const [lowStock, setLowStock] = useState<Row[]>([]);
    const [batches, setBatches] = useState<Row[]>([]);
    const [reconciliation, setReconciliation] = useState<Row | null>(null);
    const [search, setSearch] = useState('');
    const [category, setCategory] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [loading, setLoading] = useState(false);

    const load = useCallback(() => {
        setLoading(true);
        Promise.all([
            api.list<Row>('store-items', 0, 200, ''),
            retailApi.lowStock<Row[]>().catch(() => []),
            retailApi.batches<Row[]>().catch(() => []),
            retailApi.reconciliation<Row>().catch(() => null),
        ])
            .then(([page, low, batchRows, recon]) => {
                setItems(page.content);
                setLowStock(low.length > 0 ? asRows(low) : page.content.filter(
                    (row) => num(row.stockQuantity) <= num(row.reorderLevel)));
                setBatches(asRows(batchRows));
                setReconciliation(recon);
            })
            .catch((reason) => setError(message(reason)))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const term = search.trim().toLowerCase();
    const categories = Array.from(new Set(
        items.map((row) => text(row.category)).filter(Boolean),
    )).slice(0, 8);
    const visibleItems = items
        .filter((row) => !category || text(row.category) === category)
        .filter((row) => !term
            || [row.sku, row.name, row.category].some((value) => text(value).toLowerCase().includes(term)));
    const stockValue = items.reduce((sum, row) => sum + num(row.stockQuantity) * num(row.unitCost ?? row.price), 0);
    const reconRows = Array.isArray(reconciliation)
        ? asRows(reconciliation)
        : asRows(reconciliation?.items ?? reconciliation?.rows ?? reconciliation?.products ?? reconciliation?.details);
    const rowVariances = reconRows.filter((row) => rowDelta(row) !== 0).length;
    const varianceCount = reconciliation && reconciliation.balanced === false
        ? Math.max(rowVariances, reconRows.length ? 1 : 0)
        : rowVariances;
    const expiringSoon = batches.filter((row) => {
        const date = text(row.expiryDate);
        if (!date) return false;
        const days = Math.ceil((new Date(date).getTime() - Date.now()) / 86_400_000);
        return days <= 30;
    }).length;
    const sortedBatches = [...batches].sort((a, b) =>
        text(a.expiryDate || '9999').localeCompare(text(b.expiryDate || '9999')));
    const lowStockWatch = lowStock.length > 0 ? lowStock : items.filter(
        (row) => num(row.stockQuantity) <= num(row.reorderLevel));

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Inventory"
                    subtitle="On-hand stock, batch expiry and ledger reconciliation"
                    icon="Package"
                    badge={<span className={`badge ${varianceCount ? 'badge-danger' : 'badge-success'}`}>{varianceCount ? `${varianceCount} variances` : 'Balanced'}</span>}
                    actions={
                        <button type="button" className="btn-outline" onClick={() => { setSuccess(''); load(); }}>
                            <Icon name="ArrowClockwise" size={16} className="mr-2" /> {loading ? 'Refreshing...' : 'Refresh'}
                        </button>
                    }
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Products" value={items.length} icon="Package" tone="primary" footer="Tracked store items" />
                    <StatTile label="Low stock" value={lowStockWatch.length} icon="Warning" tone="warning" footer="Need a restock" />
                    <StatTile label="Expiring soon" value={expiringSoon} icon="Hourglass" tone="info" footer="Batches within 30 days" />
                    <StatTile label="Reconciliation" value={varianceCount ? `${varianceCount} off` : 'Matched'} icon={varianceCount ? 'Warning' : 'CheckCircle'} tone={varianceCount ? 'danger' : 'success'} footer="Cached stock vs ledger" />
                </div>

                <div className="tab-nav parent-subtabs" role="tablist" aria-label="Inventory sections">
                    <button type="button" role="tab" aria-selected={tab === 'onhand'} className={`tab-btn ${tab === 'onhand' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('onhand')}>
                        <Icon name="Package" size={16} className="mr-2" /> On hand
                    </button>
                    <button type="button" role="tab" aria-selected={tab === 'batches'} className={`tab-btn ${tab === 'batches' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('batches')}>
                        <Icon name="Stack" size={16} className="mr-2" /> Batch expiry
                    </button>
                    <button type="button" role="tab" aria-selected={tab === 'reconciliation'} className={`tab-btn ${tab === 'reconciliation' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('reconciliation')}>
                        <Icon name="ArrowsLeftRight" size={16} className="mr-2" /> Reconciliation
                    </button>
                </div>

                {tab === 'onhand' && (
                    <div className="retail-workbench">
                        <section className="retail-panel" aria-label="On-hand stock">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Package" size={18} /></span>
                                    On-hand stock
                                </h2>
                                <span className="retail-panel-hint">{visibleItems.length} shown</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="retail-search-row">
                                    <Icon name="MagnifyingGlass" size={18} className="text-muted" />
                                    <input className="form-input" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search by SKU, name or category..." aria-label="Search inventory" />
                                </div>
                                {categories.length > 0 && (
                                    <div className="retail-flow-steps mt-3">
                                        <button type="button" className={`retail-flow-step ${!category ? 'retail-flow-step-active' : ''}`} onClick={() => setCategory('')}>All</button>
                                        {categories.map((value) => (
                                            <button key={value} type="button" className={`retail-flow-step ${category === value ? 'retail-flow-step-active' : ''}`} onClick={() => setCategory(value)}>{value}</button>
                                        ))}
                                    </div>
                                )}
                                <div className="re-table-wrap mt-4">
                                    <table className="re-table">
                                        <thead>
                                            <tr>
                                                <th><span className="table-col"><TableIcon label="SKU" /><span>SKU</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                                <th><span className="table-col"><TableIcon label="On hand" /><span>On hand</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Reorder at" /><span>Reorder at</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Stock value" /><span>Stock value</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {visibleItems.map((row) => {
                                                const low = num(row.stockQuantity) <= num(row.reorderLevel);
                                                return (
                                                    <tr key={text(row.id)}>
                                                        <td><div className="table-cell-content"><TableIcon label="SKU" value={row.sku} /><span className="min-w-0 font-mono text-xs">{text(row.sku) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Item" value={row.name} /><span className="min-w-0 font-semibold text-gray-900">{text(row.name) || '—'}<span className="block text-xs text-gray-400">{text(row.category) || 'Store item'}</span></span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="On hand" value={row.stockQuantity} /><span className="min-w-0 font-semibold">{text(row.stockQuantity)}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Reorder at" value={row.reorderLevel} /><span className="min-w-0">{text(row.reorderLevel)}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Stock value" value={row.stockQuantity} /><span className="min-w-0">{money(num(row.stockQuantity) * num(row.unitCost ?? row.price))}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Status" value={low ? 'REORDER' : 'ACTIVE'} /><span className="min-w-0"><span className={`badge ${low ? 'badge-warning' : 'badge-success'}`}>{low ? 'REORDER' : 'IN STOCK'}</span></span></div></td>
                                                    </tr>
                                                );
                                            })}
                                            {visibleItems.length === 0 && (
                                                <TableEmptyState colSpan={6} icon="Package" message="No inventory items match that search." />
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </section>

                        <section className="retail-panel" aria-label="Stock alerts">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Warning" size={18} /></span>
                                    Stock alerts
                                </h2>
                                <span className="badge badge-warning">{lowStockWatch.length} low</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="retail-detail-list">
                                    {lowStockWatch.slice(0, 8).map((row, index) => (
                                        <div className="retail-detail-row" key={text(row.productId ?? row.id) || index}>
                                            <span><TableIcon label="Item" value={row.name ?? row.productId} />{productName(text(row.productId ?? row.id), items)}</span>
                                            <strong>{text(row.stockQuantity ?? row.quantity)} / {text(row.reorderLevel) || '—'}</strong>
                                        </div>
                                    ))}
                                    {lowStockWatch.length === 0 && (
                                        <div className="retail-alert retail-alert-info">
                                            <Icon name="CheckCircle" size={16} />
                                            <span>Every product is above its reorder level.</span>
                                        </div>
                                    )}
                                </div>
                                <div className="retail-panel-footer mt-4 -mx-5 -mb-5">
                                    <Link className="btn-outline w-full" to="/store-items">
                                        <Icon name="Truck" size={16} className="mr-2" /> Open stock desk
                                    </Link>
                                </div>
                            </div>
                        </section>
                    </div>
                )}

                {tab === 'batches' && (
                    <div className="retail-workbench">
                        <section className="retail-panel" aria-label="Batch expiry view">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Stack" size={18} /></span>
                                    Batch expiry view
                                </h2>
                                <span className="retail-panel-hint">FEFO order</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="re-table-wrap">
                                    <table className="re-table">
                                        <thead>
                                            <tr>
                                                <th><span className="table-col"><TableIcon label="Batch" /><span>Batch</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Remaining" /><span>Remaining</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Expiry" /><span>Expiry</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Received" /><span>Received</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {sortedBatches.map((row) => {
                                                const expiry = expiryInfo(text(row.expiryDate));
                                                return (
                                                    <tr key={text(row.batchNumber) || text(row.id)}>
                                                        <td><div className="table-cell-content"><TableIcon label="Batch" value={row.batchNumber} /><span className="min-w-0 font-mono text-xs">{text(row.batchNumber) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Item" value={row.productId} /><span className="min-w-0">{productName(text(row.productId), items)}<span className="block text-xs text-gray-400">{productSku(text(row.productId), items)}</span></span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Remaining" value={row.quantityRemaining ?? row.quantity} /><span className="min-w-0 font-semibold">{text(row.quantityRemaining ?? row.quantity)}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Expiry" value={row.expiryDate} /><span className="min-w-0"><span className={`badge ${expiry.tone}`}>{expiry.label}</span></span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Received" value={row.receivedAt} /><span className="min-w-0">{text(row.receivedAt).slice(0, 10) || '—'}</span></div></td>
                                                    </tr>
                                                );
                                            })}
                                            {sortedBatches.length === 0 && (
                                                <TableEmptyState colSpan={5} icon="Stack" message="No FEFO batches have been received yet." />
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </section>

                        <section className="retail-panel" aria-label="Next expiry queue">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Hourglass" size={18} /></span>
                                    Next to sell
                                </h2>
                                <span className="badge badge-warning">{expiringSoon} soon</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="retail-alert retail-alert-info">
                                    <Icon name="Info" size={16} />
                                    <span>Checkout consumes the earliest expiring batch first. This queue shows what will be picked next.</span>
                                </div>
                                <div className="retail-detail-list mt-4">
                                    {sortedBatches.slice(0, 8).map((row, index) => (
                                        <div className="retail-detail-row" key={`${text(row.batchNumber)}-${index}`}>
                                            <span><TableIcon label="Batch" value={row.batchNumber} />{text(row.batchNumber) || 'Batch'}</span>
                                            <strong className={`badge ${expiryInfo(text(row.expiryDate)).tone}`}>{expiryInfo(text(row.expiryDate)).label}</strong>
                                        </div>
                                    ))}
                                    {sortedBatches.length === 0 && (
                                        <div className="retail-alert">
                                            <Icon name="Stack" size={16} />
                                            <span>No batches available for FEFO picking.</span>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </section>
                    </div>
                )}

                {tab === 'reconciliation' && (
                    <div className="space-y-4">
                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                            <StatTile label="Checked items" value={reconRows.length || num(reconciliation?.count ?? reconciliation?.total)} icon="ListChecks" tone="primary" footer="Cached vs ledger" />
                            <StatTile label="Matched" value={reconRows.length - varianceCount} icon="CheckCircle" tone="success" footer="Balanced products" />
                            <StatTile label="Variances" value={varianceCount} icon="Warning" tone="warning" footer="Need investigation" />
                            <StatTile label="Valuation" value={money(stockValue)} icon="Coins" tone="info" footer="At latest cost or price" />
                        </div>
                        <div className="retail-workbench">
                            <section className="retail-panel" aria-label="Stock reconciliation">
                                <div className="retail-panel-header">
                                    <h2 className="retail-panel-title">
                                        <span className="icon-tile"><Icon name="ArrowsLeftRight" size={18} /></span>
                                        Stock reconciliation
                                    </h2>
                                    <span className={`badge ${varianceCount ? 'badge-danger' : 'badge-success'}`}>{varianceCount ? 'Review' : 'Matched'}</span>
                                </div>
                                <div className="retail-panel-body">
                                    <div className="retail-alert retail-alert-info mb-4">
                                        <Icon name="Info" size={16} />
                                        <span>Cached store quantities are compared with the sum of append-only stock movements.</span>
                                    </div>
                                    <div className="re-table-wrap">
                                        <table className="re-table">
                                            <thead>
                                                <tr>
                                                    <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Cached" /><span>Cached</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Ledger" /><span>Ledger</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Variance" /><span>Variance</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {reconRows.map((row, index) => {
                                                    const difference = rowDelta(row);
                                                    const status = text(row.status).toUpperCase() || (difference === 0 ? 'MATCHED' : 'MISMATCH');
                                                    const balanced = reconciliation?.balanced === true
                                                        || ['MATCHED', 'OK', 'BALANCED'].includes(status)
                                                        || difference === 0;
                                                    return (
                                                        <tr key={text(row.productId) || index}>
                                                            <td><div className="table-cell-content"><TableIcon label="Item" value={row.productId} /><span className="min-w-0 font-semibold">{text(row.name) || productName(text(row.productId), items)}</span></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Cached" value={row.cachedStock ?? row.stockQuantity} /><span className="min-w-0">{text(row.cachedStock ?? row.cachedQuantity ?? row.cached ?? row.stockQuantity)}</span></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Ledger" value={row.ledgerStock ?? row.ledger} /><span className="min-w-0">{text(row.ledgerStock ?? row.ledgerQuantity ?? row.ledger)}</span></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Variance" value={difference} /><span className="min-w-0 font-semibold">{difference}</span></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Status" value={status} /><span className="min-w-0"><span className={`badge ${balanced ? 'badge-success' : 'badge-danger'}`}>{status}</span></span></div></td>
                                                        </tr>
                                                    );
                                                })}
                                                {reconRows.length === 0 && (
                                                    <TableEmptyState colSpan={5} icon="ArrowsLeftRight" message="No reconciliation rows are available yet." />
                                                )}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </section>

                            <section className="retail-panel" aria-label="Ledger health">
                                <div className="retail-panel-header">
                                    <h2 className="retail-panel-title">
                                        <span className="icon-tile"><Icon name="Shield" size={18} /></span>
                                        Ledger health
                                    </h2>
                                </div>
                                <div className="retail-panel-body">
                                    <div className="retail-keyval-grid">
                                        <div className="retail-keyval"><span>Cache status</span><strong>{varianceCount ? 'Needs review' : 'Balanced'}</strong></div>
                                        <div className="retail-keyval"><span>Items checked</span><strong>{reconRows.length}</strong></div>
                                        <div className="retail-keyval"><span>Valuation</span><strong>{money(stockValue)}</strong></div>
                                    </div>
                                    <div className={`retail-alert mt-4 ${varianceCount ? 'retail-alert-warning' : 'retail-alert-info'}`}>
                                        <Icon name={varianceCount ? 'Warning' : 'CheckCircle'} size={16} />
                                        <span>{varianceCount
                                            ? 'Cached quantities differ from the movement ledger. Post an adjustment rather than editing history.'
                                            : 'Every cached quantity matches its append-only movement ledger.'}</span>
                                    </div>
                                    <div className="retail-panel-footer mt-4 -mx-5 -mb-5">
                                        <Link className="btn-outline w-full" to="/stock-movements">
                                            <Icon name="Truck" size={16} className="mr-2" /> View movement ledger
                                        </Link>
                                    </div>
                                </div>
                            </section>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}
