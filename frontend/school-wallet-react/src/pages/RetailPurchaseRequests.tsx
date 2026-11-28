import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { Notice, StatTile, money, text } from '../components/ModuleUi';
import { api, newIdempotencyKey, retailApi, type RetailRow } from '../lib/api';

type Row = RetailRow;
type Tab = 'orders' | 'suppliers' | 'discounts';
type DraftLine = { productId: string; quantity: string; unitCost: string };
type ReceiveValue = { quantity: string; batchNumber: string; expiryDate: string };
type Receiving = { order: Row; lines: Row[]; values: Record<string, ReceiveValue> };

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const num = (value: unknown): number => Number(value) || 0;

const asRows = (value: unknown): Row[] => {
    if (Array.isArray(value)) {
        return value.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
    }
    if (value && typeof value === 'object') {
        const wrapped = value as Row;
        const nested = wrapped.content ?? wrapped.items ?? wrapped.rows ?? wrapped.orderItems ?? wrapped.lines;
        if (Array.isArray(nested)) {
            return nested.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
        }
    }
    return [];
};

const orderLines = (order: Row): Row[] =>
    asRows(order.items ?? order.orderItems ?? order.lines);

const statusTone = (status: string): string => {
    if (status === 'RECEIVED' || status === 'APPROVED') return 'badge-success';
    if (status === 'OPEN' || status === 'PENDING') return 'badge-warning';
    if (status === 'PARTIALLY_RECEIVED') return 'badge-info';
    if (['CANCELLED', 'CANCELED', 'EXPIRED'].includes(status)) return 'badge-danger';
    return 'badge-primary';
};

