import TableIcon from '../components/TableIcon';
import TableEmptyState from '../components/TableEmptyState';
import Icon from '../components/Icon';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, money, text } from '../components/ModuleUi';
import { parentPortalApi } from '../lib/api';

type Row = Record<string, unknown>;
type Overview = {
    student?: Row;
    gradesByTerm?: Row[];
    grades?: Row[];
    attendance?: Row;
    attendanceRecords?: Row[];
    semesterAttendance?: Row;
    reportCards?: Row[];
    letters?: Row[];
    fees?: Row;
    feeRecords?: Row[];
    enrolments?: Row[];
    timetable?: Row[];
};

type TopTab = 'overview' | 'academics' | 'attendance' | 'fees';
type AcademicTab = 'results' | 'cards' | 'letters';
type AttendanceTab = 'weeks' | 'daily' | 'timetable';

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const rows = (value: unknown): Row[] => (Array.isArray(value) ? (value as Row[]) : []);

const asRow = (value: unknown): Row =>
    value && typeof value === 'object' && !Array.isArray(value) ? (value as Row) : {};

const num = (value: unknown): number => Number(value) || 0;

const label = (value: unknown): string => text(value) || '—';

const clamp = (value: number): number =>
    Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;

const pct = (value: unknown): number => clamp(num(value));

const initials = (name: string): string => {
    const parts = name
        .split(/\s+/)
        .map((part) => part.trim())
        .filter((part) => part && part !== '—');
    return (
        parts
            .slice(0, 2)
            .map((part) => part.charAt(0).toUpperCase())
            .join('') || 'SK'
    );
};

const guardianLocation = (student: Row): string =>
    ['guardianRegion', 'guardianDistrict', 'guardianStreet']
        .map((key) => text(student[key]))
        .filter(Boolean)
        .join(' · ');

const toneForValue = (value: unknown): string => {
    const normalised = text(value).toUpperCase();
    if (['PRESENT', 'PAID', 'COMPLETED', 'APPROVED', 'PUBLISHED', 'ACTIVE'].includes(normalised)) {
        return 'success';
    }
    if (['LATE', 'PARTIAL', 'PENDING', 'DRAFT', 'EXCUSED'].includes(normalised)) {
        return 'warning';
    }
    if (['ABSENT', 'UNPAID', 'OVERDUE', 'REJECTED', 'SUSPENDED'].includes(normalised)) {
        return 'danger';
    }
    return 'info';
};

const iconForValue = (tone: string): string => {
    if (tone === 'success') return 'CheckCircle';
    if (tone === 'warning') return 'WarningCircle';
    if (tone === 'danger') return 'XCircle';
    return 'Info';
};

function Progress({
    value,
    label: progressLabel,
}: {
    value: number;
    label: string;
}) {
    return (
        <div
            className="parent-progress"
            role="progressbar"
            aria-label={progressLabel}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(clamp(value))}
        >
            <span className="parent-progress-fill" style={{ width: `${clamp(value)}%` }} />
        </div>
    );
}

function MetricCard({
    label: metricLabel,
    value,
    icon,
    progress,
    tone = 'primary',
    footer,
}: {
    label: string;
    value: ReactNode;
    icon: string;
    progress: number;
    tone?: string;
    footer: string;
}) {
    return (
        <div className={`stat-card parent-metric tone-${tone}`}>
            <div className="min-w-0 flex-1">
                <p className="stat-label truncate">{metricLabel}</p>
                <p className="stat-value">{value}</p>
                <div className="stat-footer">
                    <Progress value={progress} label={`${metricLabel} progress`} />
                    <span className="parent-metric-footer">{footer}</span>
                </div>
            </div>
            <div className="stat-icon">
                <Icon name={icon} size={22} />
            </div>
        </div>
    );
}

