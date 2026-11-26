import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../components/Icon';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { useAuth } from '../auth/AuthContext';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile, money, text } from '../components/ModuleUi';
import { api, operationsApi } from '../lib/api';

type Row = Record<string, unknown>;

type PocketTab = 'overview' | 'topups' | 'cards' | 'purchases' | 'cashdesk' | 'reports';

const pocketTabs: { key: PocketTab; label: string; icon: string }[] = [
    { key: 'overview', label: 'Overview', icon: 'SquaresFour' },
    { key: 'topups', label: 'Top-ups', icon: 'Phone' },
    { key: 'cards', label: 'Cards', icon: 'CreditCard' },
    { key: 'purchases', label: 'Purchases', icon: 'Scan' },
    { key: 'cashdesk', label: 'Cash desk', icon: 'Money' },
    { key: 'reports', label: 'Reports', icon: 'Receipt' },
];

type LimitRow = {
    category?: string;
    dailyLimit?: number;
    weeklyLimit?: number;
    monthlyLimit?: number;
    active?: boolean;
};

type Balance = {
    balance: number;
    dailyLimit: number;
    weeklyLimit: number;
    monthlyLimit: number;
    spentToday: number;
    spentThisWeek: number;
    spentThisMonth: number;
    limits?: LimitRow[];
};

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const statusTone = (status: string): string => {
    if (status === 'PENDING') return 'badge-warning';
    if (status === 'POSTED') return 'badge-success';
    if (status === 'FAILED') return 'badge-danger';
    if (status === 'REVERSED') return 'badge-info';
    return 'badge-primary';
};

const percent = (spent: number | undefined, limit: number | undefined): number => {
    const cap = Number(limit ?? 0);
    if (cap <= 0) {
        return 0;
    }
    return Math.min(100, Math.round((Number(spent ?? 0) / cap) * 100));
};

function LimitMeter({
    label,
    spent,
    limit,
}: {
    label: string;
    spent: number | undefined;
    limit: number | undefined;
}) {
    const used = percent(spent, limit);
    return (
        <div className="pocket-meter">
            <div className="pocket-meter-head">
                <span>{label}</span>
                <span>{money(spent)} / {money(limit)}</span>
            </div>
            <div
                className="pocket-meter-track"
                role="progressbar"
                aria-label={`${label} usage`}
                aria-valuenow={used}
                aria-valuemin={0}
                aria-valuemax={100}
            >
                <div className="pocket-meter-fill" style={{ width: `${used}%` }} />
            </div>
            <span className="pocket-meter-note">{used}% used</span>
        </div>
    );
}

function BankCard({
    uid,
    balanceValue,
    account,
    status,
}: {
    uid: string;
    balanceValue: string;
    account: string;
    status: string;
}) {
    return (
        <section className="sk3" aria-label="Pocket Money card">
            <svg
                className="sk3-bg"
                viewBox="0 0 440 277"
                preserveAspectRatio="xMidYMid slice"
                fill="none"
                aria-hidden="true"
            >
                <defs>
                    <linearGradient id="sk3Sh" x1="0" y1="0" x2="1" y2="1">
                        <stop offset="0" stopColor="#fff" stopOpacity=".28" />
                        <stop offset="1" stopColor="#fff" stopOpacity="0" />
                    </linearGradient>
                </defs>
                <path d="M0 0H250C200 80 120 190 0 240Z" fill="url(#sk3Sh)" />
                <g stroke="#fff" strokeWidth="1.2" opacity=".14">
                    <path d="M-10 250C120 210 240 280 450 170" />
                    <path d="M-10 225C120 185 240 255 450 145" />
                    <path d="M-10 200C120 160 240 230 450 120" />
                    <path d="M-10 275C120 235 240 305 450 195" />
                    <path d="M-10 175C120 135 240 205 450 95" />
                </g>
            </svg>

            <div className="sk3-row">
                <div className="sk3-brand">
                    <Icon name="GraduationCap" size={24} /> Skulify
                </div>
                <div className="sk3-type">
                    Student bank card
                    <span>Skulify card</span>
                </div>
            </div>

            <div className="sk3-row">
                <span className="sk3-chip" aria-hidden="true" />
                <span className="sk3-tap">
                    <Icon name="Scan" size={16} /> Tap on the school sensor to pay
                </span>
            </div>

            <div className="sk3-num">{uid || 'SKUL-NFC-0001'}</div>

            <div className="sk3-bottom">
                <div>
                    <div className="sk3-lbl">Account</div>
                    <div className="sk3-val">{account}</div>
                </div>
                <div>
                    <div className="sk3-lbl">Card balance</div>
                    <div className="sk3-bal">{balanceValue}</div>
                </div>
                <div className="sk3-end">
                    <span className="sk3-lbl">Status</span>
                    <span className="sk3-pill"><b />{status}</span>
                    <span className="sk3-mark" aria-hidden="true"><i /><i /></span>
                </div>
            </div>
        </section>
    );
}