export default function RetailPurchaseRequests() {
    const [tab, setTab] = useState<Tab>('orders');
    const [orders, setOrders] = useState<Row[]>([]);
    const [suppliers, setSuppliers] = useState<Row[]>([]);
    const [items, setItems] = useState<Row[]>([]);
    const [discounts, setDiscounts] = useState<Row[]>([]);
    const [supplierForm, setSupplierForm] = useState({ code: '', name: '', phone: '', email: '', status: 'ACTIVE' });
    const [orderDraft, setOrderDraft] = useState({
        supplierId: '', reference: '', notes: '',
        lines: [{ productId: '', quantity: '1', unitCost: '' }] as DraftLine[],
    });
    const [discountForm, setDiscountForm] = useState({ code: '', kind: 'PERCENT', value: '', expiresAt: '', status: 'ACTIVE' });
    const [receiving, setReceiving] = useState<Receiving | null>(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const actionKeys = useRef<Record<string, string>>({});

    const keyFor = (action: string, id: string): string => {
        const key = `${action}:${id}`;
        if (!actionKeys.current[key]) {
            actionKeys.current[key] = newIdempotencyKey();
        }
        return actionKeys.current[key];
    };
    const clearKey = (action: string, id: string) => {
        delete actionKeys.current[`${action}:${id}`];
    };

    const load = useCallback(() => {
        retailApi.purchaseOrders<Row[]>()
            .then((rows) => setOrders(asRows(rows)))
            .catch((reason) => setError(message(reason)));
        retailApi.suppliers<Row[]>()
            .then((rows) => {
                setSuppliers(asRows(rows));
                setOrderDraft((current) => ({ ...current, supplierId: current.supplierId || text(asRows(rows)[0]?.id) }));
            })
            .catch(() => undefined);
        retailApi.discounts<Row[]>()
            .then((rows) => setDiscounts(asRows(rows)))
            .catch(() => undefined);
        api.list<Row>('store-items', 0, 200, '')
            .then((page) => {
                setItems(page.content);
                setOrderDraft((current) => ({
                    ...current,
                    lines: current.lines.map((line) => ({
                        ...line,
                        productId: line.productId || text(page.content[0]?.id),
                    })),
                }));
            })
            .catch(() => undefined);
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const run = async (action: () => Promise<unknown>, done: string, after?: () => void) => {
        setError('');
        setSuccess('');
        setBusy(true);
        try {
            await action();
            setSuccess(done);
            after?.();
            load();
        } catch (reason) {
            setError(message(reason));
        } finally {
            setBusy(false);
        }
    };

    const supplierName = (id: string): string => {
        const supplier = suppliers.find((row) => text(row.id) === id);
        return supplier ? text(supplier.name) || text(supplier.code) : '—';
    };
    const productName = (id: string): string => {
        const item = items.find((row) => text(row.id) === id);
        return item ? text(item.name) || text(item.sku) : 'Store item';
    };
    const productSku = (id: string): string => {
        const item = items.find((row) => text(row.id) === id);
        return item ? text(item.sku) : '';
    };

    const createSupplier = () => run(async () => {
        if (!supplierForm.code.trim() || !supplierForm.name.trim()) {
            throw new Error('Supplier code and name are required.');
        }
        const key = keyFor('supplier', supplierForm.code.trim());
        await retailApi.createSupplier(key, {
            code: supplierForm.code.trim(),
            name: supplierForm.name.trim(),
            ...(supplierForm.phone.trim() ? { phone: supplierForm.phone.trim() } : {}),
            ...(supplierForm.email.trim() ? { email: supplierForm.email.trim() } : {}),
            status: supplierForm.status,
        });
        clearKey('supplier', supplierForm.code.trim());
        setSupplierForm({ code: '', name: '', phone: '', email: '', status: 'ACTIVE' });
    }, 'Supplier added to the retail catalogue.');

    const createDiscount = () => run(async () => {
        if (!discountForm.code.trim() || num(discountForm.value) <= 0) {
            throw new Error('Discount code and a positive value are required.');
        }
        const code = discountForm.code.trim();
        await retailApi.createDiscount(keyFor('discount', code), {
            code,
            kind: discountForm.kind,
            value: num(discountForm.value),
            status: discountForm.status,
            ...(discountForm.expiresAt ? { expiresAt: discountForm.expiresAt } : {}),
        });
        clearKey('discount', code);
        setDiscountForm({ code: '', kind: 'PERCENT', value: '', expiresAt: '', status: 'ACTIVE' });
    }, 'Discount code created for POS Checkout.');

    const createOrder = () => run(async () => {
        const lines = orderDraft.lines
            .filter((line) => line.productId && num(line.quantity) > 0)
            .map((line) => ({
                productId: line.productId,
                quantity: num(line.quantity),
                ...(line.unitCost ? { unitCost: num(line.unitCost) } : {}),
            }));
        if (!orderDraft.supplierId || lines.length === 0) {
            throw new Error('Choose a supplier and at least one order line.');
        }
        const reference = orderDraft.reference.trim();
        await retailApi.createPurchaseOrder(keyFor('order', reference || orderDraft.supplierId), {
            supplierId: orderDraft.supplierId,
            items: lines,
            ...(reference ? { reference } : {}),
            ...(orderDraft.notes.trim() ? { notes: orderDraft.notes.trim() } : {}),
        });
        clearKey('order', reference || orderDraft.supplierId);
        setOrderDraft((current) => ({
            ...current, reference: '', notes: '',
            lines: [{ productId: text(items[0]?.id), quantity: '1', unitCost: '' }],
        }));
    }, 'Purchase order created. Approve it before receiving stock.');

    const approve = (order: Row) => {
        const id = text(order.id);
        return run(async () => {
            await retailApi.approvePurchaseOrder(id, keyFor('approve', id));
            clearKey('approve', id);
        }, `Order ${text(order.reference) || 'approved'} approved for receiving.`);
    };

    const cancel = (order: Row) => {
        const id = text(order.id);
        return run(async () => {
            await retailApi.cancelPurchaseOrder(id, keyFor('cancel', id));
            clearKey('cancel', id);
        }, `Order ${text(order.reference) || ''} cancelled.`.replace('  ', ' '));
    };

    const openReceive = async (order: Row) => {
        setError('');
        setSuccess('');
        const id = text(order.id);
        const detail = id
            ? await retailApi.purchaseOrder<Row>(id).catch(() => order)
            : order;
        const lines = orderLines(detail).length > 0 ? orderLines(detail) : orderLines(order);
        const values: Record<string, ReceiveValue> = {};
        lines.forEach((line) => {
            const lineId = text(line.orderItemId ?? line.id);
            values[lineId] = {
                quantity: String(num(line.quantity) - num(line.quantityReceived) || line.quantity || 0),
                batchNumber: '',
                expiryDate: '',
            };
        });
        setReceiving({ order: detail, lines, values });
    };

    const receive = () => run(async () => {
        if (!receiving) return;
        const id = text(receiving.order.id);
        const lines = receiving.lines
            .map((line) => {
                const lineId = text(line.orderItemId ?? line.id);
                const value = receiving.values[lineId] ?? { quantity: '', batchNumber: '', expiryDate: '' };
                return {
                    orderItemId: lineId,
                    quantity: num(value.quantity),
                    ...(value.batchNumber.trim() ? { batchNumber: value.batchNumber.trim() } : {}),
                    ...(value.expiryDate ? { expiryDate: value.expiryDate } : {}),
                };
            })
            .filter((line) => line.orderItemId && line.quantity > 0);
        if (!id || lines.length === 0) {
            throw new Error('Enter a received quantity for at least one order line.');
        }
        await retailApi.receivePurchaseOrder(id, keyFor('receive', id), { items: lines });
        clearKey('receive', id);
        setReceiving(null);
    }, 'Receipt recorded. Batches, stock and the movement ledger are updated.');

    const pending = orders.filter((row) => text(row.status) === 'OPEN').length;
    const approved = orders.filter((row) => ['APPROVED', 'PARTIALLY_RECEIVED'].includes(text(row.status))).length;
    const received = orders.filter((row) => text(row.status) === 'RECEIVED').length;
    const orderValue = orders.reduce((sum, row) => sum + num(row.total), 0);

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Purchase Requests"
                    subtitle="Suppliers, purchase orders, approvals and batch receiving"
                    icon="SealCheck"
                    badge={<span className="badge badge-warning">{pending} open</span>}
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Open orders" value={pending} icon="Hourglass" tone="warning" footer="Awaiting approval" />
                    <StatTile label="Ready to receive" value={approved} icon="CheckCircle" tone="info" footer="Approved orders" />
                    <StatTile label="Received" value={received} icon="Truck" tone="success" footer="Fully received" />
                    <StatTile label="Order value" value={money(orderValue)} icon="Coins" tone="primary" footer="Across all orders" />
                </div>

                <div className="tab-nav parent-subtabs" role="tablist" aria-label="Purchase request sections">
                    <button type="button" role="tab" aria-selected={tab === 'orders'} className={`tab-btn ${tab === 'orders' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('orders')}>
                        <Icon name="SealCheck" size={16} className="mr-2" /> Orders
                    </button>
                    <button type="button" role="tab" aria-selected={tab === 'suppliers'} className={`tab-btn ${tab === 'suppliers' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('suppliers')}>
                        <Icon name="Truck" size={16} className="mr-2" /> Suppliers
                    </button>
                    <button type="button" role="tab" aria-selected={tab === 'discounts'} className={`tab-btn ${tab === 'discounts' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('discounts')}>
                        <Icon name="Tag" size={16} className="mr-2" /> Discounts
                    </button>
                </div>

                {tab === 'orders' && (
                    <div className="retail-workbench">
                        <section className="retail-panel" aria-label="Purchase orders">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="ListChecks" size={18} /></span>
                                    Purchase orders
                                </h2>
                                <span className="retail-panel-hint">{orders.length} orders</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="retail-flow-steps mb-4">
                                    <span className="retail-flow-step retail-flow-step-active"><Icon name="ClipboardText" size={14} /> Draft</span>
                                    <Icon name="CaretRight" size={12} />
                                    <span className="retail-flow-step"><Icon name="SealCheck" size={14} /> Approve</span>
                                    <Icon name="CaretRight" size={12} />
                                    <span className="retail-flow-step"><Icon name="Truck" size={14} /> Receive</span>
                                </div>
                                <div className="re-table-wrap">
                                    <table className="re-table">
                                        <thead>
                                            <tr>
                                                <th><span className="table-col"><TableIcon label="Reference" /><span>Reference</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Supplier" /><span>Supplier</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Total" /><span>Total</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Action" /><span>Action</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {orders.map((order) => {
                                                const status = text(order.status);
                                                const id = text(order.id);
                                                return (
                                                    <tr key={id}>
                                                        <td><div className="table-cell-content"><TableIcon label="Reference" value={order.reference} /><span className="min-w-0 font-mono text-xs font-semibold">{text(order.reference) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Supplier" value={order.supplierId} /><span className="min-w-0">{supplierName(text(order.supplierId))}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Status" value={status} /><span className="min-w-0"><span className={`badge ${statusTone(status)}`}>{status || 'OPEN'}</span></span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Total" value={order.total} /><span className="min-w-0 font-semibold">{money(order.total)}</span></div></td>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="Action" value={status} />
                                                                <span className="min-w-0 flex flex-wrap gap-2">
                                                                    {status === 'OPEN' && (
                                                                        <button type="button" className="btn-primary btn-xs" disabled={busy} onClick={() => void approve(order)}>Approve</button>
                                                                    )}
                                                                    {['APPROVED', 'PARTIALLY_RECEIVED'].includes(status) && (
                                                                        <button type="button" className="btn-primary btn-xs" disabled={busy} onClick={() => void openReceive(order)}>Receive</button>
                                                                    )}
                                                                    {['OPEN', 'APPROVED', 'PARTIALLY_RECEIVED'].includes(status) && (
                                                                        <button type="button" className="btn-outline btn-xs" disabled={busy} onClick={() => void cancel(order)}>Cancel</button>
                                                                    )}
                                                                    {['RECEIVED', 'CANCELLED'].includes(status) && (
                                                                        <span className="text-xs text-muted">Closed</span>
                                                                    )}
                                                                </span>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                            {orders.length === 0 && (
                                                <TableEmptyState colSpan={5} icon="SealCheck" message="No purchase orders yet. Add a supplier and create the first order." />
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </section>

                        <section className="retail-panel" aria-label="Create purchase order">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="ClipboardText" size={18} /></span>
                                    New purchase order
                                </h2>
                                <span className="retail-panel-hint">{suppliers.length} suppliers</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="grid grid-cols-1 gap-3">
                                    <label className="block">
                                        <span className="form-label">Supplier</span>
                                        <select className="form-input w-full" value={orderDraft.supplierId} onChange={(event) => setOrderDraft((current) => ({ ...current, supplierId: event.target.value }))}>
                                            <option value="">Choose supplier...</option>
                                            {suppliers.map((row) => (
                                                <option key={text(row.id)} value={text(row.id)}>{text(row.name)} · {text(row.code)}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <label className="block">
                                        <span className="form-label">Reference (optional)</span>
                                        <input className="form-input w-full" value={orderDraft.reference} onChange={(event) => setOrderDraft((current) => ({ ...current, reference: event.target.value }))} placeholder="PO-2026-018" />
                                    </label>
                                    <div className="re-table-wrap">
                                        <table className="re-table">
                                            <thead>
                                                <tr>
                                                    <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Quantity" /><span>Qty</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Unit cost" /><span>Cost</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Action" /><span>Action</span></span></th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {orderDraft.lines.map((line, index) => (
                                                    <tr key={index}>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="Item" value={line.productId} />
                                                                <select className="form-input" value={line.productId} onChange={(event) => setOrderDraft((current) => ({
                                                                    ...current,
                                                                    lines: current.lines.map((draft, i) => i === index ? { ...draft, productId: event.target.value } : draft),
                                                                }))} aria-label={`Order item ${index + 1}`}>
                                                                    <option value="">Choose item...</option>
                                                                    {items.map((row) => (
                                                                        <option key={text(row.id)} value={text(row.id)}>{text(row.name)} · {text(row.sku)}</option>
                                                                    ))}
                                                                </select>
                                                            </div>
                                                        </td>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="Quantity" value={line.quantity} />
                                                                <input className="form-input" type="number" min="1" value={line.quantity} onChange={(event) => setOrderDraft((current) => ({
                                                                    ...current,
                                                                    lines: current.lines.map((draft, i) => i === index ? { ...draft, quantity: event.target.value } : draft),
                                                                }))} aria-label={`Quantity ${index + 1}`} style={{ width: 70 }} />
                                                            </div>
                                                        </td>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="Unit cost" value={line.unitCost} />
                                                                <input className="form-input" type="number" min="0" value={line.unitCost} onChange={(event) => setOrderDraft((current) => ({
                                                                    ...current,
                                                                    lines: current.lines.map((draft, i) => i === index ? { ...draft, unitCost: event.target.value } : draft),
                                                                }))} aria-label={`Unit cost ${index + 1}`} style={{ width: 90 }} placeholder="—" />
                                                            </div>
                                                        </td>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="Action" />
                                                                <span className="min-w-0 flex flex-wrap gap-1">
                                                                    <button type="button" className="btn-outline btn-xs" onClick={() => setOrderDraft((current) => ({
                                                                        ...current,
                                                                        lines: [...current.lines, { productId: text(items[0]?.id), quantity: '1', unitCost: '' }],
                                                                    }))}>Add</button>
                                                                    {orderDraft.lines.length > 1 && (
                                                                        <button type="button" className="btn-outline btn-xs" onClick={() => setOrderDraft((current) => ({
                                                                            ...current,
                                                                            lines: current.lines.filter((_, i) => i !== index),
                                                                        }))}>Remove</button>
                                                                    )}
                                                                </span>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                    <label className="block">
                                        <span className="form-label">Notes</span>
                                        <input className="form-input w-full" value={orderDraft.notes} onChange={(event) => setOrderDraft((current) => ({ ...current, notes: event.target.value }))} placeholder="e.g. Term 1 stationery" />
                                    </label>
                                    <button type="button" className="btn-primary" disabled={busy} onClick={() => void createOrder()}>
                                        <Icon name="Plus" size={16} className="mr-2" /> Create order
                                    </button>
                                </div>
                            </div>
                        </section>
                    </div>
                )}

                {tab === 'suppliers' && (
                    <div className="retail-workbench">
                        <section className="retail-panel" aria-label="Suppliers">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Truck" size={18} /></span>
                                    Supplier directory
                                </h2>
                                <span className="retail-panel-hint">{suppliers.length} suppliers</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="re-table-wrap">
                                    <table className="re-table">
                                        <thead>
                                            <tr>
                                                <th><span className="table-col"><TableIcon label="Code" /><span>Code</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Supplier" /><span>Supplier</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Phone" /><span>Phone</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {suppliers.map((row) => {
                                                const status = text(row.status);
                                                return (
                                                    <tr key={text(row.id)}>
                                                        <td><div className="table-cell-content"><TableIcon label="Code" value={row.code} /><span className="min-w-0 font-mono text-xs font-semibold">{text(row.code) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Supplier" value={row.name} /><span className="min-w-0 font-semibold text-gray-900">{text(row.name) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Phone" value={row.phone} /><span className="min-w-0">{text(row.phone) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Status" value={status} /><span className="min-w-0"><span className={`badge ${statusTone(status)}`}>{status || 'ACTIVE'}</span></span></div></td>
                                                    </tr>
                                                );
                                            })}
                                            {suppliers.length === 0 && (
                                                <TableEmptyState colSpan={4} icon="Truck" message="No suppliers registered yet." />
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </section>

                        <section className="retail-panel" aria-label="Add supplier">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Plus" size={18} /></span>
                                    Add supplier
                                </h2>
                            </div>
                            <div className="retail-panel-body">
                                <div className="grid grid-cols-1 gap-3">
                                    <div className="grid grid-cols-2 gap-3">
                                        <label className="block">
                                            <span className="form-label">Code</span>
                                            <input className="form-input w-full" value={supplierForm.code} onChange={(event) => setSupplierForm((current) => ({ ...current, code: event.target.value }))} placeholder="SUP-001" />
                                        </label>
                                        <label className="block">
                                            <span className="form-label">Name</span>
                                            <input className="form-input w-full" value={supplierForm.name} onChange={(event) => setSupplierForm((current) => ({ ...current, name: event.target.value }))} placeholder="Supplier name" />
                                        </label>
                                    </div>
                                    <div className="grid grid-cols-2 gap-3">
                                        <label className="block">
                                            <span className="form-label">Phone</span>
                                            <input className="form-input w-full" value={supplierForm.phone} onChange={(event) => setSupplierForm((current) => ({ ...current, phone: event.target.value }))} placeholder="Optional" />
                                        </label>
                                        <label className="block">
                                            <span className="form-label">Status</span>
                                            <select className="form-input w-full" value={supplierForm.status} onChange={(event) => setSupplierForm((current) => ({ ...current, status: event.target.value }))}>
                                                <option value="ACTIVE">ACTIVE</option>
                                                <option value="INACTIVE">INACTIVE</option>
                                            </select>
                                        </label>
                                    </div>
                                    <label className="block">
                                        <span className="form-label">Email</span>
                                        <input className="form-input w-full" type="email" value={supplierForm.email} onChange={(event) => setSupplierForm((current) => ({ ...current, email: event.target.value }))} placeholder="Optional" />
                                    </label>
                                    <button type="button" className="btn-primary" disabled={busy} onClick={() => void createSupplier()}>
                                        <Icon name="Plus" size={16} className="mr-2" /> Add supplier
                                    </button>
                                </div>
                            </div>
                        </section>
                    </div>
                )}

                {tab === 'discounts' && (
                    <div className="retail-workbench">
                        <section className="retail-panel" aria-label="Discount codes">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Tag" size={18} /></span>
                                    Discount codes
                                </h2>
                                <span className="retail-panel-hint">{discounts.length} codes</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="re-table-wrap">
                                    <table className="re-table">
                                        <thead>
                                            <tr>
                                                <th><span className="table-col"><TableIcon label="Code" /><span>Code</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Kind" /><span>Kind</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Value" /><span>Value</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Expires" /><span>Expires</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {discounts.map((row) => {
                                                const status = text(row.status);
                                                return (
                                                    <tr key={text(row.id)}>
                                                        <td><div className="table-cell-content"><TableIcon label="Code" value={row.code} /><span className="min-w-0 font-mono text-xs font-semibold">{text(row.code) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Kind" value={row.kind} /><span className="min-w-0">{text(row.kind)}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Value" value={row.value} /><span className="min-w-0 font-semibold">{text(row.kind) === 'PERCENT' ? `${text(row.value)}%` : money(row.value)}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Expires" value={row.expiresAt} /><span className="min-w-0">{text(row.expiresAt).slice(0, 10) || 'No expiry'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Status" value={status} /><span className="min-w-0"><span className={`badge ${statusTone(status)}`}>{status || 'ACTIVE'}</span></span></div></td>
                                                    </tr>
                                                );
                                            })}
                                            {discounts.length === 0 && (
                                                <TableEmptyState colSpan={5} icon="Tag" message="No discount codes configured yet." />
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </section>

                        <section className="retail-panel" aria-label="Create discount">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Plus" size={18} /></span>
                                    Create discount
                                </h2>
                            </div>
                            <div className="retail-panel-body">
                                <div className="grid grid-cols-1 gap-3">
                                    <div className="grid grid-cols-2 gap-3">
                                        <label className="block">
                                            <span className="form-label">Code</span>
                                            <input className="form-input w-full" value={discountForm.code} onChange={(event) => setDiscountForm((current) => ({ ...current, code: event.target.value }))} placeholder="SCHOOL10" />
                                        </label>
                                        <label className="block">
                                            <span className="form-label">Kind</span>
                                            <select className="form-input w-full" value={discountForm.kind} onChange={(event) => setDiscountForm((current) => ({ ...current, kind: event.target.value }))}>
                                                <option value="PERCENT">PERCENT</option>
                                                <option value="FIXED">FIXED</option>
                                            </select>
                                        </label>
                                    </div>
                                    <div className="grid grid-cols-2 gap-3">
                                        <label className="block">
                                            <span className="form-label">Value</span>
                                            <input className="form-input w-full" type="number" min="0" value={discountForm.value} onChange={(event) => setDiscountForm((current) => ({ ...current, value: event.target.value }))} placeholder={discountForm.kind === 'PERCENT' ? '10' : '500'} />
                                        </label>
                                        <label className="block">
                                            <span className="form-label">Status</span>
                                            <select className="form-input w-full" value={discountForm.status} onChange={(event) => setDiscountForm((current) => ({ ...current, status: event.target.value }))}>
                                                <option value="ACTIVE">ACTIVE</option>
                                                <option value="INACTIVE">INACTIVE</option>
                                            </select>
                                        </label>
                                    </div>
                                    <label className="block">
                                        <span className="form-label">Expires on</span>
                                        <input className="form-input w-full" type="date" value={discountForm.expiresAt} onChange={(event) => setDiscountForm((current) => ({ ...current, expiresAt: event.target.value }))} />
                                    </label>
                                    <button type="button" className="btn-primary" disabled={busy} onClick={() => void createDiscount()}>
                                        <Icon name="Plus" size={16} className="mr-2" /> Create discount
                                    </button>
                                </div>
                            </div>
                        </section>
                    </div>
                )}
            </div>

            {receiving && (
                <div className="modal-backdrop" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) setReceiving(null); }}>
                    <div className="modal-card retail-modal-card" role="dialog" aria-modal="true" aria-label="Receive purchase order">
                        <div className="retail-modal-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="Truck" size={18} /></span>
                                Receive · {text(receiving.order.reference) || 'Purchase order'}
                            </h2>
                            <button type="button" className="modal-close" onClick={() => setReceiving(null)} aria-label="Close receiving"><Icon name="X" size={18} /></button>
                        </div>
                        <div className="retail-modal-body">
                            <div className="re-table-wrap">
                                <table className="re-table">
                                    <thead>
                                        <tr>
                                            <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                            <th><span className="table-col"><TableIcon label="Ordered" /><span>Ordered</span></span></th>
                                            <th><span className="table-col"><TableIcon label="Quantity" /><span>Receive qty</span></span></th>
                                            <th><span className="table-col"><TableIcon label="Batch" /><span>Batch</span></span></th>
                                            <th><span className="table-col"><TableIcon label="Expiry" /><span>Expiry</span></span></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {receiving.lines.map((line, index) => {
                                            const lineId = text(line.orderItemId ?? line.id);
                                            const value = receiving.values[lineId] ?? { quantity: '', batchNumber: '', expiryDate: '' };
                                            const update = (patch: Partial<ReceiveValue>) =>
                                                setReceiving((current) => current ? {
                                                    ...current,
                                                    values: { ...current.values, [lineId]: { ...value, ...patch } },
                                                } : current);
                                            return (
                                                <tr key={lineId || index}>
                                                    <td><div className="table-cell-content"><TableIcon label="Item" value={line.productId} /><span className="min-w-0 font-semibold">{productName(text(line.productId))}<span className="block text-xs text-gray-400">{productSku(text(line.productId))}</span></span></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Ordered" value={line.quantity} /><span className="min-w-0">{text(line.quantity)}</span></div></td>
                                                    <td>
                                                        <div className="table-cell-content">
                                                            <TableIcon label="Quantity" />
                                                            <input className="form-input" type="number" min="0" value={value.quantity} onChange={(event) => update({ quantity: event.target.value })} aria-label={`Receive quantity for ${productName(text(line.productId))}`} style={{ width: 80 }} />
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <div className="table-cell-content">
                                                            <TableIcon label="Batch" />
                                                            <input className="form-input" value={value.batchNumber} onChange={(event) => update({ batchNumber: event.target.value })} aria-label={`Batch for ${productName(text(line.productId))}`} placeholder="Batch no." />
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <div className="table-cell-content">
                                                            <TableIcon label="Expiry" />
                                                            <input className="form-input" type="date" value={value.expiryDate} onChange={(event) => update({ expiryDate: event.target.value })} aria-label={`Expiry for ${productName(text(line.productId))}`} />
                                                        </div>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                        {receiving.lines.length === 0 && (
                                            <TableEmptyState colSpan={5} icon="ClipboardText" message="Order lines are not available for this order." />
                                        )}
                                    </tbody>
                                </table>
                            </div>
                            <div className="retail-alert retail-alert-info mt-4">
                                <Icon name="Info" size={16} />
                                <span>Received quantities create FEFO batches and append stock movement entries.</span>
                            </div>
                        </div>
                        <div className="retail-modal-footer">
                            <button type="button" className="btn-outline" onClick={() => setReceiving(null)}>Cancel</button>
                            <button type="button" className="btn-primary" disabled={busy} onClick={() => void receive()}>
                                <Icon name="Check" size={16} className="mr-2" /> Confirm receipt
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </Layout>
    );
}
