import Icon from '../components/Icon';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import { api, operationsApi } from '../lib/api';

type Row = Record<string, unknown>;

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

export default function OperationsHub() {
    const [overview, setOverview] = useState<Row | null>(null);
    const [requests, setRequests] = useState<Row[]>([]);
    const [tasks, setTasks] = useState<Row[]>([]);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = useCallback(() => {
        Promise.all([
            operationsApi.operationsSummary<Row>(),
            api.list<Row>('purchase-requests', 0, 20, ''),
            api.list<Row>('school-tasks', 0, 20, ''),
        ])
            .then(([summary, requestPage, taskPage]) => {
                setOverview(summary);
                setRequests(requestPage.content);
                setTasks(taskPage.content);
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const run = async (action: () => Promise<unknown>, done: string) => {
        setError('');
        setSuccess('');
        try {
            await action();
            setSuccess(done);
            load();
        } catch (reason) {
            setError(message(reason));
        }
    };

    const quickLinks = [
        { to: '/departments', label: 'Departments', icon: 'Buildings' },
        { to: '/staff-attendance', label: 'Staff attendance', icon: 'ClockCounterClockwise' },
        { to: '/school-tasks', label: 'Work queue', icon: 'ListChecks' },
        { to: '/inventory-items', label: 'Inventory', icon: 'Package' },
        { to: '/purchase-requests', label: 'Purchases', icon: 'SealCheck' },
        { to: '/store-pos', label: 'Store POS', icon: 'Scan' },
        { to: '/books', label: 'Library', icon: 'BookOpen' },
        { to: '/lab-sessions', label: 'Computer lab', icon: 'Code' },
    ];

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Operations Hub"
                    subtitle="Departments, staff work, inventory and purchasing in one place"
                    icon="FlowArrow"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
                    <StatTile label="Departments" value={text(overview?.departments) || '—'} icon="Buildings" tone="primary" footer="Across the school" />
                    <StatTile label="Low stock" value={text(overview?.lowStock) || '—'} icon="Warning" tone="warning" footer="Items needing restock" />
                    <StatTile label="Pending purchases" value={text(overview?.pendingRequests) || '—'} icon="Hourglass" tone="info" footer="Awaiting approval" />
                    <StatTile label="Open tasks" value={text(overview?.openTasks) || '—'} icon="ListChecks" tone="success" footer="Not yet completed" />
                    <StatTile label="Staff today" value={text(overview?.staffToday) || '—'} icon="ClockUser" tone="info" footer="Marked present today" />
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    {quickLinks.map((link) => (
                        <Link
                            key={link.to}
                            to={link.to}
                            className="quick-action flex items-center gap-3"
                        >
                            <Icon name={link.icon} size={20} />
                            <span className="text-sm font-semibold text-gray-800">{link.label}</span>
                        </Link>
                    ))}
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <FormCard title="Purchase requests" icon="SealCheck">
                        <div className="space-y-2">
                            {requests.filter((row) => text(row.status) === 'PENDING').map((row) => (
                                <div key={text(row.id)} className="flex items-center justify-between rounded-xl border border-gray-100 px-3 py-2">
                                    <div className="min-w-0">
                                        <p className="text-sm font-semibold text-gray-900">{text(row.reference)}</p>
                                        <p className="text-xs text-gray-500 truncate">{text(row.purpose)} · {text(row.quantity)} units</p>
                                    </div>
                                    <div className="flex gap-2 shrink-0">
                                        <button
                                            type="button"
                                            className="btn-outline px-3 py-1.5 text-xs"
                                            onClick={() => void run(() => operationsApi.approvePurchase(text(row.id), 'REJECT'), 'Request rejected.')}
                                        >
                                            Reject
                                        </button>
                                        <button
                                            type="button"
                                            className="btn-primary px-3 py-1.5 text-xs"
                                            onClick={() => void run(() => operationsApi.approvePurchase(text(row.id), 'APPROVE'), 'Request approved and task created.')}
                                        >
                                            Approve
                                        </button>
                                    </div>
                                </div>
                            ))}
                            {requests.filter((row) => text(row.status) === 'PENDING').length === 0 && (
                                <p className="text-sm text-gray-400">No pending purchase requests.</p>
                            )}
                        </div>
                    </FormCard>

                    <FormCard title="Open work queue" icon="ListChecks">
                        <div className="space-y-2">
                            {tasks.filter((row) => text(row.status) === 'OPEN').slice(0, 10).map((row) => (
                                <div key={text(row.id)} className="flex items-center justify-between rounded-xl border border-gray-100 px-3 py-2">
                                    <div className="min-w-0">
                                        <p className="text-sm font-semibold text-gray-900">{text(row.title)}</p>
                                        <p className="text-xs text-gray-500">Priority {text(row.priority)} · due {text(row.dueDate) || '—'}</p>
                                    </div>
                                    <button
                                        type="button"
                                        className="btn-outline px-3 py-1.5 text-xs"
                                        onClick={() => void run(() => operationsApi.completeTask(text(row.id)), 'Task completed.')}
                                    >
                                        Complete
                                    </button>
                                </div>
                            ))}
                            {tasks.filter((row) => text(row.status) === 'OPEN').length === 0 && (
                                <p className="text-sm text-gray-400">Work queue is clear.</p>
                            )}
                        </div>
                    </FormCard>
                </div>
            </div>
        </Layout>
    );
}
