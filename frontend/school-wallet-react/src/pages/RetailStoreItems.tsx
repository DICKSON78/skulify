import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { Notice, StatTile, money, text } from '../components/ModuleUi';
import { api, newIdempotencyKey, retailApi, type RetailRow } from '../lib/api';

type Row = RetailRow;
type Tab = 'products' | 'batches';
type StockAction = 'restock' | 'adjust';

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const num = (value: unknown): number => Number(value) || 0;

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

const supplierName = (id: string, suppliers: Row[]): string => {
    const supplier = suppliers.find((row) => text(row.id) === id);
    return supplier ? text(supplier.name) || text(supplier.code) : '—';
};

const productName = (id: string, items: Row[]): string => {
    const item = items.find((row) => text(row.id) === id);
    return item ? text(item.name) || text(item.sku) : '—';
};

const expiryTone = (date: string): string => {
    if (!date) return 'badge-gray';
    const days = Math.ceil((new Date(date).getTime() - Date.now()) / 86_400_000);
    if (Number.isNaN(days) || days < 0) return 'badge-danger';
    if (days <= 30) return 'badge-warning';
    return 'badge-success';
};

const daysLeft = (date: string): number => {
    if (!date) return Number.POSITIVE_INFINITY;
    return Math.ceil((new Date(date).getTime() - Date.now()) / 86_400_000);
};

