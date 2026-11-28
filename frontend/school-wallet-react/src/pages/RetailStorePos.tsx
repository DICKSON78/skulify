import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { FormCard, Notice, StatTile, money, text } from '../components/ModuleUi';
import {
    api,
    newIdempotencyKey,
    pocketMoneyApi,
    retailApi,
    type ApiError,
    type RetailRow,
} from '../lib/api';

type Row = RetailRow;
type CartLine = { productId: string; quantity: number };

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const num = (value: unknown): number => Number(value) || 0;

const asRows = (value: unknown): Row[] => {
    if (Array.isArray(value)) {
        return value.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
    }
    if (value && typeof value === 'object') {
        const wrapped = value as Row;
        const nested = wrapped.content ?? wrapped.items ?? wrapped.rows ?? wrapped.saleItems;
        if (Array.isArray(nested)) {
            return nested.filter((entry): entry is Row => Boolean(entry) && typeof entry === 'object');
        }
    }
    return [];
};

const statusTone = (status: string): string => {
    if (['COMPLETED', 'PAID', 'POSTED', 'APPROVED'].includes(status)) return 'badge-success';
    if (['PENDING', 'PARTIAL', 'PARTIALLY_RECEIVED'].includes(status)) return 'badge-warning';
    if (['CANCELLED', 'REFUNDED', 'RETURNED', 'FAILED'].includes(status)) return 'badge-danger';
    return 'badge-info';
};

const studentName = (row: Row | undefined): string => {
    if (!row) return 'Student';
    return `${text(row.firstName)} ${text(row.lastName)}`.trim()
        || text(row.studentName)
        || text(row.studentCode)
        || 'Student';
};

const fingerprintOf = (preview: Row | null): string =>
    text(preview?.pricingFingerprint ?? preview?.fingerprint);

const extractPreview = (details: unknown): Row | null => {
    if (!details || typeof details !== 'object') {
        return null;
    }
    const wrapper = details as Row;
    const candidates = [wrapper.preview, wrapper.freshPreview, wrapper.details, wrapper];
    for (const candidate of candidates) {
        if (candidate && typeof candidate === 'object' && Array.isArray((candidate as Row).lines)) {
            return candidate as Row;
        }
    }
    return null;
};