function SnapshotDonut({
    value,
    label: donutLabel,
    caption,
    icon,
    tone = 'primary',
}: {
    value: number;
    label: string;
    caption: string;
    icon: string;
    tone?: string;
}) {
    const radius = 46;
    const circumference = 2 * Math.PI * radius;
    const dash = (clamp(value) / 100) * circumference;

    return (
        <div className={`parent-donut tone-${tone}`}>
            <div className="parent-donut-head">
                <span className="quick-action-badge">
                    <Icon name={icon} size={16} />
                </span>
                <span>{donutLabel}</span>
            </div>
            <svg
                viewBox="0 0 120 120"
                role="img"
                aria-label={`${donutLabel}: ${Math.round(clamp(value))}%`}
            >
                <circle
                    className="parent-donut-track"
                    cx="60"
                    cy="60"
                    r={radius}
                    transform="rotate(-90 60 60)"
                />
                <circle
                    className="parent-donut-value"
                    cx="60"
                    cy="60"
                    r={radius}
                    strokeDasharray={`${dash} ${circumference - dash}`}
                    transform="rotate(-90 60 60)"
                />
                <text x="60" y="60" className="parent-donut-label" dominantBaseline="middle">
                    {Math.round(clamp(value))}%
                </text>
            </svg>
            <p className="parent-donut-caption-text">{caption}</p>
        </div>
    );
}

function StatusBadge({
    label: badgeLabel,
    tone = 'info',
}: {
    label: string;
    tone?: string;
}) {
    return (
        <span className={`parent-status-badge tone-${tone}`}>
            <span className="quick-action-badge">
                <Icon name={iconForValue(tone)} size={14} />
            </span>
            {badgeLabel}
        </span>
    );
}

function CellText({ value, children }: { value: unknown; children?: ReactNode }) {
    const tooltip = text(value) || undefined;
    return (
        <span className="min-w-0" title={tooltip}>
            {children ?? label(value)}
        </span>
    );
}


function ParentTableCard({
    title,
    count,
    icon,
    children,
}: {
    title: string;
    count: number;
    icon: string;
    children: ReactNode;
}) {
    return (
        <section className="re-table-wrap">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-6 py-4">
                <div>
                    <h2 className="font-semibold text-gray-900">{title}</h2>
                    <p className="text-xs text-gray-500">
                        {count} {count === 1 ? 'record' : 'records'} in this section
                    </p>
                </div>
                <span className="icon-tile">
                    <Icon name={icon} size={18} />
                </span>
            </div>
            <div className="overflow-x-auto">{children}</div>
        </section>
    );
}

function SubTabs<T extends string>({
    items,
    active,
    onChange,
    label,
}: {
    items: Array<{ key: T; label: string; icon: string }>;
    active: T;
    onChange: (key: T) => void;
    label: string;
}) {
    return (
        <div className="tab-nav parent-subtabs" role="tablist" aria-label={label}>
            {items.map((item) => (
                <button
                    key={item.key}
                    type="button"
                    role="tab"
                    aria-selected={active === item.key}
                    onClick={() => onChange(item.key)}
                    className={`tab-btn ${active === item.key ? 'tab-btn-active' : 'tab-btn-inactive'}`}
                >
                    <Icon name={item.icon} size={16} />
                    <span>{item.label}</span>
                </button>
            ))}
        </div>
    );
}