function TransactionTable({
    rows,
    terminals,
    emptyMessage,
}: {
    rows: Row[];
    terminals: Row[];
    emptyMessage: string;
}) {
    return (
        <div className="re-table-wrap">
            <table className="re-table">
                <thead>
                    <tr>
                        <th><span className="table-col"><TableIcon label="Reference" /><span>Reference</span></span></th>
                        <th><span className="table-col"><TableIcon label="Type" /><span>Type</span></span></th>
                        <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                        <th><span className="table-col"><TableIcon label="Category" /><span>Category</span></span></th>
                        <th><span className="table-col"><TableIcon label="Amount" /><span>Amount</span></span></th>
                        <th><span className="table-col"><TableIcon label="Balance after" /><span>Balance after</span></span></th>
                        <th><span className="table-col"><TableIcon label="Description" /><span>Description</span></span></th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map((row) => {
                        const terminal = terminals.find(
                            (candidate) => text(candidate.id) === text(row.terminalId),
                        );
                        return (
                            <tr key={text(row.id)}>
                                <td><div className="table-cell-content"><TableIcon label="Reference" value={row.reference} /><span className="min-w-0 font-mono text-xs">{text(row.reference)}</span></div></td>
                                <td><div className="table-cell-content"><TableIcon label="Type" value={row.type} /><span className="min-w-0"><span className={`badge ${text(row.type) === 'TOPUP' ? 'badge-success' : text(row.type) === 'REFUND' ? 'badge-info' : 'badge-primary'}`}>{text(row.type)}</span></span></div></td>
                                <td><div className="table-cell-content"><TableIcon label="Status" value={row.status} /><span className="min-w-0"><span className={`badge ${statusTone(text(row.status) || 'POSTED')}`}>{text(row.status) || 'POSTED'}</span></span></div></td>
                                <td><div className="table-cell-content"><TableIcon label="Category" value={row.category} /><span className="min-w-0">{text(row.category)}</span></div></td>
                                <td><div className="table-cell-content"><TableIcon label="Amount" value={row.amount} /><span className="min-w-0 font-semibold">{money(row.amount)}</span></div></td>
                                <td><div className="table-cell-content"><TableIcon label="Balance after" value={row.balanceAfter} /><span className="min-w-0">{money(row.balanceAfter)}</span></div></td>
                                <td>
                                    <div className="table-cell-content">
                                        <TableIcon label="Description" value={row.description} />
                                        <span className="min-w-0 text-muted">
                                            {terminal && <strong className="mr-1">{text(terminal.code)} ·</strong>}
                                            {text(row.description)}
                                        </span>
                                    </div>
                                </td>
                            </tr>
                        );
                    })}
                    {rows.length === 0 && (
                        <TableEmptyState colSpan={7} icon="Receipt" message={emptyMessage} />
                    )}
                </tbody>
            </table>
        </div>
    );
}