export default function RetailStorePos() {
    const [items, setItems] = useState<Row[]>([]);
    const [cards, setCards] = useState<Row[]>([]);
    const [students, setStudents] = useState<Row[]>([]);
    const [terminals, setTerminals] = useState<Row[]>([]);
    const [search, setSearch] = useState('');
    const [cart, setCart] = useState<CartLine[]>([]);
    const [discountCode, setDiscountCode] = useState('');
    const [preview, setPreview] = useState<Row | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);
    const [priceChanged, setPriceChanged] = useState(false);
    const [cardId, setCardId] = useState('');
    const [cardUid, setCardUid] = useState('');
    const [studentId, setStudentId] = useState('');
    const [terminalCode, setTerminalCode] = useState('');
    const [deviceKey, setDeviceKey] = useState('SKULIFY-DEMO-SENSOR-KEY');
    const [notes, setNotes] = useState('');
    const [receipt, setReceipt] = useState<Row | null>(null);
    const [sales, setSales] = useState<Row[]>([]);
    const [returnSale, setReturnSale] = useState<Row | null>(null);
    const [returnLines, setReturnLines] = useState<Row[]>([]);
    const [returnQuantities, setReturnQuantities] = useState<Record<string, string>>({});
    const [returnDispositions, setReturnDispositions] = useState<Record<string, string>>({});
    const [returnReason, setReturnReason] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const checkoutKey = useRef<string | null>(null);
    const checkoutNonce = useRef<string | null>(null);
    const checkoutTimestamp = useRef<string | null>(null);
    const returnKey = useRef<string | null>(null);

    const resetCheckoutIdentity = () => {
        checkoutKey.current = null;
        checkoutNonce.current = null;
        checkoutTimestamp.current = null;
    };

    const refreshSales = useCallback(() => {
        retailApi.sales<Row[]>(undefined, 25)
            .then((rows) => setSales(asRows(rows)))
            .catch(() => setSales([]));
    }, []);

    const load = useCallback(() => {
        api.list<Row>('store-items', 0, 200, '')
            .then((page) => setItems(page.content.filter((row) => row.active !== false)))
            .catch((reason) => setError(message(reason)));
        pocketMoneyApi.cards<Row[]>()
            .then((rows) => {
                setCards(rows);
                const preferred = rows.find((row) => text(row.status) === 'ACTIVE') ?? rows[0];
                setCardId((current) => current || text(preferred?.id));
                setCardUid((current) => current || text(preferred?.uid));
                setStudentId((current) => current || text(preferred?.studentId));
            })
            .catch(() => undefined);
        api.list<Row>('students', 0, 200, '')
            .then((page) => setStudents(page.content))
            .catch(() => undefined);
        retailApi.terminals<Row[]>()
            .then((rows) => {
                setTerminals(asRows(rows));
                setTerminalCode((current) => current || text(asRows(rows)[0]?.code));
            })
            .catch(() => pocketMoneyApi.terminals<Row[]>()
                .then((rows) => {
                    setTerminals(asRows(rows));
                    setTerminalCode((current) => current || text(asRows(rows)[0]?.code));
                })
                .catch(() => undefined));
        refreshSales();
    }, [refreshSales]);

    useEffect(() => {
        load();
    }, [load]);

    useEffect(() => {
        let active = true;
        if (cart.length === 0) {
            setPreview(null);
            setPreviewLoading(false);
            return;
        }
        setPreviewLoading(true);
        retailApi.preview<Row>({
            items: cart,
            ...(discountCode.trim() ? { discountCode: discountCode.trim() } : {}),
        })
            .then((result) => {
                if (!active) return;
                setPreview(result);
                setPriceChanged(false);
            })
            .catch((reason) => {
                if (!active) return;
                setPreview(null);
                setError(`Preview failed: ${message(reason)}`);
            })
            .finally(() => {
                if (active) setPreviewLoading(false);
            });
        return () => {
            active = false;
        };
    }, [cart, discountCode]);

    const selectCard = (id: string) => {
        resetCheckoutIdentity();
        setCardId(id);
        const card = cards.find((row) => text(row.id) === id);
        setCardUid(text(card?.uid));
        setStudentId(text(card?.studentId));
    };

    const addItem = (id: string) => {
        if (!id) return;
        resetCheckoutIdentity();
        setCart((current) => {
            const existing = current.find((line) => line.productId === id);
            if (existing) {
                return current.map((line) =>
                    line.productId === id ? { ...line, quantity: line.quantity + 1 } : line);
            }
            return [...current, { productId: id, quantity: 1 }];
        });
        setSearch('');
        setPriceChanged(false);
        setError('');
    };

    const changeQuantity = (id: string, quantity: number) => {
        resetCheckoutIdentity();
        setCart((current) => current
            .map((line) => line.productId === id ? { ...line, quantity } : line)
            .filter((line) => line.quantity > 0));
        setPriceChanged(false);
    };

    const openReceipt = (sale: Row) => {
        const id = text(sale.id);
        if (!id) {
            setReceipt(sale);
            return;
        }
        retailApi.receipt<Row>(id)
            .then(setReceipt)
            .catch(() => setReceipt(sale));
    };

    const checkout = async () => {
        if (!cart.length || !studentId || !cardUid || !terminalCode) {
            setError('Choose a student card, a terminal and at least one item before checkout.');
            return;
        }
        const key = checkoutKey.current ?? newIdempotencyKey();
        checkoutKey.current = key;
        const nonce = checkoutNonce.current ?? newIdempotencyKey();
        checkoutNonce.current = nonce;
        const requestTimestamp = checkoutTimestamp.current ?? new Date().toISOString();
        checkoutTimestamp.current = requestTimestamp;
        setError('');
        setSuccess('');
        setBusy(true);
        try {
            const result = await retailApi.createSale<Row>(key, {
                studentId,
                cardUid,
                terminalCode,
                deviceKey,
                items: cart.map((line) => ({ productId: line.productId, quantity: line.quantity })),
                ...(discountCode.trim() ? { discountCode: discountCode.trim() } : {}),
                ...(fingerprintOf(preview) ? { pricingFingerprint: fingerprintOf(preview) } : {}),
                ...(notes.trim() ? { notes: notes.trim() } : {}),
                nonce,
                requestTimestamp,
            });
            setReceipt(result);
            setSuccess(Boolean(result.replayed)
                ? 'This checkout was already recorded. The original receipt is shown.'
                : 'Sale posted to the Pocket Money ledger and stock updated.');
            resetCheckoutIdentity();
            setCart([]);
            setDiscountCode('');
            setNotes('');
            setPriceChanged(false);
            refreshSales();
            load();
        } catch (reason) {
            const failure = reason as ApiError;
            if (failure.code === 'PRICE_CHANGED' || message(reason).includes('PRICE_CHANGED')) {
                const fresh = extractPreview(failure.details);
                if (fresh) {
                    setPreview(fresh);
                    setPriceChanged(true);
                }
                resetCheckoutIdentity();
                setError('Prices changed. Review the updated total, then confirm again.');
            } else {
                setError(message(reason));
            }
        } finally {
            setBusy(false);
        }
    };

    const openReturn = (sale: Row) => {
        const id = text(sale.id);
        if (!id) {
            setError('This sale cannot be returned without a reference.');
            return;
        }
        setError('');
        setSuccess('');
        returnKey.current = null;
        retailApi.sale<Row>(id)
            .then((detail) => {
                const lines = asRows(detail.items ?? detail.lines ?? detail.saleItems ?? sale.items);
                setReturnSale({ ...detail, id });
                setReturnLines(lines);
                const quantities: Record<string, string> = {};
                lines.forEach((line) => {
                    quantities[text(line.saleItemId ?? line.id)] = String(line.quantity ?? 1);
                });
                setReturnQuantities(quantities);
                setReturnDispositions({});
                setReturnReason('');
            })
            .catch((reason) => setError(message(reason)));
    };

    const submitReturn = async () => {
        if (!returnSale) return;
        const id = text(returnSale.id);
        const lines = returnLines
            .map((line) => {
                const lineId = text(line.saleItemId ?? line.id);
                return {
                    saleItemId: lineId,
                    quantity: num(returnQuantities[lineId]),
                    disposition: returnDispositions[lineId] ?? 'RESTOCK',
                };
            })
            .filter((line) => line.saleItemId && line.quantity > 0);
        if (lines.length === 0) {
            setError('Enter a quantity for at least one item to return.');
            return;
        }
        const key = returnKey.current ?? newIdempotencyKey();
        returnKey.current = key;
        setError('');
        setSuccess('');
        setBusy(true);
        try {
            const result = await retailApi.returnSale<Row>(id, key, {
                items: lines,
                ...(returnReason.trim() ? { reason: returnReason.trim() } : {}),
            });
            setSuccess(Boolean(result.replayed)
                ? 'This return was already recorded.'
                : 'Return recorded. Stock and the Pocket Money wallet are updated.');
            returnKey.current = null;
            setReturnSale(null);
            setReturnLines([]);
            setReturnReason('');
            refreshSales();
            load();
            openReceipt({ id });
        } catch (reason) {
            setError(message(reason));
        } finally {
            setBusy(false);
        }
    };

    const term = search.trim().toLowerCase();
    const suggestions = items
        .filter((row) => row.active !== false)
        .filter((row) => !term
            || [row.sku, row.name, row.category].some((value) => text(value).toLowerCase().includes(term)))
        .slice(0, 8);
    const previewLines = asRows(preview?.lines);
    const receiptLines = asRows(receipt?.items ?? receipt?.lines ?? receipt?.saleItems);
    const lowStock = items.filter((row) => num(row.stockQuantity) <= num(row.reorderLevel)).length;
    const previewTotal = preview ? num(preview.total) : 0;
    const selectedCard = cards.find((row) => text(row.id) === cardId);
    const selectedStudent = students.find((row) => text(row.id) === text(selectedCard?.studentId)
        || text(row.id) === studentId);

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="POS Checkout"
                    subtitle="Scan a student card, price the basket and settle through Pocket Money"
                    icon="Scan"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Products" value={items.length} icon="Basket" tone="primary" footer="Available in the store" />
                    <StatTile label="Low stock" value={lowStock} icon="Warning" tone="warning" footer="Items needing restock" />
                    <StatTile label="Active terminals" value={terminals.filter((row) => text(row.status) === 'ACTIVE').length} icon="Desktop" tone="info" footer="Ready for checkout" />
                    <StatTile label="Basket total" value={money(previewTotal)} icon="Coins" tone="success" footer={previewLoading ? 'Pricing on the server...' : 'Server-priced total'} />
                </div>

                <FormCard title="Student and terminal" icon="CreditCard">
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                        <label className="block">
                            <span className="form-label">Student card</span>
                            <select
                                className="form-input w-full"
                                value={cardId}
                                onChange={(event) => selectCard(event.target.value)}
                            >
                                <option value="">Scan a card or choose manually...</option>
                                {cards.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>
                                        {text(row.uid)} · {text(row.status)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Card UID (scan / tap)</span>
                            <input
                                className="form-input w-full font-mono"
                                value={cardUid}
                                onChange={(event) => { resetCheckoutIdentity(); setCardUid(event.target.value); }}
                                placeholder="CARD-XXXXXXXX"
                            />
                        </label>
                        <label className="block">
                            <span className="form-label">Student</span>
                            <select
                                className="form-input w-full"
                                value={studentId}
                                onChange={(event) => { resetCheckoutIdentity(); setStudentId(event.target.value); }}
                            >
                                <option value="">Choose student...</option>
                                {students.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>
                                        {studentName(row)} · {text(row.studentCode)}
                                    </option>
                                ))}
                            </select>
                            {selectedStudent && (
                                <span className="mt-1 block text-xs text-gray-400">
                                    {studentName(selectedStudent)} · {text(selectedStudent.className)}
                                </span>
                            )}
                        </label>
                        <label className="block">
                            <span className="form-label">Terminal</span>
                            <select
                                className="form-input w-full"
                                value={terminalCode}
                                onChange={(event) => { resetCheckoutIdentity(); setTerminalCode(event.target.value); }}
                            >
                                <option value="">Choose terminal...</option>
                                {terminals.map((row) => (
                                    <option key={text(row.id)} value={text(row.code)}>
                                        {text(row.code)} · {text(row.merchantCode)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block lg:col-span-2">
                            <span className="form-label">Device key</span>
                            <input
                                className="form-input w-full"
                                value={deviceKey}
                                onChange={(event) => { resetCheckoutIdentity(); setDeviceKey(event.target.value); }}
                                placeholder="Sensor device key"
                            />
                            <span className="mt-1 block text-xs text-gray-400">
                                The terminal registered under Pocket Money must match this key.
                            </span>
                        </label>
                    </div>
                </FormCard>

                <FormCard title="Build the basket" icon="Basket">
                    <label className="block">
                        <span className="form-label">Scan barcode or search by name / SKU</span>
                        <input
                            className="form-input w-full"
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter' && suggestions[0]) {
                                    event.preventDefault();
                                    addItem(text(suggestions[0].id));
                                }
                            }}
                            placeholder="Scan or type a product name..."
                        />
                    </label>
                    <div className="mt-3 flex flex-wrap gap-2">
                        {suggestions.map((row) => (
                            <button
                                key={text(row.id)}
                                type="button"
                                className="btn-outline btn-xs"
                                onClick={() => addItem(text(row.id))}
                                title={`${text(row.name)} · ${money(row.price)}`}
                            >
                                <Icon name="Plus" size={14} className="mr-1" />
                                {text(row.sku) || text(row.name)}
                            </button>
                        ))}
                        {suggestions.length === 0 && (
                            <span className="text-xs text-gray-400">No products match that scan or search.</span>
                        )}
                    </div>

                    <div className="re-table-wrap mt-4">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Quantity" /><span>Quantity</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Unit price" /><span>Unit price</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Line total" /><span>Line total</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Available stock" /><span>Available stock</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Action" /><span>Action</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {cart.map((line) => {
                                    const priced = previewLines.find((row) => text(row.productId) === line.productId);
                                    const product = items.find((row) => text(row.id) === line.productId);
                                    const unitPrice = num(priced?.unitPrice ?? product?.price);
                                    return (
                                        <tr key={line.productId}>
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Item" value={priced?.name ?? product?.name} />
                                                    <span className="min-w-0">
                                                        <strong className="block text-gray-900">{text(priced?.name ?? product?.name) || 'Product'}</strong>
                                                        <span className="text-xs text-gray-400">{text(priced?.sku ?? product?.sku)}</span>
                                                    </span>
                                                </div>
                                            </td>
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Quantity" value={line.quantity} />
                                                    <input
                                                        className="form-input"
                                                        type="number"
                                                        min="1"
                                                        max={num(priced?.availableStock ?? product?.stockQuantity) || undefined}
                                                        value={line.quantity}
                                                        onChange={(event) => changeQuantity(line.productId, Number(event.target.value))}
                                                        aria-label={`Quantity for ${text(priced?.name ?? product?.name)}`}
                                                        style={{ width: 80 }}
                                                    />
                                                </div>
                                            </td>
                                            <td><div className="table-cell-content"><TableIcon label="Unit price" value={unitPrice} /><span className="min-w-0">{money(unitPrice)}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Line total" value={priced?.lineTotal ?? unitPrice * line.quantity} /><span className="min-w-0 font-semibold">{money(priced?.lineTotal ?? unitPrice * line.quantity)}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Available stock" value={priced?.availableStock ?? product?.stockQuantity} /><span className="min-w-0">{text(priced?.availableStock ?? product?.stockQuantity ?? '—')}</span></div></td>
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Action" />
                                                    <button type="button" className="btn-outline btn-xs" onClick={() => changeQuantity(line.productId, 0)}>Remove</button>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                                {cart.length === 0 && (
                                    <TableEmptyState colSpan={6} icon="Basket" message="No items in the basket. Scan a product to begin." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>

                <FormCard title="Pricing and checkout" icon="CashRegister">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <label className="block">
                            <span className="form-label">Discount code (optional)</span>
                            <input
                                className="form-input w-full"
                                value={discountCode}
                                onChange={(event) => { resetCheckoutIdentity(); setDiscountCode(event.target.value); }}
                                placeholder="e.g. SCHOOL10"
                            />
                        </label>
                        <label className="block">
                            <span className="form-label">Notes (optional)</span>
                            <input
                                className="form-input w-full"
                                value={notes}
                                onChange={(event) => { resetCheckoutIdentity(); setNotes(event.target.value); }}
                                placeholder="e.g. Returning customer"
                            />
                        </label>
                    </div>
                    <div className="grid grid-cols-3 gap-3 mt-4 text-sm">
                        <div><p className="text-gray-500">Subtotal</p><p className="font-semibold">{money(preview?.subtotal)}</p></div>
                        <div><p className="text-gray-500">Discount</p><p className="font-semibold">{money(preview?.discount)}</p></div>
                        <div><p className="text-gray-500">Total</p><p className="text-xl font-bold text-primary-dark">{money(previewTotal)}</p></div>
                    </div>
                    {priceChanged && (
                        <p className="notice warning mt-3">Server prices changed. Confirm again to charge the updated total.</p>
                    )}
                    <div className="pocket-charge-row">
                        <div>
                            <span>Amount to charge</span>
                            <strong>{money(previewTotal)}</strong>
                        </div>
                        <button
                            type="button"
                            className="btn-primary"
                            disabled={busy || previewLoading || !cart.length || !studentId || !cardUid || !terminalCode}
                            onClick={() => void checkout()}
                        >
                            <Icon name={priceChanged ? 'ArrowClockwise' : 'Check'} size={18} className="mr-2" />
                            {priceChanged ? 'Confirm new price' : 'Checkout card'}
                        </button>
                    </div>
                </FormCard>

                {receipt && (
                    <FormCard title="Receipt" icon="Receipt">
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
                            <div><p className="text-gray-500">Receipt</p><p className="font-mono font-semibold">{text(receipt.receiptNo ?? receipt.reference) || '—'}</p></div>
                            <div><p className="text-gray-500">Status</p><p><span className={`badge ${statusTone(text(receipt.status))}`}>{text(receipt.status) || 'COMPLETED'}</span></p></div>
                            <div><p className="text-gray-500">Total</p><p className="font-semibold">{money(receipt.total ?? receipt.amount)}</p></div>
                            <div><p className="text-gray-500">Balance after</p><p className="font-semibold">{money(receipt.balanceAfter)}</p></div>
                            <div><p className="text-gray-500">Payment</p><p>{text(receipt.paymentMethod) || 'POCKET_MONEY'}</p></div>
                            <div><p className="text-gray-500">Student</p><p>{studentName(students.find((row) => text(row.id) === text(receipt.studentId)))}</p></div>
                        </div>
                        <div className="re-table-wrap mt-4">
                            <table className="re-table">
                                <thead>
                                    <tr>
                                        <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Quantity" /><span>Qty</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Unit price" /><span>Unit price</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Line total" /><span>Line total</span></span></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {receiptLines.map((line, index) => (
                                        <tr key={index}>
                                            <td><div className="table-cell-content"><TableIcon label="Item" value={line.name} /><span className="min-w-0"><strong className="block text-gray-900">{text(line.name) || 'Product'}</strong><span className="text-xs text-gray-400">{text(line.sku)}</span></span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Quantity" value={line.quantity} /><span className="min-w-0">{text(line.quantity)}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Unit price" value={line.unitPrice} /><span className="min-w-0">{money(line.unitPrice)}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Line total" value={line.lineTotal} /><span className="min-w-0 font-semibold">{money(line.lineTotal)}</span></div></td>
                                        </tr>
                                    ))}
                                    {receiptLines.length === 0 && (
                                        <TableEmptyState colSpan={4} icon="Receipt" message="No line items on this receipt." />
                                    )}
                                </tbody>
                            </table>
                        </div>
                        <button
                            type="button"
                            className="btn-outline mt-4"
                            disabled={!text(receipt.id)}
                            onClick={() => void retailApi.printReceipt(text(receipt.id)).catch((reason) => setError(message(reason)))}
                        >
                            <Icon name="Receipt" size={16} className="mr-2" /> Print receipt
                        </button>
                    </FormCard>
                )}

                {returnSale && (
                    <FormCard title={`Return items · ${text(returnSale.receiptNo ?? returnSale.reference) || 'Sale'}`} icon="ArrowCounterClockwise">
                        <div className="re-table-wrap">
                            <table className="re-table">
                                <thead>
                                    <tr>
                                        <th><span className="table-col"><TableIcon label="Item" /><span>Item</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Sold" /><span>Sold</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Quantity" /><span>Return qty</span></span></th>
                                        <th><span className="table-col"><TableIcon label="Disposition" /><span>Disposition</span></span></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {returnLines.map((line, index) => {
                                        const lineId = text(line.saleItemId ?? line.id);
                                        return (
                                            <tr key={lineId || index}>
                                                <td><div className="table-cell-content"><TableIcon label="Item" value={line.name} /><span className="min-w-0">{text(line.name) || text(line.sku)}</span></div></td>
                                                <td><div className="table-cell-content"><TableIcon label="Sold" value={line.quantity} /><span className="min-w-0">{text(line.quantity)}</span></div></td>
                                                <td>
                                                    <div className="table-cell-content">
                                                        <TableIcon label="Quantity" />
                                                        <input
                                                            className="form-input"
                                                            type="number"
                                                            min="0"
                                                            max={num(line.quantity) || undefined}
                                                            value={returnQuantities[lineId] ?? '0'}
                                                            onChange={(event) => setReturnQuantities((current) => ({ ...current, [lineId]: event.target.value }))}
                                                            aria-label={`Return quantity for ${text(line.name)}`}
                                                            style={{ width: 90 }}
                                                        />
                                                    </div>
                                                </td>
                                                <td>
                                                    <div className="table-cell-content">
                                                        <TableIcon label="Disposition" value={returnDispositions[lineId]} />
                                                        <select
                                                            className="form-input"
                                                            value={returnDispositions[lineId] ?? 'RESTOCK'}
                                                            onChange={(event) => setReturnDispositions((current) => ({ ...current, [lineId]: event.target.value }))}
                                                            aria-label={`Disposition for ${text(line.name)}`}
                                                        >
                                                            <option value="RESTOCK">Restock</option>
                                                            <option value="WRITE_OFF">Write off</option>
                                                        </select>
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                    {returnLines.length === 0 && (
                                        <TableEmptyState colSpan={4} icon="Receipt" message="Sale lines are not available for this sale." />
                                    )}
                                </tbody>
                            </table>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-4">
                            <label className="block">
                                <span className="form-label">Reason</span>
                                <input
                                    className="form-input w-full"
                                    value={returnReason}
                                    onChange={(event) => setReturnReason(event.target.value)}
                                    placeholder="e.g. Wrong item"
                                />
                            </label>
                        </div>
                        <div className="flex flex-wrap gap-2 mt-4">
                            <button type="button" className="btn-primary" disabled={busy} onClick={() => void submitReturn()}>
                                <Icon name="ArrowCounterClockwise" size={16} className="mr-2" /> Confirm return
                            </button>
                            <button type="button" className="btn-outline" onClick={() => { setReturnSale(null); returnKey.current = null; }}>
                                Cancel
                            </button>
                        </div>
                    </FormCard>
                )}

                <FormCard title="Recent sales" icon="ListChecks">
                    <div className="re-table-wrap">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    <th><span className="table-col"><TableIcon label="Receipt" /><span>Receipt</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Student" /><span>Student</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Total" /><span>Total</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Date" /><span>Date</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Action" /><span>Action</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {sales.map((sale) => {
                                    const status = text(sale.status);
                                    return (
                                        <tr key={text(sale.id)}>
                                            <td><div className="table-cell-content"><TableIcon label="Receipt" value={sale.receiptNo ?? sale.reference} /><span className="min-w-0 font-mono text-xs">{text(sale.receiptNo ?? sale.reference) || '—'}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Student" value={sale.studentId} /><span className="min-w-0">{studentName(students.find((row) => text(row.id) === text(sale.studentId)))}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Total" value={sale.total} /><span className="min-w-0 font-semibold">{money(sale.total ?? sale.amount)}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Status" value={status} /><span className="min-w-0"><span className={`badge ${statusTone(status)}`}>{status || 'COMPLETED'}</span></span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Date" value={sale.createdAt} /><span className="min-w-0">{text(sale.createdAt).slice(0, 16).replace('T', ' ') || '—'}</span></div></td>
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Action" value={status} />
                                                    <span className="min-w-0 flex flex-wrap gap-2">
                                                        <button type="button" className="btn-outline btn-xs" onClick={() => openReceipt(sale)}>Receipt</button>
                                                        {!['CANCELLED', 'REFUNDED', 'RETURNED'].includes(status) && (
                                                            <button type="button" className="btn-outline btn-xs" onClick={() => openReturn(sale)}>Return</button>
                                                        )}
                                                    </span>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                                {sales.length === 0 && (
                                    <TableEmptyState colSpan={6} icon="Receipt" message="No sales recorded yet. Checkout a basket to see it here." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>
            </div>
        </Layout>
    );
}
