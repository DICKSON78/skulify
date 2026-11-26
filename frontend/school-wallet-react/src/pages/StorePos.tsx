import Icon from '../components/Icon';
import { useCallback, useEffect, useState } from 'react';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile, money, text } from '../components/ModuleUi';
import { api, operationsApi } from '../lib/api';

type Row = Record<string, unknown>;

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

export default function StorePos() {
    const [items, setItems] = useState<Row[]>([]);
    const [terminals, setTerminals] = useState<Row[]>([]);
    const [cardUid, setCardUid] = useState('');
    const [terminalCode, setTerminalCode] = useState('');
    const [itemId, setItemId] = useState('');
    const [quantity, setQuantity] = useState('1');
    const [receipt, setReceipt] = useState<Row | null>(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = useCallback(() => {
        Promise.all([
            api.list<Row>('store-items', 0, 100, ''),
            api.list<Row>('store-terminals', 0, 100, ''),
        ])
            .then(([itemPage, terminalPage]) => {
                setItems(itemPage.content.filter((row) => row.active === true));
                setTerminals(terminalPage.content.filter((row) => text(row.status) === 'ACTIVE'));
                setItemId((current) => current || itemPage.content[0]?.id?.toString() || '');
                setTerminalCode((current) => current || terminalPage.content[0]?.code?.toString() || '');
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const selected = items.find((row) => text(row.id) === itemId);
    const unitPrice = Number(selected?.price ?? 0);
    const total = unitPrice * (Number(quantity) || 0);
    const lowStock = items.filter((row) => Number(row.stockQuantity) <= Number(row.reorderLevel)).length;

    const charge = async () => {
        setError('');
        setSuccess('');
        try {
            const result = await operationsApi.posCharge<Row>({
                cardUid,
                terminalCode,
                storeItemId: itemId,
                quantity: Number(quantity),
            });
            setReceipt(result);
            setSuccess('Payment approved. Card balance and stock updated.');
            load();
        } catch (reason) {
            setError(message(reason));
            setReceipt(null);
        }
    };

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Store POS"
                    subtitle="Scan the student's card like a supermarket checkout"
                    icon="Scan"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Products" value={items.length} icon="Basket" tone="primary" footer="Available in the store" />
                    <StatTile label="Low stock" value={lowStock} icon="Warning" tone="warning" footer="Items needing restock" />
                    <StatTile label="Active terminals" value={terminals.length} icon="Desktop" tone="info" footer="Ready for checkout" />
                    <StatTile label="Current total" value={money(total)} icon="Coins" tone="success" footer="Today's sales so far" />
                </div>

                <FormCard title="Checkout" icon="CashRegister">
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                        <label className="block">
                            <span className="form-label">Card UID (scan / tap)</span>
                            <input
                                className="form-input w-full font-mono"
                                value={cardUid}
                                onChange={(event) => setCardUid(event.target.value)}
                                placeholder="CARD-XXXXXXXX"
                                autoFocus
                            />
                            <span className="mt-1 block text-xs text-gray-400">
                                NFC, RFID or QR identifier printed on the school card.
                            </span>
                        </label>
                        <label className="block">
                            <span className="form-label">Terminal</span>
                            <select className="form-input w-full" value={terminalCode} onChange={(event) => setTerminalCode(event.target.value)}>
                                <option value="">No terminal (manual sale)</option>
                                {terminals.map((terminal) => (
                                    <option key={text(terminal.id)} value={text(terminal.code)}>
                                        {text(terminal.code)} · {text(terminal.name)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Item</span>
                            <select className="form-input w-full" value={itemId} onChange={(event) => setItemId(event.target.value)}>
                                <option value="">Choose item...</option>
                                {items.map((item) => (
                                    <option key={text(item.id)} value={text(item.id)}>
                                        {text(item.name)} · {money(item.price)} · {text(item.stockQuantity)} in stock
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Quantity</span>
                            <input
                                className="form-input w-full"
                                type="number"
                                min="1"
                                value={quantity}
                                onChange={(event) => setQuantity(event.target.value)}
                            />
                        </label>
                    </div>
                    <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
                        <div>
                            <p className="text-sm text-gray-500">Amount to charge</p>
                            <p className="text-2xl font-bold text-primary-dark">{money(total)}</p>
                        </div>
                        <button
                            type="button"
                            disabled={!cardUid || !itemId || total <= 0}
                            onClick={() => void charge()}
                            className="btn-primary"
                        >
                            <Icon name="Check" size={18} className="mr-2" /> Charge card
                        </button>
                    </div>
                </FormCard>

                {receipt && (
                    <FormCard title="Receipt" icon="Receipt">
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
                            <div><p className="text-gray-500">Reference</p><p className="font-mono font-semibold">{text(receipt.reference)}</p></div>
                            <div><p className="text-gray-500">Type</p><p className="font-semibold">{text(receipt.type)}</p></div>
                            <div><p className="text-gray-500">Amount</p><p className="font-semibold">{money(receipt.amount)}</p></div>
                            <div><p className="text-gray-500">Balance after</p><p className="font-semibold">{money(receipt.balanceAfter)}</p></div>
                            <div className="sm:col-span-4"><p className="text-gray-500">Description</p><p className="text-gray-800">{text(receipt.description)}</p></div>
                        </div>
                    </FormCard>
                )}
            </div>
        </Layout>
    );
}