export default function PocketMoney() {
    const { can } = useAuth();
    const posAllowed = can('/store-pos');
    const cashAllowed = can('/cash-requests');

    const [tab, setTab] = useState<PocketTab>('overview');
    const [cards, setCards] = useState<Row[]>([]);
    const [students, setStudents] = useState<Row[]>([]);
    const [items, setItems] = useState<Row[]>([]);
    const [terminals, setTerminals] = useState<Row[]>([]);
    const [cardId, setCardId] = useState('');
    const [balance, setBalance] = useState<Balance | null>(null);
    const [transactions, setTransactions] = useState<Row[]>([]);
    const [amount, setAmount] = useState('');
    const [reference, setReference] = useState('');
    const [daily, setDaily] = useState('');
    const [weekly, setWeekly] = useState('');
    const [monthly, setMonthly] = useState('');
    const [category, setCategory] = useState('');
    const [terminalCode, setTerminalCode] = useState('');
    const [itemId, setItemId] = useState('');
    const [quantity, setQuantity] = useState('1');
    const [receipt, setReceipt] = useState<Row | null>(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const loadSupporting = useCallback(() => {
        api.list<Row>('store-items', 0, 100, '')
            .then((page) => {
                setItems(page.content.filter((row) => row.active === true));
                setItemId((current) => current || page.content[0]?.id?.toString() || '');
            })
            .catch(() => undefined);
        if (posAllowed) {
            api.list<Row>('store-terminals', 0, 100, '')
                .then((page) => {
                    const active = page.content.filter((row) => text(row.status) === 'ACTIVE');
                    setTerminals(active);
                    setTerminalCode((current) => current || active[0]?.code?.toString() || '');
                })
                .catch(() => undefined);
        }
    }, [posAllowed]);

    const loadCards = useCallback(() => {
        api.list<Row>('pocket-cards', 0, 100, '')
            .then((page) => {
                setCards(page.content);
                const preferred = page.content.find((row) => text(row.status) === 'ACTIVE')
                    ?? page.content[0];
                setCardId((current) => current || preferred?.id?.toString() || '');
            })
            .catch((reason) => setError(message(reason)));
        api.list<Row>('students', 0, 100, '')
            .then((page) => setStudents(page.content))
            .catch(() => undefined);
    }, []);

    const loadCard = useCallback((id: string) => {
        if (!id) {
            return;
        }
        Promise.all([
            operationsApi.pocketBalance<Balance>(id),
            operationsApi.cardTransactions<Row[]>(id),
        ])
            .then(([summary, history]) => {
                setBalance(summary);
                setTransactions(history);
                setDaily(String(summary.dailyLimit ?? 0));
                setWeekly(String(summary.weeklyLimit ?? 0));
                setMonthly(String(summary.monthlyLimit ?? 0));
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        loadCards();
        loadSupporting();
    }, [loadCards, loadSupporting]);

    useEffect(() => {
        if (cardId) {
            loadCard(cardId);
        }
    }, [cardId, loadCard]);

    const run = async (action: () => Promise<unknown>, done: string) => {
        setError('');
        setSuccess('');
        try {
            await action();
            setSuccess(done);
            loadCard(cardId);
        } catch (reason) {
            setError(message(reason));
        }
    };

    const charge = async () => {
        setError('');
        setSuccess('');
        try {
            const result = await operationsApi.posCharge<Row>({
                cardUid: text(selectedCard?.cardUid),
                terminalCode,
                storeItemId: itemId,
                quantity: Number(quantity),
            });
            setReceipt(result);
            setSuccess('Sensor payment approved. Card balance and stock updated.');
            setQuantity('1');
            loadCard(cardId);
            loadSupporting();
        } catch (reason) {
            setError(message(reason));
            setReceipt(null);
        }
    };

    const pushUssd = async () => {
        setError('');
        setSuccess('');
        try {
            await operationsApi.pushUssdTopUp(cardId, {
                amount,
                providerReference: reference,
            });
            setSuccess('PUSH USSD sent to the parent phone. Approve it to load the child card.');
            setAmount('');
            setReference('');
            loadCard(cardId);
        } catch (reason) {
            setError(message(reason));
        }
    };

    const decideTopUp = async (transactionId: string, approve: boolean) => {
        setError('');
        setSuccess('');
        try {
            if (approve) {
                await operationsApi.approveTopUp(transactionId);
            } else {
                await operationsApi.declineTopUp(transactionId);
            }
            setSuccess(
                approve
                    ? 'USSD top-up approved. Money is now on the child card.'
                    : 'USSD top-up declined. No money was deducted.',
            );
            loadCard(cardId);
        } catch (reason) {
            setError(message(reason));
        }
    };

    const selectedCard = cards.find((row) => text(row.id) === cardId);
    const selectedStudent = students.find(
        (row) => text(row.id) === text(selectedCard?.studentId),
    );
    const studentName = selectedStudent
        ? `${text(selectedStudent.firstName)} ${text(selectedStudent.lastName)}`.trim()
        : 'Student card';
    const activeTerminals = terminals.filter((row) => text(row.status) === 'ACTIVE');
    const selectedItem = items.find((row) => text(row.id) === itemId);
    const chargeTotal = Number(selectedItem?.price ?? 0) * (Number(quantity) || 0);
    const latestPurchase = transactions.find((row) => text(row.type) === 'PURCHASE');
    const pendingTopups = transactions.filter(
        (row) => text(row.type) === 'TOPUP' && text(row.status) === 'PENDING',
    );
    const topupRows = transactions.filter((row) => text(row.type) === 'TOPUP');
    const purchaseRows = transactions.filter((row) => text(row.type) === 'PURCHASE');
    const lastTap = latestPurchase?.createdAt
        ? new Date(String(latestPurchase.createdAt)).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
        })
        : 'No tap yet';
    const categoryLimits = Array.isArray(balance?.limits) ? balance.limits : [];

    const card = (
        <BankCard
            uid={text(selectedCard?.cardUid) || 'SKUL-NFC-0001'}
            balanceValue={money(balance?.balance)}
            account={studentName}
            status={text(selectedCard?.status) || 'ACTIVE'}
        />
    );

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Pocket Money"
                    subtitle="haba ndo bank ya mwanafunzi na pocket money idea ilipo and connected ith ntf card with sensor for transactions"
                    icon="PiggyBank"
                />
                <Notice error={error} success={success} />

                <div className="pocket-card-switcher">
                    <span className="pocket-card-switcher-label">
                        <Icon name="CreditCard" size={16} /> Selected card
                    </span>
                    <select
                        className="form-input"
                        value={cardId}
                        onChange={(event) => setCardId(event.target.value)}
                        aria-label="Select student card"
                    >
                        <option value="">Choose a student card...</option>
                        {cards.map((row) => (
                            <option key={text(row.id)} value={text(row.id)}>
                                {text(row.cardUid)} · balance {money(row.balance)}
                            </option>
                        ))}
                    </select>
                    {selectedCard && (
                        <span className="badge badge-success">
                            <Icon name="CheckCircle" size={14} /> {text(selectedCard.status)}
                        </span>
                    )}
                </div>

                <div className="tab-nav pocket-tab-nav" role="tablist" aria-label="Pocket Money sections">
                    {pocketTabs.map((item) => (
                        <button
                            key={item.key}
                            type="button"
                            role="tab"
                            aria-selected={tab === item.key}
                            className={`tab-btn pocket-tab-btn ${tab === item.key ? 'tab-btn-active' : 'tab-btn-inactive'}`}
                            onClick={() => setTab(item.key)}
                        >
                            <Icon name={item.icon} size={16} /> {item.label}
                        </button>
                    ))}
                </div>

                {tab === 'overview' && (
                    <div className="space-y-4">
                        <div className="pocket-hero">
                            <section className="pocket-intro">
                                <span className="pocket-eyebrow">
                                    <Icon name="GraduationCap" size={16} /> Haba student bank
                                </span>
                                <h2 className="pocket-title">Pocket money, ready for the sensor</h2>
                                <p className="pocket-copy">
                                    Load a child&apos;s school card, set the spending envelope, then let the
                                    NFC terminal handle every snack, book and bus token at the point of sale.
                                </p>
                                <div className="pocket-chip-row">
                                    <span className="pocket-chip"><Icon name="CreditCard" size={15} /> NFC card</span>
                                    <span className="pocket-chip"><Icon name="Lightning" size={15} /> Sensor payments</span>
                                    <span className="pocket-chip"><Icon name="Shield" size={15} /> Parent controls</span>
                                </div>
                                <div className="pocket-sensor-strip">
                                    <div>
                                        <Icon name="Scan" size={18} />
                                        <span>Card reader</span>
                                        <strong>Ready</strong>
                                    </div>
                                    <div>
                                        <Icon name="CashRegister" size={18} />
                                        <span>Terminal</span>
                                        <strong>{text(activeTerminals[0]?.code) || 'School POS'}</strong>
                                    </div>
                                    <div>
                                        <Icon name="Clock" size={18} />
                                        <span>Last sensor tap</span>
                                        <strong>{lastTap}</strong>
                                    </div>
                                </div>
                            </section>
                            {card}
                        </div>

                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                            <StatTile label="Card balance" value={money(balance?.balance)} icon="PiggyBank" tone="primary" footer="Salio la Pocket Money" />
                            <StatTile label="Spent today" value={money(balance?.spentToday)} icon="Basket" tone="warning" footer="Since midnight" />
                            <StatTile label="Daily limit" value={money(balance?.dailyLimit)} icon="Gauge" tone="info" footer="Maximum per day" />
                            <StatTile label="Weekly limit" value={money(balance?.weeklyLimit)} icon="CalendarBlank" tone="success" footer="Maximum per week" />
                        </div>

                        <FormCard title="Spending envelope" icon="Gauge">
                            <div className="pocket-meter-grid">
                                <LimitMeter label="Daily" spent={balance?.spentToday} limit={balance?.dailyLimit} />
                                <LimitMeter label="Weekly" spent={balance?.spentThisWeek} limit={balance?.weeklyLimit} />
                                <LimitMeter label="Monthly" spent={balance?.spentThisMonth} limit={balance?.monthlyLimit} />
                            </div>
                            {categoryLimits.length > 0 && (
                                <div className="pocket-category-limits">
                                    {categoryLimits.filter((row) => row.active !== false).map((row) => (
                                        <span key={text(row.category)} className="pocket-chip">
                                            <Icon name="Tag" size={14} /> {text(row.category)} · {money(row.dailyLimit)}/day
                                        </span>
                                    ))}
                                </div>
                            )}
                        </FormCard>
                    </div>
                )}

                {tab === 'topups' && (
                    <div className="space-y-4">
                        <FormCard title="Weka Pocket Money" icon="Phone">
                            <p className="pocket-copy mb-4">
                                Enter the amount, press <strong>PUSH USSD</strong>, then approve the prompt
                                on the parent phone. The money moves straight onto the child&apos;s Skulify card.
                            </p>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <label className="block">
                                    <span className="form-label">Amount (TZS)</span>
                                    <input
                                        className="form-input w-full"
                                        type="number"
                                        min="1"
                                        value={amount}
                                        onChange={(event) => setAmount(event.target.value)}
                                        placeholder="e.g. 20000"
                                    />
                                </label>
                                <label className="block">
                                    <span className="form-label">Payment reference (optional)</span>
                                    <input
                                        className="form-input w-full"
                                        value={reference}
                                        onChange={(event) => setReference(event.target.value)}
                                        placeholder="e.g. M-PESA-QWERTY"
                                    />
                                </label>
                            </div>
                            <div className="pocket-topup-actions mt-4">
                                <button
                                    type="button"
                                    disabled={!cardId || !amount}
                                    onClick={() => void pushUssd()}
                                    className="btn-primary"
                                >
                                    <Icon name="Phone" size={18} className="mr-2" /> PUSH USSD
                                </button>
                                <button
                                    type="button"
                                    disabled={!cardId || !amount}
                                    onClick={() =>
                                        void run(
                                            () =>
                                                operationsApi.topUp(cardId, {
                                                    amount,
                                                    providerReference: reference,
                                                    category: 'TOPUP',
                                                }),
                                            'Top-up recorded. The child can now use the card at the store.',
                                        )
                                    }
                                    className="btn-outline"
                                >
                                    <Icon name="Plus" size={18} className="mr-2" /> Add money directly
                                </button>
                            </div>
                        </FormCard>

                        {pendingTopups.length > 0 && (
                            <FormCard title="Pending USSD approvals" icon="Bell">
                                <p className="pocket-copy mb-4">
                                    These payments are waiting on the parent phone. Money is only added to the
                                    child card after approval.
                                </p>
                                <div className="pocket-pending-list">
                                    {pendingTopups.map((row) => (
                                        <div key={text(row.id)} className="pocket-pending-row">
                                            <div className="pocket-pending-main">
                                                <Icon name="Phone" size={18} />
                                                <div>
                                                    <strong>{text(row.providerReference)}</strong>
                                                    <span>{money(row.amount)} · {text(row.description)}</span>
                                                </div>
                                            </div>
                                            <div className="pocket-pending-actions">
                                                <button
                                                    type="button"
                                                    className="btn-primary btn-xs"
                                                    onClick={() => void decideTopUp(text(row.id), true)}
                                                >
                                                    Approve
                                                </button>
                                                <button
                                                    type="button"
                                                    className="btn-outline btn-xs"
                                                    onClick={() => void decideTopUp(text(row.id), false)}
                                                >
                                                    Decline
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </FormCard>
                        )}

                        <FormCard title="Top-up history" icon="Receipt">
                            <TransactionTable rows={topupRows} terminals={terminals} emptyMessage="No top-ups yet." />
                        </FormCard>
                    </div>
                )}

                {tab === 'cards' && (
                    <div className="space-y-4">
                        <FormCard title="Pocket Money card" icon="CreditCard">
                            <div className="pocket-card-preview">{card}</div>
                        </FormCard>

                        <FormCard title="Spending controls" icon="Shield">
                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                                <label className="block">
                                    <span className="form-label">Daily limit</span>
                                    <input className="form-input w-full" type="number" min="0" value={daily} onChange={(event) => setDaily(event.target.value)} />
                                </label>
                                <label className="block">
                                    <span className="form-label">Weekly limit</span>
                                    <input className="form-input w-full" type="number" min="0" value={weekly} onChange={(event) => setWeekly(event.target.value)} />
                                </label>
                                <label className="block">
                                    <span className="form-label">Monthly limit</span>
                                    <input className="form-input w-full" type="number" min="0" value={monthly} onChange={(event) => setMonthly(event.target.value)} />
                                </label>
                            </div>
                            <label className="block mt-3">
                                <span className="form-label">Category (optional, e.g. SNACKS)</span>
                                <input className="form-input w-full" value={category} onChange={(event) => setCategory(event.target.value)} />
                            </label>
                            <div className="pocket-meter-grid mt-4">
                                <LimitMeter label="Daily" spent={balance?.spentToday} limit={balance?.dailyLimit} />
                                <LimitMeter label="Weekly" spent={balance?.spentThisWeek} limit={balance?.weeklyLimit} />
                                <LimitMeter label="Monthly" spent={balance?.spentThisMonth} limit={balance?.monthlyLimit} />
                            </div>
                            {categoryLimits.length > 0 && (
                                <div className="pocket-category-limits">
                                    {categoryLimits.filter((row) => row.active !== false).map((row) => (
                                        <span key={text(row.category)} className="pocket-chip">
                                            <Icon name="Tag" size={14} /> {text(row.category)} · {money(row.dailyLimit)}/day
                                        </span>
                                    ))}
                                </div>
                            )}
                            <button
                                type="button"
                                disabled={!cardId}
                                onClick={() =>
                                    void run(
                                        () =>
                                            operationsApi.setLimits(cardId, {
                                                category,
                                                dailyLimit: daily,
                                                weeklyLimit: weekly,
                                                monthlyLimit: monthly,
                                            }),
                                        'Spending limits updated.',
                                    )
                                }
                                className="btn-primary mt-4"
                            >
                                <Icon name="FloppyDisk" size={18} className="mr-2" /> Save limits
                            </button>
                        </FormCard>
                    </div>
                )}

                {tab === 'purchases' && (
                    <div className="space-y-4">
                        {posAllowed ? (
                            <FormCard title="Sensor transaction" icon="Scan">
                                <p className="pocket-copy mb-4">
                                    Tap the student card on the reader, or enter its UID below. The sensor posts
                                    the purchase to the same Haba bank ledger.
                                </p>
                                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                                    <label className="block">
                                        <span className="form-label">Card UID (sensor tap)</span>
                                        <input
                                            className="form-input w-full font-mono"
                                            value={text(selectedCard?.cardUid)}
                                            readOnly
                                            placeholder="SKUL-NFC-0001"
                                        />
                                    </label>
                                    <label className="block">
                                        <span className="form-label">Terminal</span>
                                        <select className="form-input w-full" value={terminalCode} onChange={(event) => setTerminalCode(event.target.value)}>
                                            <option value="">Choose terminal...</option>
                                            {activeTerminals.map((terminal) => (
                                                <option key={text(terminal.id)} value={text(terminal.code)}>
                                                    {text(terminal.code)} · {text(terminal.location)}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                    <label className="block">
                                        <span className="form-label">Store item</span>
                                        <select className="form-input w-full" value={itemId} onChange={(event) => setItemId(event.target.value)}>
                                            <option value="">Choose item...</option>
                                            {items.map((item) => (
                                                <option key={text(item.id)} value={text(item.id)}>
                                                    {text(item.name)} · {money(item.price)}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                    <label className="block">
                                        <span className="form-label">Quantity</span>
                                        <input className="form-input w-full" type="number" min="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
                                    </label>
                                </div>
                                <div className="pocket-charge-row">
                                    <div>
                                        <span>Amount to charge</span>
                                        <strong>{money(chargeTotal)}</strong>
                                    </div>
                                    <button
                                        type="button"
                                        disabled={!selectedCard || !terminalCode || !itemId || chargeTotal <= 0}
                                        onClick={() => void charge()}
                                        className="btn-primary"
                                    >
                                        <Icon name="Lightning" size={18} className="mr-2" /> Charge sensor
                                    </button>
                                </div>
                                {receipt && (
                                    <div className="pocket-receipt">
                                        <Icon name="Receipt" size={18} />
                                        <span>
                                            Receipt {text(receipt.reference)} · {money(receipt.amount)} ·
                                            balance {money(receipt.balanceAfter)}
                                        </span>
                                        <span className="badge badge-success">{text(receipt.type)}</span>
                                    </div>
                                )}
                            </FormCard>
                        ) : (
                            <FormCard title="Sensor checkout" icon="Scan">
                                <p className="pocket-copy">
                                    Sensor payments are posted from the POS Checkout page. Use the card
                                    selector above to choose the student card before tapping.
                                </p>
                                {can('/store-pos') && (
                                    <Link className="btn-outline mt-4 inline-flex" to="/store-pos">
                                        <Icon name="Scan" size={18} className="mr-2" /> Open POS Checkout
                                    </Link>
                                )}
                            </FormCard>
                        )}

                        <FormCard title="Purchases" icon="Basket">
                            <TransactionTable rows={purchaseRows} terminals={terminals} emptyMessage="No purchases yet." />
                        </FormCard>
                    </div>
                )}

                {tab === 'cashdesk' && (
                    <FormCard title="Cash desk" icon="Money">
                        <p className="pocket-copy">
                            Teacher cash requests, parent approval and agent payout references are kept
                            together in the Cash Requests desk. Nothing is deducted from the child card
                            until a guardian approves the request.
                        </p>
                        {cashAllowed ? (
                            <Link className="btn-primary mt-4 inline-flex" to="/cash-requests">
                                <Icon name="Money" size={18} className="mr-2" /> Open Cash Requests
                            </Link>
                        ) : (
                            <p className="notice mt-4">Cash desk access is not available for this role.</p>
                        )}
                    </FormCard>
                )}

                {tab === 'reports' && (
                    <FormCard title="Card activity report" icon="Receipt">
                        <TransactionTable rows={transactions} terminals={terminals} emptyMessage="No transactions yet." />
                    </FormCard>
                )}
            </div>
        </Layout>
    );
}