export default function ParentPortal() {
    const [children, setChildren] = useState<Row[]>([]);
    const [studentId, setStudentId] = useState('');
    const [overview, setOverview] = useState<Overview | null>(null);
    const [tab, setTab] = useState<TopTab>('overview');
    const [academicTab, setAcademicTab] = useState<AcademicTab>('results');
    const [attendanceTab, setAttendanceTab] = useState<AttendanceTab>('weeks');
    const [error, setError] = useState('');

    useEffect(() => {
        parentPortalApi.children<Row[]>()
            .then((list) => {
                setChildren(list);
                setStudentId((current) => current || text(list[0]?.id));
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    const load = useCallback((id: string) => {
        if (!id) {
            return;
        }
        parentPortalApi
            .overview<Overview>(id)
            .then(setOverview)
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        load(studentId);
    }, [load, studentId]);

    const student = overview?.student ?? {};
    const studentName = label(student.name);
    const attendance = overview?.attendance ?? {};
    const fees = overview?.fees ?? {};
    const cards = rows(overview?.reportCards);
    const bestCard = cards.reduce<Row | null>(
        (best, card) => (best && num(best.average) >= num(card.average) ? best : card),
        null,
    );

    const average = pct(bestCard?.average ?? (overview?.gradesByTerm?.[0]?.average ?? 0));
    const attendanceRate = pct(attendance.rate);
    const semester = asRow(overview?.semesterAttendance);
    const semesterChild = asRow(semester.student);
    const semesterProgress = pct(semester.semesterProgress);
    const completedWeeks = num(semester.completedWeeks);
    const semesterWeeks = num(semester.numberOfWeeks);
    const semesterWeek = num(semester.week);
    const semesterWeekRows = rows(semester.weeks);
    const semesterSelectedWeek =
        semesterWeekRows.find((weekRow) => num(weekRow.number) === semesterWeek) ?? {};
    const semesterChildStatus = text(semesterSelectedWeek.status);
    const weekProgress = pct(semester.weekProgress);
    const markedThisWeek = num(semester.markedThisWeek);
    const semesterClassSize = num(semester.totalStudents);
    const feeBilled = num(fees.billed);
    const feePaid = num(fees.paid);
    const feeClearance = feeBilled ? pct((feePaid / feeBilled) * 100) : 0;
    const position = num(bestCard?.position);
    const classSize = num(bestCard?.classSize);
    const positionProgress =
        position && classSize ? clamp(((classSize - position + 1) / classSize) * 100) : 0;
    const shareOfTotal = (value: unknown): number =>
        num(attendance.total) ? pct((num(value) / num(attendance.total)) * 100) : 0;

    const tabs: Array<{
        key: TopTab;
        label: string;
        desc: string;
        icon: string;
        tone: string;
    }> = [
        {
            key: 'overview',
            label: 'Overview',
            desc: 'Child summary',
            icon: 'SquaresFour',
            tone: 'primary',
        },
        {
            key: 'academics',
            label: 'Academics',
            desc: 'Results, cards & letters',
            icon: 'Books',
            tone: 'violet',
        },
        {
            key: 'attendance',
            label: 'Attendance',
            desc: 'Semester weeks & check-ins',
            icon: 'CalendarCheck',
            tone: 'success',
        },
        { key: 'fees', label: 'Fees', desc: 'Payments & balance', icon: 'Wallet', tone: 'warning' },
    ];

    const academicTabs: Array<{ key: AcademicTab; label: string; icon: string }> = [
        { key: 'results', label: 'Results', icon: 'Exam' },
        { key: 'cards', label: 'Report cards', icon: 'Certificate' },
        { key: 'letters', label: 'Letters', icon: 'EnvelopeOpen' },
    ];

    const attendanceTabs: Array<{ key: AttendanceTab; label: string; icon: string }> = [
        { key: 'weeks', label: 'Semester weeks', icon: 'CalendarCheck' },
        { key: 'daily', label: 'Daily check-ins', icon: 'ListChecks' },
        { key: 'timetable', label: 'Timetable', icon: 'Table' },
    ];

    return (
        <Layout>
            <div className="space-y-5">
                <PageHeader
                    title="Parent Portal"
                    subtitle="Everything linked to your child, generated and kept in one place"
                    icon="UsersThree"
                />

                {error && <div className="notice error">{error}</div>}

                {children.length === 0 && !error ? (
                    <FormCard title="No linked child" icon="Info">
                        <p className="text-sm text-gray-600">
                            No student is linked to this account. Ask the registrar to attach your
                            child's admission record to your parent login.
                        </p>
                    </FormCard>
                ) : (
                    <>
                        {children.length > 0 && (
                            <div className="parent-child-switcher">
                                <span className="parent-child-switcher-label">
                                    <Icon name="UsersThree" size={16} />
                                    Viewing child
                                </span>
                                <select
                                    className="form-input"
                                    aria-label="Viewing child"
                                    value={studentId}
                                    onChange={(event) => setStudentId(event.target.value)}
                                >
                                    {children.map((child) => (
                                        <option key={text(child.id)} value={text(child.id)}>
                                            {text(child.name)} · {text(child.studentCode)}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        )}

                        <div className="parent-quick-grid" aria-label="Portal sections">
                            {tabs.map((item) => (
                                <button
                                    key={item.key}
                                    type="button"
                                    aria-pressed={tab === item.key}
                                    onClick={() => setTab(item.key)}
                                    className={`parent-quick tone-${item.tone}${tab === item.key ? ' active' : ''}`}
                                >
                                    <span className="quick-action-badge">
                                        <Icon name={item.icon} size={20} />
                                    </span>
                                    <span className="min-w-0">
                                        <span className="quick-action-title">{item.label}</span>
                                        <span className="quick-action-desc">{item.desc}</span>
                                    </span>
                                    <Icon name="CaretRight" size={14} className="parent-quick-arrow" />
                                </button>
                            ))}
                        </div>

                        {tab === 'overview' && (
                            <>
                                <section className="card parent-hero">
                                    <div className="parent-hero-main">
                                        <span className="parent-avatar">
                                            <Icon name="GraduationCap" size={28} />
                                        </span>
                                        <div className="min-w-0">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="parent-eyebrow">Linked student</p>
                                                <span className="parent-badge">Live record</span>
                                            </div>
                                            <h2 className="parent-student-name">{studentName}</h2>
                                            <p className="parent-meta">
                                                {label(student.studentCode)} · {label(student.className)}
                                            </p>
                                            <p className="parent-meta parent-meta-muted">
                                                {text(student.admissionDate)
                                                    ? `Admitted ${label(student.admissionDate)} · ${initials(studentName)} learner profile`
                                                    : `${initials(studentName)} learner profile`}
                                            </p>
                                        </div>
                                    </div>

                                    <div className="parent-hero-side">
                                        <div className="parent-guardian">
                                            <span className="quick-action-badge">
                                                <Icon name="UsersThree" size={18} />
                                            </span>
                                            <div className="min-w-0">
                                                <p className="parent-eyebrow">Parent / Guardian</p>
                                                <p className="parent-guardian-name">
                                                    {text(student.guardianName) || 'Guardian record'}
                                                </p>
                                                {text(student.guardianPhone) && (
                                                    <p className="parent-meta">{text(student.guardianPhone)}</p>
                                                )}
                                                {guardianLocation(student) && (
                                                    <p className="parent-meta parent-meta-muted">
                                                        {guardianLocation(student)}
                                                    </p>
                                                )}
                                            </div>
                                        </div>
                                        <span className="parent-badge">
                                            {children.length} linked {children.length === 1 ? 'child' : 'children'}
                                        </span>
                                    </div>
                                </section>

                                <div className="parent-metric-grid">
                                    <MetricCard
                                        label="Average score"
                                        value={`${Math.round(average)}%`}
                                        icon="ChartLineUp"
                                        progress={average}
                                        tone="primary"
                                        footer="Best published term result"
                                    />
                                    <MetricCard
                                        label="Semester attendance"
                                        value={`${Math.round(semesterProgress)}%`}
                                        icon="CalendarCheck"
                                        progress={semesterProgress}
                                        tone="success"
                                        footer="Completed weeks ÷ semester weeks"
                                    />
                                    <MetricCard
                                        label="Fee clearance"
                                        value={`${Math.round(feeClearance)}%`}
                                        icon="Wallet"
                                        progress={feeClearance}
                                        tone="info"
                                        footer={`Balance ${money(fees.outstanding)}`}
                                    />
                                    <MetricCard
                                        label="Class position"
                                        value={position ? `${position} / ${classSize || '—'}` : '—'}
                                        icon="Medal"
                                        progress={positionProgress}
                                        tone="violet"
                                        footer="Term ranking snapshot"
                                    />
                                </div>

                                <section className="card parent-charts">
                                    <div className="card-header">
                                        <h3 className="card-title">
                                            <span className="icon-tile">
                                                <Icon name="ChartDonut" size={18} />
                                            </span>
                                            Progress snapshot
                                        </h3>
                                        <span className="parent-badge">This term</span>
                                    </div>
                                    <div className="parent-donut-grid">
                                        <SnapshotDonut
                                            value={average}
                                            label="Average"
                                            caption="Academic performance"
                                            icon="Books"
                                            tone="primary"
                                        />
                                        <SnapshotDonut
                                            value={semesterProgress}
                                            label="Attendance"
                                            caption="Completed weeks ÷ semester weeks"
                                            icon="CalendarCheck"
                                            tone="success"
                                        />
                                        <SnapshotDonut
                                            value={feeClearance}
                                            label="Fees"
                                            caption="Amount settled"
                                            icon="Wallet"
                                            tone="info"
                                        />
                                    </div>
                                </section>
                            </>
                        )}

                        {tab === 'academics' && (
                            <>
                                <SubTabs
                                    items={academicTabs}
                                    active={academicTab}
                                    onChange={setAcademicTab}
                                    label="Academics sections"
                                />
                                {academicTab === 'results' && (
                                    <div className="space-y-4">
                                        {(overview?.gradesByTerm ?? []).map((term, index) => (
                                            <ParentTableCard
                                                key={`${text(term.term)}-${index}`}
                                                title={`${label(term.term)} · ${num(term.average)}% average`}
                                                count={rows(term.subjects).length}
                                                icon="Books"
                                            >
                                                <table className="re-table parent-table">
                                                    <thead>
                                                        <tr>
                                                            <th><span className="table-col"><TableIcon label="Subject" /><span>Subject</span></span></th>
                                                            <th><span className="table-col"><TableIcon label="Assessment" /><span>Assessment</span></span></th>
                                                            <th><span className="table-col"><TableIcon label="Score" /><span>Score</span></span></th>
                                                            <th><span className="table-col"><TableIcon label="Grade" /><span>Grade</span></span></th>
                                                            <th><span className="table-col"><TableIcon label="Remarks" /><span>Remarks</span></span></th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        {rows(term.subjects).map((result, rowIndex) => {
                                                            const maxScore = num(result.maxScore);
                                                            const scorePercent = maxScore
                                                                ? pct((num(result.score) / maxScore) * 100)
                                                                : 0;
                                                            return (
                                                                <tr key={`${text(result.subject)}-${rowIndex}`}>
                                                                    <td className="font-semibold"><div className="table-cell-content"><TableIcon label="Subject" value={result.subject} /><CellText value={result.subject} /></div></td>
                                                                    <td><div className="table-cell-content"><TableIcon label="Assessment" value={result.examType} /><CellText value={result.examType} /></div></td>
                                                                    <td>
                                                                        <div className="table-cell-content">
                                                                            <TableIcon label="Score" value={result.score} />
                                                                            <div className="parent-score">
                                                                                <div className="parent-score-line">
                                                                                    <span>{num(result.score)} / {maxScore}</span>
                                                                                    <strong>{Math.round(scorePercent)}%</strong>
                                                                                </div>
                                                                                <Progress
                                                                                    value={scorePercent}
                                                                                    label={`${label(result.subject)} score`}
                                                                                />
                                                                            </div>
                                                                        </div>
                                                                    </td>
                                                                    <td><div className="table-cell-content"><TableIcon label="Grade" value={result.grade} /><CellText value={result.grade}><StatusBadge label={label(result.grade)} tone="primary" /></CellText></div></td>
                                                                    <td><div className="table-cell-content"><TableIcon label="Remarks" value={result.remarks} /><CellText value={result.remarks} /></div></td>
                                                                </tr>
                                                            );
                                                        })}
                                                    </tbody>
                                                </table>
                                            </ParentTableCard>
                                        ))}
                                        {(overview?.gradesByTerm ?? []).length === 0 && (
                                            <FormCard title="No subject results yet" icon="Info">
                                                <p className="text-sm text-gray-600">
                                                    Results appear here as soon as your child's teacher uploads marks.
                                                </p>
                                            </FormCard>
                                        )}
                                    </div>
                                )}

                                {academicTab === 'cards' && (
                                    <ParentTableCard
                                        title="Report cards & results"
                                        count={cards.length}
                                        icon="Certificate"
                                    >
                                        <table className="re-table parent-table">
                                            <thead>
                                                <tr>
                                                    <th><span className="table-col"><TableIcon label="Reference" /><span>Reference</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Term" /><span>Term</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Class" /><span>Class</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Average" /><span>Average</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Position" /><span>Position</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {cards.map((card) => (
                                                    <tr key={text(card.id)}>
                                                        <td className="font-mono text-xs"><div className="table-cell-content"><TableIcon label="Reference" value={card.reference} /><CellText value={card.reference} /></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Term" value={card.term} /><CellText value={card.term} /></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Class" value={card.className} /><CellText value={card.className} /></div></td>
                                                        <td className="font-semibold"><div className="table-cell-content"><TableIcon label="Average" value={card.average} /><CellText value={`${num(card.average)}% · ${label(card.grade)}`} /></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Position" value={card.position} /><CellText value={`${num(card.position)} / ${num(card.classSize)}`} /></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Status" value={card.status} /><CellText value={card.status}><StatusBadge label={label(card.status)} tone={toneForValue(card.status)} /></CellText></div></td>
                                                    </tr>
                                                ))}
                                                {cards.length === 0 && (
                                                    <TableEmptyState colSpan={6} message="No report cards published yet." />
                                                )}
                                            </tbody>
                                        </table>
                                    </ParentTableCard>
                                )}

                                {academicTab === 'letters' && (
                                    <div className="space-y-4">
                                        {rows(overview?.letters).map((letter) => (
                                            <FormCard
                                                key={text(letter.id)}
                                                title={`${text(letter.title)} · ${text(letter.reference)}`}
                                                icon="EnvelopeOpen"
                                            >
                                                <p className="text-xs text-gray-500 mb-3">
                                                    {label(letter.term)} · generated {label(letter.generatedAt)}
                                                </p>
                                                <pre className="whitespace-pre-wrap rounded-xl bg-gray-50 p-4 text-sm text-gray-700 font-sans">
                                                    {text(letter.content)}
                                                </pre>
                                                <div className="mt-3 rounded-xl border border-primary/20 bg-primary-light/50 p-4">
                                                    <p className="text-xs font-bold uppercase tracking-wider text-primary mb-2">
                                                        Next-semester requirements
                                                    </p>
                                                    <pre className="whitespace-pre-wrap text-sm text-gray-700 font-sans">
                                                        {text(letter.requirements)}
                                                    </pre>
                                                </div>
                                            </FormCard>
                                        ))}
                                        {rows(overview?.letters).length === 0 && (
                                            <FormCard title="No parent letters yet" icon="Info">
                                                <p className="text-sm text-gray-600">
                                                    Letters are generated automatically after results are published.
                                                </p>
                                            </FormCard>
                                        )}
                                    </div>
                                )}
                            </>
                        )}

                        {tab === 'attendance' && (
                            <>
                                <SubTabs
                                    items={attendanceTabs}
                                    active={attendanceTab}
                                    onChange={setAttendanceTab}
                                    label="Attendance sections"
                                />
                                {attendanceTab === 'weeks' && (
                                    <div className="space-y-4">
                                        <div className="parent-metric-grid parent-metric-grid--compact">
                                            <MetricCard label="Weeks complete" value={`${completedWeeks} / ${semesterWeeks}`} icon="CalendarCheck" progress={semesterProgress} tone="success" footer={`${label(semester.termName)} · week ${semesterWeek}`} />
                                            <MetricCard label="Semester attendance" value={`${Math.round(semesterProgress)}%`} icon="ChartLineUp" progress={semesterProgress} tone="primary" footer="Completed weeks ÷ semester weeks" />
                                            <MetricCard label="Marked in week" value={markedThisWeek} icon="ListChecks" progress={weekProgress} tone="warning" footer={`${label(semester.className)} · ${semesterClassSize} student(s)`} />
                                            <MetricCard label="Child status" value={semesterChildStatus ? label(semesterChildStatus) : '—'} icon="UserCheck" progress={semesterChildStatus ? 100 : 0} tone={semesterChildStatus === 'PRESENT' ? 'success' : 'info'} footer={`Week ${semesterWeek || '—'} register`} />
                                        </div>
                                        <FormCard title="Progress" icon="ChartLineUp">
                                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                                                <div>
                                                    <div className="flex items-center justify-between gap-3 mb-2">
                                                        <span className="text-sm font-semibold text-gray-800">
                                                            Semester progress
                                                        </span>
                                                        <span className="text-sm font-semibold text-primary">
                                                            {Math.round(semesterProgress)}%
                                                        </span>
                                                    </div>
                                                    <Progress value={semesterProgress} label="Semester attendance progress" />
                                                    <p className="text-xs text-muted mt-2">
                                                        {completedWeeks} of {semesterWeeks} weeks complete ·{' '}
                                                        {label(semester.weekStart)} to {label(semester.weekEnd)}
                                                    </p>
                                                    <p className="text-xs text-muted mt-1">
                                                        {num(semesterChild.marked)} of {semesterWeeks} weeks recorded for {studentName} ·{' '}
                                                        {num(semesterChild.present)} present
                                                    </p>
                                                </div>
                                                <div>
                                                    <div className="flex items-center justify-between gap-3 mb-2">
                                                        <span className="text-sm font-semibold text-gray-800">
                                                            Latest register
                                                        </span>
                                                        <span className="text-sm font-semibold text-primary">
                                                            {Math.round(weekProgress)}%
                                                        </span>
                                                    </div>
                                                    <Progress value={weekProgress} label="Latest register attendance progress" />
                                                    <p className="text-xs text-muted mt-2">
                                                        {markedThisWeek} of {semesterClassSize} student(s) marked in week {semesterWeek}
                                                    </p>
                                                </div>
                                            </div>
                                        </FormCard>
                                        <ParentTableCard
                                            title="Semester weeks"
                                            count={semesterWeekRows.length}
                                            icon="CalendarCheck"
                                        >
                                            <table className="re-table parent-table">
                                                <thead>
                                                    <tr>
                                                        <th><span className="table-col"><TableIcon label="Week" /><span>Week</span></span></th>
                                                        <th><span className="table-col"><TableIcon label="Dates" /><span>Dates</span></span></th>
                                                        <th><span className="table-col"><TableIcon label="Class marked" /><span>Class marked</span></span></th>
                                                        <th><span className="table-col"><TableIcon label="Child status" /><span>Child status</span></span></th>
                                                        <th><span className="table-col"><TableIcon label="Register" /><span>Register</span></span></th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {semesterWeekRows.map((weekRow, index) => (
                                                        <tr key={`${text(weekRow.number)}-${index}`}>
                                                            <td className="font-semibold"><div className="table-cell-content"><TableIcon label="Week" value={weekRow.number} /><CellText value={`Week ${label(weekRow.number)}`} /></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Dates" value={weekRow.startDate} /><CellText value={`${label(weekRow.startDate)} – ${label(weekRow.endDate)}`} /></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Class marked" value={weekRow.marked} /><CellText value={`${num(weekRow.marked)} / ${num(weekRow.total)}`} /></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Child status" value={weekRow.status} /><CellText value={weekRow.status}><StatusBadge label={label(weekRow.status)} tone={toneForValue(weekRow.status)} /></CellText></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Register" value={weekRow.complete} /><CellText value={weekRow.complete}><StatusBadge label={weekRow.complete ? 'Complete' : 'In progress'} tone={weekRow.complete ? 'success' : 'warning'} /></CellText></div></td>
                                                        </tr>
                                                    ))}
                                                    {semesterWeekRows.length === 0 && (
                                                        <TableEmptyState colSpan={5} message="No semester weeks configured yet." />
                                                    )}
                                                </tbody>
                                            </table>
                                        </ParentTableCard>
                                    </div>
                                )}

                                {attendanceTab === 'daily' && (
                                    <div className="space-y-4">
                                        <div className="parent-metric-grid parent-metric-grid--compact">
                                            <MetricCard label="Days" value={num(attendance.total)} icon="CalendarBlank" progress={100} tone="primary" footer="Recorded sessions" />
                                            <MetricCard label="Present" value={num(attendance.present)} icon="CheckCircle" progress={attendanceRate} tone="success" footer="Attended sessions" />
                                            <MetricCard label="Absent" value={num(attendance.absent)} icon="XCircle" progress={shareOfTotal(attendance.absent)} tone="danger" footer="Missed sessions" />
                                            <MetricCard label="Late" value={num(attendance.late)} icon="Clock" progress={shareOfTotal(attendance.late)} tone="warning" footer="Arrived after time" />
                                            <MetricCard label="Excused" value={num(attendance.excused)} icon="CalendarPlus" progress={shareOfTotal(attendance.excused)} tone="info" footer="Approved absence" />
                                        </div>
                                        <ParentTableCard
                                            title="Attendance record"
                                            count={rows(overview?.attendanceRecords).length}
                                            icon="CalendarCheck"
                                        >
                                            <table className="re-table parent-table">
                                                <thead>
                                                    <tr>
                                                        <th><span className="table-col"><TableIcon label="Date" /><span>Date</span></span></th>
                                                        <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                                        <th><span className="table-col"><TableIcon label="Check-in" /><span>Check-in</span></span></th>
                                                        <th><span className="table-col"><TableIcon label="Remarks" /><span>Remarks</span></span></th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {rows(overview?.attendanceRecords).map((record, index) => (
                                                        <tr key={`${text(record.date)}-${index}`}>
                                                            <td><div className="table-cell-content"><TableIcon label="Date" value={record.date} /><CellText value={record.date} /></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Status" value={record.status} /><CellText value={record.status}><StatusBadge label={label(record.status)} tone={toneForValue(record.status)} /></CellText></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Check-in" value={record.checkIn} /><CellText value={record.checkIn} /></div></td>
                                                            <td><div className="table-cell-content"><TableIcon label="Remarks" value={record.remarks} /><CellText value={record.remarks} /></div></td>
                                                        </tr>
                                                    ))}
                                                    {rows(overview?.attendanceRecords).length === 0 && (
                                                        <TableEmptyState colSpan={4} message="No attendance records yet." />
                                                    )}
                                                </tbody>
                                            </table>
                                        </ParentTableCard>
                                    </div>
                                )}

                                {attendanceTab === 'timetable' && (
                                    <ParentTableCard title="Weekly timetable" count={rows(overview?.timetable).length} icon="Table">
                                        <table className="re-table parent-table">
                                            <thead>
                                                <tr>
                                                    <th><span className="table-col"><TableIcon label="Day" /><span>Day</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Subject" /><span>Subject</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Time" /><span>Time</span></span></th>
                                                    <th><span className="table-col"><TableIcon label="Room" /><span>Room</span></span></th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {rows(overview?.timetable).map((entry, index) => (
                                                    <tr key={`${text(entry.day)}-${text(entry.start)}-${index}`}>
                                                        <td className="font-semibold"><div className="table-cell-content"><TableIcon label="Day" value={entry.day} /><CellText value={entry.day} /></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Subject" value={entry.subject} /><CellText value={entry.subject} /></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Time" value={entry.start} /><CellText value={`${label(entry.start)} – ${label(entry.end)}`} /></div></td>
                                                        <td><div className="table-cell-content"><TableIcon label="Room" value={entry.room} /><CellText value={entry.room} /></div></td>
                                                    </tr>
                                                ))}
                                                {rows(overview?.timetable).length === 0 && (
                                                    <TableEmptyState colSpan={4} message="No timetable entries for this class yet." />
                                                )}
                                            </tbody>
                                        </table>
                                    </ParentTableCard>
                                )}
                            </>
                        )}

                        {tab === 'fees' && (
                            <div className="space-y-4">
                                <div className="parent-metric-grid parent-metric-grid--compact">
                                    <MetricCard label="Billed" value={money(fees.billed)} icon="Invoice" progress={feeBilled ? 100 : 0} tone="primary" footer="Total invoiced" />
                                    <MetricCard label="Paid" value={money(fees.paid)} icon="CheckCircle" progress={feeClearance} tone="success" footer="Settled so far" />
                                    <MetricCard label="Outstanding" value={money(fees.outstanding)} icon="Hourglass" progress={100 - feeClearance} tone="warning" footer="Remaining balance" />
                                </div>
                                <ParentTableCard
                                    title="Fee records"
                                    count={rows(overview?.feeRecords).length}
                                    icon="Wallet"
                                >
                                    <table className="re-table parent-table">
                                        <thead>
                                            <tr>
                                                <th><span className="table-col"><TableIcon label="Reference" /><span>Reference</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Fee" /><span>Fee</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Amount" /><span>Amount</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Paid" /><span>Paid</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Due" /><span>Due</span></span></th>
                                                <th><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {rows(overview?.feeRecords).map((fee, index) => (
                                                <tr key={`${text(fee.reference)}-${index}`}>
                                                    <td className="font-mono text-xs"><div className="table-cell-content"><TableIcon label="Reference" value={fee.reference} /><CellText value={fee.reference} /></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Fee" value={fee.feeType} /><CellText value={fee.feeType} /></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Amount" value={fee.amount} /><CellText value={money(fee.amount)} /></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Paid" value={fee.paidAmount} /><CellText value={money(fee.paidAmount)} /></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Due" value={fee.dueDate} /><CellText value={fee.dueDate} /></div></td>
                                                    <td><div className="table-cell-content"><TableIcon label="Status" value={fee.status} /><CellText value={fee.status}><StatusBadge label={label(fee.status)} tone={toneForValue(fee.status)} /></CellText></div></td>
                                                </tr>
                                            ))}
                                            {rows(overview?.feeRecords).length === 0 && (
                                                <TableEmptyState colSpan={6} message="No fee records yet." />
                                            )}
                                        </tbody>
                                    </table>
                                </ParentTableCard>
                            </div>
                        )}

                    </>
                )}
            </div>
        </Layout>
    );
}
