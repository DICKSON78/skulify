import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import TableIcon from '../components/TableIcon';
import TableEmptyState from '../components/TableEmptyState';
import Icon from '../components/Icon';
import Layout from '../components/Layout';
import { StatTile } from '../components/ModuleUi';
import { useAuth } from '../auth/AuthContext';
import { api } from '../lib/api';

type Summary = {
    students: number;
    activeStudents: number;
    classes: number;
    attendanceRate: number;
    billed: number;
    collected: number;
    outstanding: number;
    activeEnrolments: number;
    recentPayments: Array<{ reference: string; amount: number; status: string }>;
};

type RecentStudent = { name: string; class: string; status: string };

const money = (value: number) =>
    new Intl.NumberFormat('en-TZ', {
        style: 'currency',
        currency: 'TZS',
        maximumFractionDigits: 0,
    }).format(value || 0);

export default function Dashboard() {
    const { user, can } = useAuth();
    const [summary, setSummary] = useState<Summary | null>(null);
    const [recentStudents, setRecentStudents] = useState<RecentStudent[]>([]);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        Promise.all([api.summary<Summary>(), api.recentStudents<RecentStudent>()])
            .then(([overview, students]) => {
                if (active) {
                    setSummary(overview);
                    setRecentStudents(students);
                }
            })
            .catch((reason) => {
                if (active) {
                    setError(reason instanceof Error ? reason.message : 'Unable to load dashboard');
                }
            });
        return () => {
            active = false;
        };
    }, []);

    const quickActions = [
        can('/students') && { label: 'Students', desc: 'Manage student records', to: '/students', icon: 'GraduationCap', tone: 'primary' },
        can('/fees') && { label: 'Fees', desc: 'View fee payments', to: '/fees', icon: 'Wallet', tone: 'info' },
        can('/attendance') && { label: 'Attendance', desc: 'Track daily attendance', to: '/attendance', icon: 'CalendarCheck', tone: 'violet' },
        can('/reports') && { label: 'Reports', desc: 'Download analytics', to: '/reports', icon: 'ChartBar', tone: 'warning' },
    ].filter(Boolean) as Array<{ label: string; desc: string; to: string; icon: string; tone: string }>;

    const feePosition = [
        { label: 'Collected', value: summary?.collected ?? 0 },
        { label: 'Outstanding', value: summary?.outstanding ?? 0 },
    ];
    const attendanceRate = summary?.attendanceRate ?? 0;
    const collectionRate = summary?.billed
        ? Math.round((summary.collected / summary.billed) * 100)
        : 0;

    const feeTotal = feePosition.reduce((total, point) => total + Math.max(0, point.value), 0);
    const collectedPercent = feeTotal
        ? Math.round((feePosition[0].value / feeTotal) * 100)
        : 0;
    const donutRadius = 54;
    const donutCircumference = 2 * Math.PI * donutRadius;
    const collectedDash = (collectedPercent / 100) * donutCircumference;
    const gaugeLength = Math.PI * 110;

    return (
        <Layout>
            <div className="space-y-6">
                <div className="page-heading">
                    <span className="icon-tile">
                        <Icon name="SquaresFour" size={20} />
                    </span>
                    <div className="min-w-0">
                        <h1 className="page-title">Dashboard</h1>
                        <p className="page-subtitle">
                            Welcome back{user ? `, ${user.fullName.split(' ')[0]}` : ''}! Here's what's happening with your school today.
                        </p>
                    </div>
                </div>

                {error && <div className="notice error">{error}</div>}

                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 md:gap-5">
                    <StatTile
                        label="Total Students"
                        value={summary?.students ?? '—'}
                        icon="GraduationCap"
                        tone="primary"
                        footer={<>Active: <strong>{summary?.activeStudents ?? '—'}</strong> · vs last month</>}
                    />
                    <StatTile
                        label="Active Classes"
                        value={summary?.classes ?? '—'}
                        icon="Buildings"
                        tone="info"
                        footer={<>Across all academic programmes</>}
                    />
                    <StatTile
                        label="Outstanding Fees"
                        value={summary ? money(summary.outstanding) : '—'}
                        icon="Receipt"
                        tone="warning"
                        footer={<>Collection rate <strong>{collectionRate}%</strong></>}
                    />
                    <StatTile
                        label="Active Enrolments"
                        value={summary?.activeEnrolments ?? '—'}
                        icon="ChartLineUp"
                        tone="violet"
                        footer={<>Current academic term</>}
                    />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 md:gap-5">
                    <StatTile
                        label="Attendance Rate"
                        value={`${attendanceRate}%`}
                        icon="CalendarCheck"
                        tone="success"
                        footer={<>Present across recorded sessions</>}
                    />
                    <StatTile
                        label="Active Students"
                        value={summary?.activeStudents ?? '—'}
                        icon="UserPlus"
                        tone="primary"
                        footer={<>Of <strong>{summary?.students ?? '—'}</strong> total students</>}
                    />
                    <StatTile
                        label="Total Collected"
                        value={summary ? money(summary.collected) : '—'}
                        icon="Coins"
                        tone="success"
                        footer={<>Against <strong>{summary ? money(summary.outstanding) : '—'}</strong> outstanding</>}
                    />
                    <StatTile
                        label="Total Billed"
                        value={summary ? money(summary.billed) : '—'}
                        icon="Invoice"
                        tone="info"
                        footer={<>Invoiced for this term</>}
                    />
                </div>

                {quickActions.length > 0 && (
                    <section className="card">
                        <div className="card-header">
                            <h3 className="card-title">
                                <span className="icon-tile amber">
                                    <Icon name="Lightning" size={18} />
                                </span>
                                Quick Actions
                            </h3>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
                            {quickActions.map((action) => (
                                <Link
                                    key={action.to}
                                    to={action.to}
                                    className={`quick-action-card tone-${action.tone}`}
                                >
                                    <div className="flex items-center gap-3">
                                        <span className="quick-action-badge">
                                            <Icon name={action.icon} size={22} />
                                        </span>
                                        <span className="min-w-0">
                                            <span className="quick-action-title">{action.label}</span>
                                            <span className="quick-action-desc">{action.desc}</span>
                                        </span>
                                    </div>
                                </Link>
                            ))}
                        </div>
                    </section>
                )}

                <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
                    <section className="card">
                        <div className="card-header">
                            <h3 className="card-title">
                                <span className="icon-tile">
                                    <Icon name="ChartDonut" size={18} />
                                </span>
                                Fee position
                            </h3>
                        </div>
                        <div className="donut-wrap">
                            <svg
                                viewBox="0 0 160 160"
                                className="donut-svg"
                                role="img"
                                aria-label={`Collected ${money(feePosition[0].value)}, outstanding ${money(feePosition[1].value)}`}
                            >
                                <circle
                                    className={`donut-track ${feeTotal ? '' : 'empty'}`}
                                    cx="80"
                                    cy="80"
                                    r={donutRadius}
                                    transform="rotate(-90 80 80)"
                                />
                                <circle
                                    className="donut-value"
                                    cx="80"
                                    cy="80"
                                    r={donutRadius}
                                    strokeDasharray={`${collectedDash} ${donutCircumference - collectedDash}`}
                                    transform="rotate(-90 80 80)"
                                />
                                <text x="80" y="76" className="donut-label">{collectedPercent}%</text>
                                <text x="80" y="96" className="donut-caption">Collected</text>
                            </svg>
                            <div className="donut-legend">
                                {feePosition.map((point, index) => (
                                    <div key={point.label} className="donut-legend-item">
                                        <span className={`donut-dot ${index === 1 ? 'outstanding' : ''}`} />
                                        <span className="donut-legend-label">{point.label}</span>
                                        <strong>{money(point.value)}</strong>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </section>

                    <section className="card">
                        <div className="card-header">
                            <h3 className="card-title">
                                <span className="icon-tile">
                                    <Icon name="ChartLineUp" size={18} />
                                </span>
                                Attendance overview
                            </h3>
                        </div>
                        <div className="gauge-wrap">
                            <svg viewBox="0 0 280 180" className="w-full max-w-[320px]" role="img" aria-label={`${attendanceRate}% attendance`}>
                                <path className="gauge-track" d="M 30 150 A 110 110 0 0 1 250 150" />
                                <path
                                    className="gauge-value"
                                    d="M 30 150 A 110 110 0 0 1 250 150"
                                    strokeDasharray={`${(attendanceRate / 100) * gaugeLength} ${gaugeLength}`}
                                />
                                <text x="140" y="132" textAnchor="middle" className="gauge-label">{attendanceRate}%</text>
                                <text x="140" y="154" textAnchor="middle" className="gauge-caption">Attendance rate</text>
                            </svg>
                        </div>
                    </section>
                </div>

                <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
                    {can('/students') && (
                        <div className="re-table-wrap">
                            <div className="card-header px-5 pt-5">
                                <h3 className="card-title">Recent Students</h3>
                                <Link to="/students" className="text-xs font-semibold text-primary-dark">View all</Link>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="re-table">
                                    <thead><tr><th className="text-left"><span className="table-col"><TableIcon label="Name" /><span>Name</span></span></th><th className="text-left"><span className="table-col"><TableIcon label="Class" /><span>Class</span></span></th><th className="text-left"><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th></tr></thead>
                                    <tbody>
                                        {recentStudents.map((student) => (
                                            <tr key={student.name}>
                                                <td className="font-semibold"><div className="table-cell-content"><TableIcon label="Name" value={student.name} /><span className="min-w-0">{student.name}</span></div></td>
                                                <td><div className="table-cell-content"><TableIcon label="Class" value={student.class} /><span className="min-w-0">{student.class}</span></div></td>
                                                <td><div className="table-cell-content"><TableIcon label="Status" value={student.status} /><span className="min-w-0"><span className={`badge ${student.status === 'ACTIVE' ? 'badge-success' : 'badge-warning'}`}>{student.status}</span></span></div></td>
                                            </tr>
                                        ))}
                                        {recentStudents.length === 0 && (
                                            <TableEmptyState colSpan={3} message="No students yet" />
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    {can('/fees') && (
                        <div className="re-table-wrap">
                            <div className="card-header px-5 pt-5">
                                <h3 className="card-title">Recent Fee Payments</h3>
                                <Link to="/fees" className="text-xs font-semibold text-primary-dark">View all</Link>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="re-table">
                                    <thead><tr><th className="text-left"><span className="table-col"><TableIcon label="Ref" /><span>Ref</span></span></th><th className="text-right"><span className="table-col"><TableIcon label="Amount" /><span>Amount</span></span></th><th className="text-left"><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th></tr></thead>
                                    <tbody>
                                        {(summary?.recentPayments ?? []).map((payment) => (
                                            <tr key={payment.reference}>
                                                <td className="font-semibold"><div className="table-cell-content"><TableIcon label="Ref" value={payment.reference} /><span className="min-w-0">{payment.reference}</span></div></td>
                                                <td className="text-right font-medium tabular-nums"><div className="table-cell-content"><TableIcon label="Amount" value={payment.amount} /><span className="min-w-0">{money(payment.amount)}</span></div></td>
                                                <td><div className="table-cell-content"><TableIcon label="Status" value={payment.status} /><span className="min-w-0"><span className="badge badge-success">{payment.status}</span></span></div></td>
                                            </tr>
                                        ))}
                                        {(summary?.recentPayments ?? []).length === 0 && (
                                            <TableEmptyState colSpan={3} message="No payments recorded yet" />
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </Layout>
    );
}
