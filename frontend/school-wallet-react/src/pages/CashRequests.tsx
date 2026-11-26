import { useCallback, useEffect, useState } from 'react';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { FormCard, Notice, StatTile, money, text } from '../components/ModuleUi';
import { useAuth } from '../auth/AuthContext';
import { api, operationsApi } from '../lib/api';

type Row = Record<string, unknown>;

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const statusTone = (status: string): string => {
    if (status === 'PENDING') return 'badge-warning';
    if (status === 'APPROVED') return 'badge-info';
    if (status === 'DECLINED') return 'badge-danger';
    if (status === 'PAID') return 'badge-success';
    return 'badge-primary';
};

export default function CashRequests() {
    const { user, hasRole } = useAuth();
    const guardian = hasRole('PARENT') || hasRole('STUDENT');
    const teacher = hasRole('TEACHER') || hasRole('CLASS_TEACHER');
    const canApprove = !teacher || hasRole('HEAD_MASTER') || hasRole('TENANT_ADMIN')
        || hasRole('SUPER_ADMIN') || hasRole('ACCOUNTANT');

    const [rows, setRows] = useState<Row[]>([]);
    const [students, setStudents] = useState<Row[]>([]);
    const [teachers, setTeachers] = useState<Row[]>([]);
    const [studentId, setStudentId] = useState('');
    const [amount, setAmount] = useState('');
    const [reason, setReason] = useState('');
    const [agentCodes, setAgentCodes] = useState<Record<string, string>>({});
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = useCallback(() => {
        setError('');
        operationsApi.cashRequests<Row[]>()
            .then(setRows)
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        load();
        api.list<Row>('students', 0, 100, '')
            .then((page) => {
                setStudents(page.content);
                setStudentId((current) => current || page.content[0]?.id?.toString() || '');
            })
            .catch(() => undefined);
        if (!guardian) {
            api.list<Row>('teachers', 0, 100, '')
                .then((page) => setTeachers(page.content))
                .catch(() => undefined);
        }
    }, [load, guardian]);

    const run = async (action: () => Promise<unknown>, done: string) => {
        setError('');
        setSuccess('');
        try {
            await action();
            setSuccess(done);
            setReason('');
            setAmount('');
            load();
        } catch (reason) {
            setError(message(reason));
        }
    };

    const studentName = (id: string): string => {
        const row = students.find((candidate) => text(candidate.id) === id);
        if (!row) return id ? 'Student' : '—';
        return `${text(row.firstName)} ${text(row.lastName)}`.trim() || text(row.studentCode);
    };

    const teacherName = (id: string): string => {
        const row = teachers.find((candidate) => text(candidate.id) === id);
        if (!row) return id ? 'Teacher' : '—';
        return `${text(row.firstName)} ${text(row.lastName)}`.trim()
            || text(row.employeeCode)
            || 'Teacher';
    };

    const pending = rows.filter((row) => text(row.status) === 'PENDING').length;
    const approved = rows.filter((row) => text(row.status) === 'APPROVED').length;
    const paid = rows.filter((row) => text(row.status) === 'PAID').length;
    const total = rows.reduce((sum, row) => sum + (Number(row.amount) || 0), 0);

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Cash Requests"
                    subtitle="Teacher request · parent approval · agent payout"
                    icon="Money"
                    badge={<span className="badge badge-info">{text(user?.fullName) || 'Skulify'}</span>}
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Pending approval" value={pending} icon="Clock" tone="warning" footer="Waiting for parent" />
                    <StatTile label="Approved" value={approved} icon="CheckCircle" tone="info" footer="Ready for agent" />
                    <StatTile label="Cash paid" value={paid} icon="Money" tone="success" footer="Collected by teacher" />
                    <StatTile label="Total requested" value={money(total)} icon="Wallet" tone="primary" footer="All requests" />
                </div>

                {!guardian && (
                    <FormCard title="Request cash for a student" icon="Money">
                        <p className="pocket-copy mb-4">
                            Logged in as <strong>{text(user?.fullName) || 'Teacher'}</strong>. The guardian
                            receives the request and must approve it before the teacher phone receives
                            the payout reference.
                        </p>
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                            <label className="block">
                                <span className="form-label">Student</span>
                                <select
                                    className="form-input w-full"
                                    value={studentId}
                                    onChange={(event) => setStudentId(event.target.value)}
                                >
                                    <option value="">Choose student...</option>
                                    {students.map((row) => (
                                        <option key={text(row.id)} value={text(row.id)}>
                                            {studentName(text(row.id))}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="block">
                                <span className="form-label">Amount (TZS)</span>
                                <input
                                    className="form-input w-full"
                                    type="number"
                                    min="1"
                                    value={amount}
                                    onChange={(event) => setAmount(event.target.value)}
                                    placeholder="e.g. 15000"
                                />
                            </label>
                            <label className="block">
                                <span className="form-label">Reason</span>
                                <input
                                    className="form-input w-full"
                                    value={reason}
                                    onChange={(event) => setReason(event.target.value)}
                                    placeholder="e.g. Transport to school trip"
                                />
                            </label>
                        </div>
                        <button
                            type="button"
                            className="btn-primary mt-4"
                            disabled={!studentId || !amount || !reason}
                            onClick={() =>
                                void run(
                                    () =>
                                        operationsApi.requestCash({
                                            studentId,
                                            amount,
                                            reason,
                                        }),
                                    'Cash request sent to the guardian for approval.',
                                )
                            }
                        >
                            <Icon name="PaperPlaneTilt" size={18} className="mr-2" /> Request cash
                        </button>
                    </FormCard>
                )}

                {guardian && (
                    <FormCard title="Parent approval" icon="Shield">
                        <p className="pocket-copy">
                            Approve a pending request to debit the child's card and send the payout
                            reference to the teacher's phone. Declining leaves the balance unchanged.
                        </p>
                    </FormCard>
                )}

                <FormCard title="Cash request records" icon="ListChecks">
                    <div className="re-table-wrap">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    <th><span className="table-col"><TableIcon label="Student" /><span>Student</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Teacher" /><span>Teacher</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Amount" /><span>Amount</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Reason" /><span>Reason</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Payout reference" /><span>Payout reference</span></span></th>
                                    <th><span className="table-col"><TableIcon label="Action" /><span>Action</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((row) => {
                                    const id = text(row.id);
                                    const status = text(row.status);
                                    return (
                                        <tr key={id}>
                                            <td><div className="table-cell-content"><TableIcon label="Student" value={row.studentId} /><span className="min-w-0">{studentName(text(row.studentId))}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Teacher" value={row.teacherId} /><span className="min-w-0">{teacherName(text(row.teacherId))}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Amount" value={row.amount} /><span className="min-w-0 font-semibold">{money(row.amount)}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Reason" value={row.reason} /><span className="min-w-0 text-muted">{text(row.reason)}</span></div></td>
                                            <td><div className="table-cell-content"><TableIcon label="Status" value={status} /><span className="min-w-0"><span className={`badge ${statusTone(status)}`}>{status}</span></span></div></td>
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Payout reference" value={row.payoutReference} />
                                                    <span className="min-w-0 font-mono text-xs">
                                                        {text(row.payoutReference) || '—'}
                                                    </span>
                                                </div>
                                            </td>
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Action" value={status} />
                                                    <span className="min-w-0 flex flex-wrap items-center gap-2">
                                                        {status === 'PENDING' && canApprove && (
                                                            <>
                                                                <button
                                                                    type="button"
                                                                    className="btn-primary btn-xs"
                                                                    onClick={() =>
                                                                        void run(
                                                                            () =>
                                                                                operationsApi.decideCashRequest(
                                                                                    id,
                                                                                    'APPROVE',
                                                                                    'Approved by guardian',
                                                                                ),
                                                                            'Approved. The payout reference is on the teacher phone.',
                                                                        )
                                                                    }
                                                                >
                                                                    Approve
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    className="btn-outline btn-xs"
                                                                    onClick={() =>
                                                                        void run(
                                                                            () =>
                                                                                operationsApi.decideCashRequest(
                                                                                    id,
                                                                                    'DECLINE',
                                                                                    'Declined by guardian',
                                                                                ),
                                                                            'Cash request declined.',
                                                                        )
                                                                    }
                                                                >
                                                                    Decline
                                                                </button>
                                                            </>
                                                        )}
                                                        {status === 'APPROVED' && !guardian && (
                                                            <>
                                                                <input
                                                                    className="form-input cash-agent-input"
                                                                    value={agentCodes[id] ?? ''}
                                                                    onChange={(event) =>
                                                                        setAgentCodes((current) => ({
                                                                            ...current,
                                                                            [id]: event.target.value,
                                                                        }))
                                                                    }
                                                                    placeholder="Agent code"
                                                                    aria-label="Agent code"
                                                                />
                                                                <button
                                                                    type="button"
                                                                    className="btn-primary btn-xs"
                                                                    onClick={() =>
                                                                        void run(
                                                                            () =>
                                                                                operationsApi.payoutCashRequest(
                                                                                    id,
                                                                                    agentCodes[id] ?? '',
                                                                                    'Cash collected at agent',
                                                                                ),
                                                                            'Withdrawal confirmed. Cash is with the teacher.',
                                                                        )
                                                                    }
                                                                >
                                                                    Confirm cash
                                                                </button>
                                                            </>
                                                        )}
                                                        {(status === 'DECLINED' || status === 'PAID') && (
                                                            <span className="text-muted text-xs">
                                                                {text(row.note) || 'No further action'}
                                                            </span>
                                                        )}
                                                    </span>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                                {rows.length === 0 && (
                                    <TableEmptyState colSpan={7} icon="Money" message="No cash requests yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>
            </div>
        </Layout>
    );
}
