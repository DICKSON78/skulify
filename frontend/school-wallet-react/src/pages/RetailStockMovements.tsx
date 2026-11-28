import { useCallback, useEffect, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import { api, retailApi, type RetailRow } from '../lib/api';

type Row = RetailRow;

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;
const safeText = (value: unknown): string => text(value).replace(UUID_PATTERN, 'item');

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
        const nested = wrapped.content ?? wrapped.items ?? wrapped.rows;
        if (Array.isArray(nested)) {
            return nested.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
        }
    }
    return [];
};

const typeTone = (type: string): string => {
    if (type === 'RESTOCK' || type === 'RETURN') return 'badge-success';
    if (type === 'SALE') return 'badge-info';
    if (type === 'WRITE_OFF') return 'badge-danger';
    if (type === 'ADJUST') return 'badge-warning';
    return 'badge-primary';
};

export default function RetailStockMovements() {
    const [movements, setMovements] = useState<Row[]>([]);
    const [items, setItems] = useState<Row[]>([]);
    const [productId, setProductId] = useState('');
    const [type, setType] = useState('');
    const [reconRows, setReconRows] = useState<Row[]>([]);
    const [reconBalanced, setReconBalanced] = useState<boolean | null>(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const load = useCallback(() => {
        setLoading(true);
        Promise.all([
            retailApi.stockMovements<Row[]>(),
            api.list<Row>('store-items', 0, 200, ''),
            retailApi.reconciliation<Row>().catch(() => null),
        ])
            .then(([rows, page, recon]) => {
                setMovements(asRows(rows));
                setItems(page.content);
                setReconRows(Array.isArray(recon)
                    ? asRows(recon)
                    : asRows(recon?.items ?? recon?.rows ?? recon?.products ?? recon?.details));
                setReconBalanced(!Array.isArray(recon) && typeof recon?.balanced === 'boolean'
                    ? recon.balanced
                    : null);
            })
            .catch((reason) => setError(message(reason)))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const itemName = (id: string): string => {
        const item = items.find((row) => text(row.id) === id);
        return item ? text(item.name) || text(item.sku) : 'Store item';
    };
    const itemSku = (id: string): string => {
        const item = items.find((row) => text(row.id) === id);
        return item ? text(item.sku) : '';
    };
    const visible = movements.filter((row) =>
        (!productId || text(row.productId) === productId)
        && (!type || text(row.type) === type));
    const restocks = movements.filter((row) => text(row.type) === 'RESTOCK').length;
    const sales = movements.filter((row) => text(row.type) === 'SALE').length;
    const netQuantity = movements.reduce((sum, row) => sum + num(row.quantity), 0);
    const rowVariances = reconRows.filter((row) => rowDelta(row) !== 0).length;
    const variances = reconBalanced === false
        ? Math.max(rowVariances, reconRows.length ? 1 : 0)
        : rowVariances;

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Stock Movements"
                    subtitle="Append-only ledger of receipts, sales, returns and adjustments"
                    icon="Truck"
                    actions={
                        <button type="button" className="btn-outline" onClick={load}>
                            <Icon name="ArrowClockwise" size={16} className="mr-2" /> {loading ? 'Refreshing...' : 'Refresh'}
                        </button>
                    }
                />
                <Notice error={error} success="" />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Movements" value={movements.length} icon="ListChecks" tone="primary" footer="Ledger entries" />
                    <StatTile label="Restocks" value={restocks} icon="Truck" tone="success" footer="Batch receipts" />
                    <StatTile label="Sales" value={sales} icon="Basket" tone="info" footer="Stock consumed at checkout" />
                    <StatTile label="Net quantity" value={netQuantity} icon="ArrowsLeftRight" tone="warning" footer="Signed movement total" />
                </div>

                {variances > 0 && (
                    <div className="notice warning">
                        {variances} product(s) differ between cached stock and the ledger. Review Inventory reconciliation.
                    </div>
                )}

                <FormCard title="Movement ledger" icon="Truck">
                    <p className="text-sm text-gray-500 mb-4">
                        Entries are permanent. Corrections are posted as a new adjustment, never by editing history.
                    </p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <label className="block">
                            <span className="form-label">Product</span>
                            <select className="form-input w-full" value={productId} onChange={(event) => setProductId(event.target.value)}>
                                <option value="">All products</option>
                                {items.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>{text(row.name)} · {text(row.sku)}</option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Movement type</span>
                            <select className="form-input w-full" value={type} onChange={(event) => setType(event.target.value)}>
                                <option value="">All types</option>
                                {['RESTOCK', 'SALE', 'RETURN', 'WRITE_OFF', 'ADJUST'].map((value) => (
                                    <option key={value} value={value}>{value}</option>
                                ))}
                            </select>
                        </label>
                    </div>
                    <div className="re-table-wrap mt-4">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    <th><span className="table-col"><TableIcon label="Reference" /><span>Reference</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Type" /><span>Type</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Quantity" /><span>Quantity</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Balance after" /><span>Balance after</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Batch" /><span>Batch</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Reason" /><span>Reason</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Recorded by" /><span>Recorded by</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Date" /><span>Date</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {visible.map((row) => (
                                    <tr key={text(row.id)}>
                                        <td><div className="table-cell-content"><TableIcon label="Reference" value={row.reference} /><span className="min-w-0 font-mono text-xs">{safeText(row.reference) || '—'}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Item" value={row.productId} /><span className="min-w-0 font-semibold">{itemName(text(row.productId))}<span className="block text-xs text-gray-400">{itemSku(text(row.productId))}</span></span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Type" value={row.type} /><span className="min-w-0"><span className={`badge ${typeTone(text(row.type))}`}>{text(row.type) || 'ADJUST'}</span></span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Quantity" value={row.quantity} /><span className="min-w-0 font-semibold">{num(row.quantity) > 0 ? `+${num(row.quantity)}` : num(row.quantity)}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Balance after" value={row.quantityAfter} /><span className="min-w-0">{text(row.quantityAfter) || '—'}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Batch" value={row.batchNumber} /><span className="min-w-0 font-mono text-xs">{safeText(row.batchNumber) || '—'}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Reason" value={row.reason} /><span className="min-w-0 text-muted">{safeText(row.reason) || '—'}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Recorded by" value={row.performedBy} /><span className="min-w-0">{safeText(row.performedBy) || '—'}</span></div></td>
                                        <td><div className="table-cell-content"><TableIcon label="Date" value={row.createdAt} /><span className="min-w-0">{text(row.createdAt).slice(0, 16).replace('T', ' ') || '—'}</span></div></td>
                                    </tr>
                                ))}
                                {visible.length === 0 && (
                                    <TableEmptyState colSpan={9} icon="Truck" message={movements.length ? 'No movements match these filters.' : 'No stock movements recorded yet.'} />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>
            </div>
        </Layout>
    );
}