export default function RetailStoreItems() {
    const [tab, setTab] = useState<Tab>('products');
    const [stockAction, setStockAction] = useState<StockAction>('restock');
    const [items, setItems] = useState<Row[]>([]);
    const [batches, setBatches] = useState<Row[]>([]);
    const [suppliers, setSuppliers] = useState<Row[]>([]);
    const [search, setSearch] = useState('');
    const [category, setCategory] = useState('');
    const [selectedProduct, setSelectedProduct] = useState('');
    const [productModal, setProductModal] = useState(false);
    const [productForm, setProductForm] = useState({
        sku: '', name: '', category: '', price: '', reorderLevel: '5', unit: 'PIECE', location: '',
    });
    const [restock, setRestock] = useState({
        productId: '', quantity: '', unitCost: '', batchNumber: '',
        expiryDate: '', supplierId: '', reference: '',
    });
    const [adjust, setAdjust] = useState({ productId: '', quantity: '', reason: '' });
    const [priceEdit, setPriceEdit] = useState<{ id: string; value: string } | null>(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const restockKey = useRef<string | null>(null);
    const adjustKey = useRef<string | null>(null);

    const loadBatches = useCallback((productId?: string) => {
        retailApi.batches<Row[]>(productId)
            .then((rows) => setBatches(asRows(rows)))
            .catch((reason) => setError(message(reason)));
    }, []);

    const load = useCallback(() => {
        api.list<Row>('store-items', 0, 200, '')
            .then((page) => {
                setItems(page.content);
                setSelectedProduct((current) => current || text(page.content[0]?.id));
                setRestock((current) => ({ ...current, productId: current.productId || text(page.content[0]?.id) }));
                setAdjust((current) => ({ ...current, productId: current.productId || text(page.content[0]?.id) }));
            })
            .catch((reason) => setError(message(reason)));
        retailApi.suppliers<Row[]>()
            .then((rows) => setSuppliers(asRows(rows)))
            .catch(() => undefined);
        loadBatches();
    }, [loadBatches]);

    useEffect(() => {
        load();
    }, [load]);

    useEffect(() => {
        if (tab === 'batches') {
            loadBatches(selectedProduct || undefined);
        }
    }, [tab, selectedProduct, loadBatches]);

    const run = async (action: () => Promise<unknown>, done: string, after?: () => void) => {
        setError('');
        setSuccess('');
        setBusy(true);
        try {
            await action();
            setSuccess(done);
            after?.();
            load();
            if (tab === 'batches') loadBatches(selectedProduct || undefined);
        } catch (reason) {
            setError(message(reason));
        } finally {
            setBusy(false);
        }
    };

    const submitRestock = () =>
        run(async () => {
            if (!restock.productId || num(restock.quantity) <= 0 || !restock.batchNumber.trim()) {
                throw new Error('Product, quantity and batch number are required.');
            }
            const key = restockKey.current ?? newIdempotencyKey();
            restockKey.current = key;
            await retailApi.restock(key, {
                productId: restock.productId,
                quantity: num(restock.quantity),
                batchNumber: restock.batchNumber.trim(),
                ...(restock.unitCost ? { unitCost: num(restock.unitCost) } : {}),
                ...(restock.expiryDate ? { expiryDate: restock.expiryDate } : {}),
                ...(restock.supplierId ? { supplierId: restock.supplierId } : {}),
                ...(restock.reference.trim() ? { reference: restock.reference.trim() } : {}),
            });
            restockKey.current = null;
            setRestock((current) => ({
                ...current, quantity: '', unitCost: '', batchNumber: '', expiryDate: '', reference: '',
            }));
        }, 'Batch received. Stock and the movement ledger are updated.');

    const submitAdjust = () =>
        run(async () => {
            if (!adjust.productId || !num(adjust.quantity) || !adjust.reason.trim()) {
                throw new Error('Product, a non-zero quantity and a reason are required.');
            }
            const key = adjustKey.current ?? newIdempotencyKey();
            adjustKey.current = key;
            await retailApi.adjust(key, {
                productId: adjust.productId,
                quantity: num(adjust.quantity),
                reason: adjust.reason.trim(),
            });
            adjustKey.current = null;
            setAdjust((current) => ({ ...current, quantity: '', reason: '' }));
        }, 'Stock adjustment posted to the movement ledger.');

    const createProduct = () =>
        run(async () => {
            if (!productForm.sku.trim() || !productForm.name.trim() || num(productForm.price) <= 0) {
                throw new Error('SKU, name and a price greater than zero are required.');
            }
            await api.create<Row>('store-items', {
                sku: productForm.sku.trim(),
                name: productForm.name.trim(),
                category: productForm.category.trim(),
                price: num(productForm.price),
                stockQuantity: 0,
                reorderLevel: num(productForm.reorderLevel),
                unit: productForm.unit.trim() || 'PIECE',
                location: productForm.location.trim(),
                active: true,
            });
            setProductForm({ sku: '', name: '', category: '', price: '', reorderLevel: '5', unit: 'PIECE', location: '' });
            setProductModal(false);
        }, 'Product added. Receive a batch to open its stock ledger.');

    const savePrice = (id: string) =>
        run(async () => {
            if (!priceEdit || num(priceEdit.value) <= 0) {
                throw new Error('Enter a price greater than zero.');
            }
            await api.update<Row>('store-items', id, { price: num(priceEdit.value) });
            setPriceEdit(null);
        }, 'Selling price updated.');

    const term = search.trim().toLowerCase();
    const categories = Array.from(new Set(
        items.map((row) => text(row.category)).filter(Boolean),
    )).slice(0, 8);
    const visibleItems = items
        .filter((row) => !category || text(row.category) === category)
        .filter((row) => !term
            || [row.sku, row.name, row.category].some((value) => text(value).toLowerCase().includes(term)));
    const lowStock = items.filter((row) => num(row.stockQuantity) <= num(row.reorderLevel)).length;
    const stockValue = items.reduce((sum, row) => sum + num(row.price) * num(row.stockQuantity), 0);
    const expiringSoon = batches.filter((row) => daysLeft(text(row.expiryDate)) <= 30).length;
    const expiryWatch = [...batches]
        .filter((row) => text(row.expiryDate))
        .sort((a, b) => text(a.expiryDate).localeCompare(text(b.expiryDate)))
        .slice(0, 6);

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Store Items"
                    subtitle="Products, batches and stock adjustments for the school store"
                    icon="Basket"
                    badge={<span className="badge badge-warning">{lowStock} low stock</span>}
                    actions={
                        <button type="button" className="btn-primary" onClick={() => setProductModal(true)}>
                            <Icon name="Plus" size={16} className="mr-2" /> New product
                        </button>
                    }
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Products" value={items.length} icon="Basket" tone="primary" footer="Catalogue items" />
                    <StatTile label="Low stock" value={lowStock} icon="Warning" tone="warning" footer="At or below reorder level" />
                    <StatTile label="Stock value" value={money(stockValue)} icon="Coins" tone="info" footer="At current selling price" />
                    <StatTile label="Batches" value={batches.length} icon="Stack" tone="success" footer={`${expiringSoon} expiring soon`} />
                </div>

                <div className="tab-nav parent-subtabs" role="tablist" aria-label="Store item sections">
                    <button type="button" role="tab" aria-selected={tab === 'products'} className={`tab-btn ${tab === 'products' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('products')}>
                        <Icon name="Basket" size={16} className="mr-2" /> Products
                    </button>
                    <button type="button" role="tab" aria-selected={tab === 'batches'} className={`tab-btn ${tab === 'batches' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setTab('batches')}>
                        <Icon name="Stack" size={16} className="mr-2" /> Batches
                    </button>
                </div>

                {tab === 'products' && (
                    <div className="retail-workbench">
                        <section className="retail-panel" aria-label="Product catalogue">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Basket" size={18} /></span>
                                    Product catalogue
                                </h2>
                                <span className="retail-panel-hint">{visibleItems.length} shown</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="retail-search-row">
                                    <Icon name="MagnifyingGlass" size={18} className="text-muted" />
                                    <input className="form-input" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search by SKU, name or category..." aria-label="Search products" />
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
                                                <th><span className="table-col"><TableIcon label="Price" /><span>Price</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Stock" /><span>Stock</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Reorder level" /><span>Reorder at</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {visibleItems.map((row) => {
                                                const id = text(row.id);
                                                const reorder = num(row.stockQuantity) <= num(row.reorderLevel);
                                                return (
                                                    <tr key={id}>
                                                        <td><div className="table-cell-content"><TableIcon label="SKU" value={row.sku} /><span className="min-w-0 font-mono text-xs">{text(row.sku) || '—'}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Item" value={row.name} /><span className="min-w-0 font-semibold text-gray-900">{text(row.name) || '—'}<span className="block text-xs text-gray-400">{text(row.category) || 'Store item'}</span></span></div></td>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="Price" value={row.price} />
                                                                {priceEdit?.id === id ? (
                                                                    <span className="min-w-0 flex flex-wrap gap-2">
                                                                        <input className="form-input" type="number" min="0" value={priceEdit.value} onChange={(event) => setPriceEdit({ id, value: event.target.value })} aria-label="New selling price" style={{ width: 100 }} />
                                                                        <button type="button" className="btn-primary btn-xs" disabled={busy} onClick={() => void savePrice(id)}>Save</button>
                                                                        <button type="button" className="btn-outline btn-xs" onClick={() => setPriceEdit(null)}>Cancel</button>
                                                                    </span>
                                                                ) : (
                                                                    <button type="button" className="btn-outline btn-xs" onClick={() => setPriceEdit({ id, value: String(num(row.price)) })}>{money(row.price)}</button>
                                                                )}
                                                            </div>
                                                        </td>
                                                        <td><div className="table-cell-content"><TableIcon label="Stock" value={row.stockQuantity} /><span className="min-w-0 font-semibold">{text(row.stockQuantity)}</span></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Reorder level" value={row.reorderLevel} /><span className="min-w-0">{text(row.reorderLevel)}</span></div></td>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="Status" value={reorder ? 'REORDER' : row.active === false ? 'INACTIVE' : 'ACTIVE'} />
                                                                <span className="min-w-0"><span className={`badge ${row.active === false ? 'badge-danger' : reorder ? 'badge-warning' : 'badge-success'}`}>{row.active === false ? 'INACTIVE' : reorder ? 'REORDER' : 'ACTIVE'}</span></span>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                            {visibleItems.length === 0 && (
                                                <TableEmptyState colSpan={6} icon="Basket" message="No products match that search." />
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </section>

                        <section className="retail-panel" aria-label="Stock desk">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Truck" size={18} /></span>
                                    Stock desk
                                </h2>
                                <span className="retail-panel-hint">Ledger-backed</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="tab-nav" role="tablist" aria-label="Stock actions">
                                    <button type="button" role="tab" aria-selected={stockAction === 'restock'} className={`tab-btn ${stockAction === 'restock' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setStockAction('restock')}>
                                        <Icon name="Plus" size={14} className="mr-1" /> Receive batch
                                    </button>
                                    <button type="button" role="tab" aria-selected={stockAction === 'adjust'} className={`tab-btn ${stockAction === 'adjust' ? 'tab-btn-active' : 'tab-btn-inactive'}`} onClick={() => setStockAction('adjust')}>
                                        <Icon name="SlidersHorizontal" size={14} className="mr-1" /> Adjust
                                    </button>
                                </div>

                                {stockAction === 'restock' ? (
                                    <div className="grid grid-cols-1 gap-3 mt-4">
                                        <label className="block">
                                            <span className="form-label">Product</span>
                                            <select className="form-input w-full" value={restock.productId} onChange={(event) => setRestock((current) => ({ ...current, productId: event.target.value }))}>
                                                <option value="">Choose product...</option>
                                                {items.map((row) => (
                                                    <option key={text(row.id)} value={text(row.id)}>{text(row.name)} · {text(row.sku)}</option>
                                                ))}
                                            </select>
                                        </label>
                                        <div className="grid grid-cols-2 gap-3">
                                            <label className="block">
                                                <span className="form-label">Batch number</span>
                                                <input className="form-input w-full" value={restock.batchNumber} onChange={(event) => setRestock((current) => ({ ...current, batchNumber: event.target.value }))} placeholder="BATCH-042" />
                                            </label>
                                            <label className="block">
                                                <span className="form-label">Quantity</span>
                                                <input className="form-input w-full" type="number" min="1" value={restock.quantity} onChange={(event) => setRestock((current) => ({ ...current, quantity: event.target.value }))} placeholder="40" />
                                            </label>
                                        </div>
                                        <div className="grid grid-cols-2 gap-3">
                                            <label className="block">
                                                <span className="form-label">Unit cost</span>
                                                <input className="form-input w-full" type="number" min="0" value={restock.unitCost} onChange={(event) => setRestock((current) => ({ ...current, unitCost: event.target.value }))} placeholder="Optional" />
                                            </label>
                                            <label className="block">
                                                <span className="form-label">Expiry date</span>
                                                <input className="form-input w-full" type="date" value={restock.expiryDate} onChange={(event) => setRestock((current) => ({ ...current, expiryDate: event.target.value }))} />
                                            </label>
                                        </div>
                                        <div className="grid grid-cols-2 gap-3">
                                            <label className="block">
                                                <span className="form-label">Supplier</span>
                                                <select className="form-input w-full" value={restock.supplierId} onChange={(event) => setRestock((current) => ({ ...current, supplierId: event.target.value }))}>
                                                    <option value="">No supplier</option>
                                                    {suppliers.map((row) => (
                                                        <option key={text(row.id)} value={text(row.id)}>{text(row.name)} · {text(row.code)}</option>
                                                    ))}
                                                </select>
                                            </label>
                                            <label className="block">
                                                <span className="form-label">Reference</span>
                                                <input className="form-input w-full" value={restock.reference} onChange={(event) => setRestock((current) => ({ ...current, reference: event.target.value }))} placeholder="GRN-118" />
                                            </label>
                                        </div>
                                        <button type="button" className="btn-primary" disabled={busy} onClick={() => void submitRestock()}>
                                            <Icon name="Plus" size={16} className="mr-2" /> Record restock
                                        </button>
                                    </div>
                                ) : (
                                    <div className="grid grid-cols-1 gap-3 mt-4">
                                        <div className="retail-alert retail-alert-info">
                                            <Icon name="Info" size={16} />
                                            <span>Use a negative quantity for damage or loss. Every adjustment is written to the stock ledger.</span>
                                        </div>
                                        <label className="block">
                                            <span className="form-label">Product</span>
                                            <select className="form-input w-full" value={adjust.productId} onChange={(event) => setAdjust((current) => ({ ...current, productId: event.target.value }))}>
                                                <option value="">Choose product...</option>
                                                {items.map((row) => (
                                                    <option key={text(row.id)} value={text(row.id)}>{text(row.name)} · {text(row.sku)}</option>
                                                ))}
                                            </select>
                                        </label>
                                        <div className="grid grid-cols-2 gap-3">
                                            <label className="block">
                                                <span className="form-label">Signed quantity</span>
                                                <input className="form-input w-full" type="number" value={adjust.quantity} onChange={(event) => setAdjust((current) => ({ ...current, quantity: event.target.value }))} placeholder="-3 or 5" />
                                            </label>
                                            <label className="block">
                                                <span className="form-label">Reason</span>
                                                <input className="form-input w-full" value={adjust.reason} onChange={(event) => setAdjust((current) => ({ ...current, reason: event.target.value }))} placeholder="Damaged in storage" />
                                            </label>
                                        </div>
                                        <button type="button" className="btn-primary" disabled={busy} onClick={() => void submitAdjust()}>
                                            <Icon name="Check" size={16} className="mr-2" /> Post adjustment
                                        </button>
                                    </div>
                                )}
                            </div>
                        </section>
                    </div>
                )}

                {tab === 'batches' && (
                    <div className="retail-workbench">
                        <section className="retail-panel" aria-label="Batch register">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Stack" size={18} /></span>
                                    Batch register
                                </h2>
                                <span className="retail-panel-hint">FEFO order by expiry</span>
                            </div>
                            <div className="retail-panel-body">
                                <label className="block mb-4">
                                    <span className="form-label">Filter by product</span>
                                    <select className="form-input w-full" value={selectedProduct} onChange={(event) => setSelectedProduct(event.target.value)}>
                                        <option value="">All products</option>
                                        {items.map((row) => (
                                            <option key={text(row.id)} value={text(row.id)}>{text(row.name)} · {text(row.sku)}</option>
                                        ))}
                                    </select>
                                </label>
                                <div className="re-table-wrap">
                                    <table className="re-table">
                                        <thead>
                                            <tr>
                                                <th><span className="table-col"><TableIcon label="Batch" /><span>Batch</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Remaining" /><span>Remaining</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Unit cost" /><span>Unit cost</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Expiry" /><span>Expiry</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Supplier" /><span>Supplier</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Reference" /><span>Reference</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {batches.map((row) => (
                                                <tr key={text(row.batchNumber) || text(row.id)}>
                                                    <td><div className="table-cell-content"><TableIcon label="Batch" value={row.batchNumber} /><span className="min-w-0 font-mono text-xs">{text(row.batchNumber) || '—'}</span></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Item" value={row.productId} /><span className="min-w-0">{productName(text(row.productId), items)}</span></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Remaining" value={row.quantityRemaining ?? row.quantity} /><span className="min-w-0 font-semibold">{text(row.quantityRemaining ?? row.quantity)}</span></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Unit cost" value={row.unitCost} /><span className="min-w-0">{money(row.unitCost)}</span></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Expiry" value={row.expiryDate} /><span className="min-w-0">{text(row.expiryDate) ? <span className={`badge ${expiryTone(text(row.expiryDate))}`}>{text(row.expiryDate)}</span> : '—'}</span></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Supplier" value={row.supplierId} /><span className="min-w-0">{supplierName(text(row.supplierId), suppliers)}</span></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Reference" value={row.reference} /><span className="min-w-0 font-mono text-xs">{text(row.reference) || text(row.source) || '—'}</span></div></td>
                                                </tr>
                                            ))}
                                            {batches.length === 0 && (
                                                <TableEmptyState colSpan={7} icon="Stack" message="No batches recorded for this product." />
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </section>

                        <section className="retail-panel" aria-label="Expiry watch">
                            <div className="retail-panel-header">
                                <h2 className="retail-panel-title">
                                    <span className="icon-tile"><Icon name="Hourglass" size={18} /></span>
                                    Expiry watch
                                </h2>
                                <span className="badge badge-warning">{expiringSoon} soon</span>
                            </div>
                            <div className="retail-panel-body">
                                <div className="retail-detail-list">
                                    {expiryWatch.map((row, index) => (
                                        <div className="retail-detail-row" key={`${text(row.batchNumber)}-${index}`}>
                                            <span><TableIcon label="Batch" value={row.batchNumber} />{text(row.batchNumber) || 'Batch'}</span>
                                            <strong className={`badge ${expiryTone(text(row.expiryDate))}`}>{text(row.expiryDate)}</strong>
                                        </div>
                                    ))}
                                    {expiryWatch.length === 0 && (
                                        <div className="retail-alert retail-alert-info">
                                            <Icon name="CheckCircle" size={16} />
                                            <span>No batches with expiry dates are due for review.</span>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </section>
                    </div>
                )}
            </div>

            {productModal && (
                <div className="modal-backdrop" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) setProductModal(false); }}>
                    <div className="modal-card retail-modal-card" role="dialog" aria-modal="true" aria-label="New product">
                        <div className="retail-modal-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="Plus" size={18} /></span>
                                New product
                            </h2>
                            <button type="button" className="modal-close" onClick={() => setProductModal(false)} aria-label="Close"><Icon name="X" size={18} /></button>
                        </div>
                        <div className="retail-modal-body">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <label className="block">
                                    <span className="form-label">SKU</span>
                                    <input className="form-input w-full" value={productForm.sku} onChange={(event) => setProductForm((current) => ({ ...current, sku: event.target.value }))} placeholder="SKU-001" />
                                </label>
                                <label className="block">
                                    <span className="form-label">Name</span>
                                    <input className="form-input w-full" value={productForm.name} onChange={(event) => setProductForm((current) => ({ ...current, name: event.target.value }))} placeholder="Exercise book" />
                                </label>
                                <label className="block">
                                    <span className="form-label">Category</span>
                                    <input className="form-input w-full" value={productForm.category} onChange={(event) => setProductForm((current) => ({ ...current, category: event.target.value }))} placeholder="Stationery" />
                                </label>
                                <label className="block">
                                    <span className="form-label">Selling price</span>
                                    <input className="form-input w-full" type="number" min="0" value={productForm.price} onChange={(event) => setProductForm((current) => ({ ...current, price: event.target.value }))} placeholder="0" />
                                </label>
                                <label className="block">
                                    <span className="form-label">Reorder level</span>
                                    <input className="form-input w-full" type="number" min="0" value={productForm.reorderLevel} onChange={(event) => setProductForm((current) => ({ ...current, reorderLevel: event.target.value }))} />
                                </label>
                                <label className="block">
                                    <span className="form-label">Unit</span>
                                    <input className="form-input w-full" value={productForm.unit} onChange={(event) => setProductForm((current) => ({ ...current, unit: event.target.value }))} />
                                </label>
                                <label className="block sm:col-span-2">
                                    <span className="form-label">Shelf location</span>
                                    <input className="form-input w-full" value={productForm.location} onChange={(event) => setProductForm((current) => ({ ...current, location: event.target.value }))} placeholder="Optional" />
                                </label>
                            </div>
                            <div className="retail-alert retail-alert-info mt-4">
                                <Icon name="Info" size={16} />
                                <span>New products start at zero stock. Receive a batch on the Stock desk so every quantity is backed by a movement.</span>
                            </div>
                        </div>
                        <div className="retail-modal-footer">
                            <button type="button" className="btn-outline" onClick={() => setProductModal(false)}>Cancel</button>
                            <button type="button" className="btn-primary" disabled={busy} onClick={() => void createProduct()}>
                                <Icon name="Check" size={16} className="mr-2" /> Save product
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </Layout>
    );
}
