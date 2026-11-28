import { useCallback, useEffect, useRef, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { Notice, money, text } from '../components/ModuleUi';
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
    const [category, setCategory] = useState('');
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
    }, []);

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

    const addItem = (id: string, clearSearch = false) => {
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
        if (clearSearch) setSearch('');
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

    const closeReceipt = () => {
        setReceipt(null);
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

    const closeReturn = () => {
        setReturnSale(null);
        setReturnLines([]);
        returnKey.current = null;
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
            load();
            openReceipt({ id });
        } catch (reason) {
            setError(message(reason));
        } finally {
            setBusy(false);
        }
    };

    const term = search.trim().toLowerCase();
    const categories = Array.from(new Set(
        items.map((row) => text(row.category)).filter(Boolean),
    )).slice(0, 8);
    const visibleProducts = items
        .filter((row) => !category || text(row.category) === category)
        .filter((row) => !term
            || [row.sku, row.name, row.category].some((value) => text(value).toLowerCase().includes(term)))
        .slice(0, 24);
    const suggestions = items
        .filter((row) => !term
            || [row.sku, row.name, row.category].some((value) => text(value).toLowerCase().includes(term)))
        .slice(0, 6);
    const previewLines = asRows(preview?.lines);
    const receiptLines = asRows(receipt?.items ?? receipt?.lines ?? receipt?.saleItems);
    const previewTotal = preview ? num(preview.total) : 0;
    const cartCount = cart.reduce((sum, line) => sum + line.quantity, 0);
    const selectedCard = cards.find((row) => text(row.id) === cardId);
    const selectedStudent = students.find((row) => text(row.id) === text(selectedCard?.studentId)
        || text(row.id) === studentId);
    const canCheckout = Boolean(cart.length && studentId && cardUid && terminalCode);

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="POS Checkout"
                    subtitle="Scan a student card, price the basket and settle through Pocket Money"
                    icon="Scan"
                    badge={<span className="badge badge-primary">{cartCount} in basket</span>}
                />
                <Notice error={error} success={success} />

                <div className="retail-workbench">
                    <section className="retail-panel" aria-label="Product browser">
                        <div className="retail-panel-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="Basket" size={18} /></span>
                                Choose products
                            </h2>
                            <span className="retail-panel-hint">Tap a product or scan its SKU</span>
                        </div>
                        <div className="retail-panel-body">
                            <div className="retail-search-row">
                                <Icon name="MagnifyingGlass" size={18} className="text-muted" />
                                <input
                                    className="form-input"
                                    value={search}
                                    onChange={(event) => setSearch(event.target.value)}
                                    onKeyDown={(event) => {
                                        if (event.key === 'Enter' && suggestions[0]) {
                                            event.preventDefault();
                                            addItem(text(suggestions[0].id), true);
                                        }
                                    }}
                                    placeholder="Scan barcode or search by name / SKU..."
                                    aria-label="Scan or search products"
                                />
                            </div>

                            {categories.length > 0 && (
                                <div className="retail-flow-steps mt-3">
                                    <button
                                        type="button"
                                        className={`retail-flow-step ${!category ? 'retail-flow-step-active' : ''}`}
                                        onClick={() => setCategory('')}
                                    >
                                        All
                                    </button>
                                    {categories.map((value) => (
                                        <button
                                            key={value}
                                            type="button"
                                            className={`retail-flow-step ${category === value ? 'retail-flow-step-active' : ''}`}
                                            onClick={() => setCategory(value)}
                                        >
                                            {value}
                                        </button>
                                    ))}
                                </div>
                            )}

                            {suggestions.length > 0 && term && (
                                <div className="mt-3 flex flex-wrap gap-2">
                                    {suggestions.map((row) => (
                                        <button
                                            key={text(row.id)}
                                            type="button"
                                            className="btn-outline btn-xs"
                                            onClick={() => addItem(text(row.id), true)}
                                            title={`${text(row.name)} · ${money(row.price)}`}
                                        >
                                            <Icon name="Plus" size={14} className="mr-1" />
                                            {text(row.sku) || text(row.name)}
                                        </button>
                                    ))}
                                </div>
                            )}

                            <div className="retail-product-grid mt-4">
                                {visibleProducts.map((row) => (
                                    <article className="retail-product-tile" key={text(row.id)}>
                                        <div className="retail-product-top">
                                            <span className="retail-product-icon"><Icon name="Package" size={18} /></span>
                                            <span className={`badge ${num(row.stockQuantity) <= num(row.reorderLevel) ? 'badge-warning' : 'badge-success'}`}>
                                                {num(row.stockQuantity) <= num(row.reorderLevel) ? 'Low' : 'In stock'}
                                            </span>
                                        </div>
                                        <div className="min-w-0">
                                            <p className="retail-product-name" title={text(row.name)}>{text(row.name) || 'Product'}</p>
                                            <p className="retail-product-meta">{text(row.category) || 'Store item'} · {text(row.sku)}</p>
                                        </div>
                                        <p className="retail-product-price">{money(row.price)}</p>
                                        <div className="retail-product-stock">
                                            <span>{text(row.stockQuantity)} available</span>
                                            <button
                                                type="button"
                                                className="btn-primary btn-xs"
                                                onClick={() => addItem(text(row.id))}
                                                aria-label={`Add ${text(row.name)} to basket`}
                                            >
                                                <Icon name="Plus" size={14} className="mr-1" /> Add
                                            </button>
                                        </div>
                                    </article>
                                ))}
                                {visibleProducts.length === 0 && (
                                    <div className="retail-alert retail-alert-info col-span-full">
                                        <Icon name="Info" size={16} />
                                        <span>No products match that scan or category. Try another SKU or name.</span>
                                    </div>
                                )}
                            </div>
                        </div>
                    </section>

                    <section className="retail-panel retail-cart-panel" aria-label="Checkout basket">
                        <div className="retail-panel-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="CashRegister" size={18} /></span>
                                Checkout
                            </h2>
                            <span className="badge badge-primary">{cartCount} items</span>
                        </div>
                        <div className="retail-panel-body">
                            <div className="space-y-3">
                                <label className="block">
                                    <span className="form-label">Card UID (scan / tap)</span>
                                    <div className="retail-search-row">
                                        <span className="retail-product-icon"><Icon name="Scan" size={18} /></span>
                                        <input
                                            className="form-input font-mono"
                                            value={cardUid}
                                            onChange={(event) => { resetCheckoutIdentity(); setCardUid(event.target.value); }}
                                            placeholder="SKUL-NFC-0001"
                                            aria-label="Student card UID"
                                        />
                                    </div>
                                </label>

                                <div className="retail-keyval-grid">
                                    <div className="retail-keyval">
                                        <span>Student</span>
                                        <strong>{selectedStudent ? studentName(selectedStudent) : 'Choose below'}</strong>
                                    </div>
                                    <div className="retail-keyval">
                                        <span>Terminal</span>
                                        <strong className="font-mono">{text(terminalCode) || 'Not selected'}</strong>
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 gap-3">
                                    <label className="block">
                                        <span className="form-label">Student card</span>
                                        <select className="form-input w-full" value={cardId} onChange={(event) => selectCard(event.target.value)}>
                                            <option value="">Scan a card or choose manually...</option>
                                            {cards.map((row) => (
                                                <option key={text(row.id)} value={text(row.id)}>
                                                    {text(row.uid)} · {text(row.status)}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                    <label className="block">
                                        <span className="form-label">Student</span>
                                        <select className="form-input w-full" value={studentId} onChange={(event) => { resetCheckoutIdentity(); setStudentId(event.target.value); }}>
                                            <option value="">Choose student...</option>
                                            {students.map((row) => (
                                                <option key={text(row.id)} value={text(row.id)}>
                                                    {studentName(row)} · {text(row.studentCode)}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                    <label className="block">
                                        <span className="form-label">Terminal</span>
                                        <select className="form-input w-full" value={terminalCode} onChange={(event) => { resetCheckoutIdentity(); setTerminalCode(event.target.value); }}>
                                            <option value="">Choose terminal...</option>
                                            {terminals.map((row) => (
                                                <option key={text(row.id)} value={text(row.code)}>
                                                    {text(row.code)} · {text(row.merchantCode)}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                </div>

                                <details className="text-xs text-muted">
                                    <summary className="cursor-pointer">Sensor device key</summary>
                                    <input
                                        className="form-input mt-2 w-full"
                                        value={deviceKey}
                                        onChange={(event) => { resetCheckoutIdentity(); setDeviceKey(event.target.value); }}
                                        placeholder="Sensor device key"
                                    />
                                </details>
                            </div>

                            <div className="mt-5">
                                <div className="flex items-center justify-between gap-2">
                                    <h3 className="section-label">Basket</h3>
                                    {previewLoading && <span className="retail-panel-hint">Repricing...</span>}
                                </div>
                                <div className="retail-cart-list mt-2">
                                    {cart.map((line) => {
                                        const priced = previewLines.find((row) => text(row.productId) === line.productId);
                                        const product = items.find((row) => text(row.id) === line.productId);
                                        const unitPrice = num(priced?.unitPrice ?? product?.price);
                                        return (
                                            <div className="retail-cart-row" key={line.productId}>
                                                <div className="retail-cart-row-main">
                                                    <strong>{text(priced?.name ?? product?.name) || 'Product'}</strong>
                                                    <span>{text(priced?.sku ?? product?.sku)} · {money(unitPrice)} each</span>
                                                    <div className="retail-qty-control mt-2">
                                                        <button type="button" className="action-btn" onClick={() => changeQuantity(line.productId, line.quantity - 1)} aria-label={`Decrease ${text(product?.name)} quantity`}>
                                                            <Icon name="Minus" size={14} />
                                                        </button>
                                                        <input
                                                            className="form-input"
                                                            type="number"
                                                            min="1"
                                                            value={line.quantity}
                                                            onChange={(event) => changeQuantity(line.productId, Number(event.target.value))}
                                                            aria-label={`Quantity for ${text(priced?.name ?? product?.name)}`}
                                                        />
                                                        <button type="button" className="action-btn" onClick={() => changeQuantity(line.productId, line.quantity + 1)} aria-label={`Increase ${text(product?.name)} quantity`}>
                                                            <Icon name="Plus" size={14} />
                                                        </button>
                                                        <button type="button" className="action-btn" onClick={() => changeQuantity(line.productId, 0)} aria-label={`Remove ${text(product?.name)} from basket`}>
                                                            <Icon name="Trash" size={14} />
                                                        </button>
                                                    </div>
                                                </div>
                                                <div className="text-right">
                                                    <strong className="block text-sm">{money(priced?.lineTotal ?? unitPrice * line.quantity)}</strong>
                                                    <span className="retail-panel-hint">Stock {text(priced?.availableStock ?? product?.stockQuantity ?? '—')}</span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                    {cart.length === 0 && (
                                        <div className="retail-alert">
                                            <Icon name="Basket" size={16} />
                                            <span>Scan a product to begin. Items and prices are confirmed by the server before checkout.</span>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                        <div className="retail-panel-footer">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <label className="block">
                                    <span className="form-label">Discount code</span>
                                    <input className="form-input w-full" value={discountCode} onChange={(event) => { resetCheckoutIdentity(); setDiscountCode(event.target.value); }} placeholder="Optional" />
                                </label>
                                <label className="block">
                                    <span className="form-label">Notes</span>
                                    <input className="form-input w-full" value={notes} onChange={(event) => { resetCheckoutIdentity(); setNotes(event.target.value); }} placeholder="Optional" />
                                </label>
                            </div>
                            <div className="retail-summary-list mt-4">
                                <div className="retail-summary-row"><span>Subtotal</span><strong>{money(preview?.subtotal)}</strong></div>
                                <div className="retail-summary-row"><span>Discount</span><strong>{money(preview?.discount)}</strong></div>
                                <div className="retail-total-row"><span>Amount to charge</span><strong>{money(previewTotal)}</strong></div>
                            </div>
                            {priceChanged && (
                                <div className="retail-alert retail-alert-warning mt-3">
                                    <Icon name="Warning" size={16} />
                                    <span>Server prices changed. Confirm again to charge the updated total.</span>
                                </div>
                            )}
                            <button
                                type="button"
                                className="btn-primary w-full mt-4"
                                disabled={busy || previewLoading || !canCheckout}
                                onClick={() => void checkout()}
                            >
                                <Icon name={priceChanged ? 'ArrowClockwise' : 'Check'} size={18} className="mr-2" />
                                {priceChanged ? 'Confirm new price' : 'Checkout card'}
                            </button>
                        </div>
                    </section>
                </div>
            </div>

            {receipt && (
                <div className="modal-backdrop" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) closeReceipt(); }}>
                    <div className="modal-card retail-modal-card" role="dialog" aria-modal="true" aria-label="Sale receipt">
                        <div className="retail-modal-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="Receipt" size={18} /></span>
                                Receipt · {text(receipt.receiptNo ?? receipt.reference) || 'Sale'}
                            </h2>
                            <button type="button" className="modal-close" onClick={closeReceipt} aria-label="Close receipt"><Icon name="X" size={18} /></button>
                        </div>
                        <div className="retail-modal-body">
                            <div className="retail-keyval-grid">
                                <div className="retail-keyval"><span>Status</span><strong><span className={`badge ${statusTone(text(receipt.status))}`}>{text(receipt.status) || 'COMPLETED'}</span></strong></div>
                                <div className="retail-keyval"><span>Total</span><strong>{money(receipt.total ?? receipt.amount)}</strong></div>
                                <div className="retail-keyval"><span>Balance after</span><strong>{money(receipt.balanceAfter)}</strong></div>
                                <div className="retail-keyval"><span>Payment</span><strong>{text(receipt.paymentMethod) || 'POCKET_MONEY'}</strong></div>
                                <div className="retail-keyval"><span>Student</span><strong>{studentName(students.find((row) => text(row.id) === text(receipt.studentId)))}</strong></div>
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
                        </div>
                        <div className="retail-modal-footer">
                            <button type="button" className="btn-outline" onClick={closeReceipt}>Close</button>
                            <button
                                type="button"
                                className="btn-outline"
                                disabled={!text(receipt.id) || ['CANCELLED', 'REFUNDED', 'RETURNED'].includes(text(receipt.status))}
                                onClick={() => {
                                    const sale = receipt;
                                    setReceipt(null);
                                    openReturn(sale);
                                }}
                            >
                                <Icon name="ArrowCounterClockwise" size={16} className="mr-2" /> Return items
                            </button>
                            <button
                                type="button"
                                className="btn-primary"
                                disabled={!text(receipt.id)}
                                onClick={() => void retailApi.printReceipt(text(receipt.id)).catch((reason) => setError(message(reason)))}
                            >
                                <Icon name="Receipt" size={16} className="mr-2" /> Print receipt
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {returnSale && (
                <div className="modal-backdrop" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) closeReturn(); }}>
                    <div className="modal-card retail-modal-card" role="dialog" aria-modal="true" aria-label="Return items">
                        <div className="retail-modal-header">
                            <h2 className="retail-panel-title">
                                <span className="icon-tile"><Icon name="ArrowCounterClockwise" size={18} /></span>
                                Return · {text(returnSale.receiptNo ?? returnSale.reference) || 'Sale'}
                            </h2>
                            <button type="button" className="modal-close" onClick={closeReturn} aria-label="Close return"><Icon name="X" size={18} /></button>
                        </div>
                        <div className="retail-modal-body">
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
                                                            <input className="form-input" type="number" min="0" max={num(line.quantity) || undefined} value={returnQuantities[lineId] ?? '0'} onChange={(event) => setReturnQuantities((current) => ({ ...current, [lineId]: event.target.value }))} aria-label={`Return quantity for ${text(line.name)}`} style={{ width: 90 }} />
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <div className="table-cell-content">
                                                            <TableIcon label="Disposition" value={returnDispositions[lineId]} />
                                                            <select className="form-input" value={returnDispositions[lineId] ?? 'RESTOCK'} onChange={(event) => setReturnDispositions((current) => ({ ...current, [lineId]: event.target.value }))} aria-label={`Disposition for ${text(line.name)}`}>
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
                            <label className="block mt-4">
                                <span className="form-label">Reason</span>
                                <input className="form-input w-full" value={returnReason} onChange={(event) => setReturnReason(event.target.value)} placeholder="e.g. Wrong item" />
                            </label>
                        </div>
                        <div className="retail-modal-footer">
                            <button type="button" className="btn-outline" onClick={closeReturn}>Cancel</button>
                            <button type="button" className="btn-primary" disabled={busy} onClick={() => void submitReturn()}>
                                <Icon name="ArrowCounterClockwise" size={16} className="mr-2" /> Confirm return
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </Layout>
    );
}
