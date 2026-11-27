import Icon from '../components/Icon';
import Layout from '../components/Layout';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import PageHeader from '../components/PageHeader';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import type { Row } from '../components/ResourcePage';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { classAttendanceApi } from '../lib/api';

type AttendanceOverview = {
    classes: Row[];
    classId: string;
    className: string;
    terms: Row[];
    termId: string;
    termName: string;
    numberOfWeeks: number;
    week: number;
    weekStart: string;
    weekEnd: string;
    students: Row[];
    statuses: Record<string, string>;
    weeks: Row[];
    totalStudents: number;
    completedWeeks: number;
    semesterProgress: number;
    markedThisWeek: number;
    weekProgress: number;
};

const STATUS_OPTIONS = [
    { value: 'PRESENT', label: 'Present', icon: 'CheckCircle' },
    { value: 'ABSENT', label: 'Absent', icon: 'XCircle' },
    { value: 'EXCUSED', label: 'Ruhusa', icon: 'WarningCircle' },
] as const;

const clamp = (value: number): number => Math.min(100, Math.max(0, value));

const studentName = (row: Row): string =>
    `${text(row.firstName)} ${text(row.lastName)}`.trim() || text(row.studentCode);

function Progress({ value, label }: { value: number; label: string }) {
    return (
        <div
            className="attendance-progress"
            role="progressbar"
            aria-label={label}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(clamp(value))}
        >
            <span className="attendance-progress-fill" style={{ width: `${clamp(value)}%` }} />
        </div>
    );
}

export default function ClassAttendance() {
    const [data, setData] = useState<AttendanceOverview | null>(null);
    const [selections, setSelections] = useState<Record<string, string>>({});
    const [classId, setClassId] = useState('');
    const [termId, setTermId] = useState('');
    const [week, setWeek] = useState(1);
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = useCallback(
        async (nextClassId?: string, nextTermId?: string, nextWeek?: number) => {
            setLoading(true);
            setError('');
            try {
                const result = await classAttendanceApi.overview<AttendanceOverview>(
                    nextClassId,
                    nextTermId,
                    nextWeek,
                );
                setData(result);
                setSelections({ ...(result.statuses ?? {}) });
                setClassId(text(result.classId));
                setTermId(text(result.termId));
                setWeek(Number(result.week) || 1);
            } catch (reason) {
                setError(reason instanceof Error ? reason.message : 'Unable to load attendance');
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    useEffect(() => {
        void load();
    }, [load]);

    const students = data?.students ?? [];
    const totalStudents = data?.totalStudents ?? students.length;
    const markedCount = students.filter((row) => Boolean(selections[text(row.id)])).length;
    const currentWeek = (data?.weeks ?? []).find((row) => Number(row.number) === week);
    const weekComplete = totalStudents > 0 && markedCount === totalStudents;
    const alreadyComplete = Boolean(currentWeek?.complete);
    const projectedCompletedWeeks =
        (Number(data?.completedWeeks) || 0) + (weekComplete && !alreadyComplete ? 1 : 0);
    const numberOfWeeks = Number(data?.numberOfWeeks) || 1;
    const semesterPercent = numberOfWeeks
        ? (projectedCompletedWeeks * 100) / numberOfWeeks
        : 0;
    const weekPercent = totalStudents ? (markedCount * 100) / totalStudents : 0;

    const filteredStudents = useMemo(() => {
        const query = search.trim().toLowerCase();
        if (!query) return students;
        return students.filter((row) =>
            `${studentName(row)} ${text(row.studentCode)}`.toLowerCase().includes(query),
        );
    }, [students, search]);

    const chooseStatus = (studentId: string, status: string) => {
        setSuccess('');
        setSelections((current) => ({ ...current, [studentId]: status }));
    };

    const save = async () => {
        if (!data || markedCount === 0) {
            setError('Choose a status for at least one student.');
            return;
        }
        setSaving(true);
        setError('');
        setSuccess('');
        try {
            const entries = students
                .filter((row) => Boolean(selections[text(row.id)]))
                .map((row) => ({
                    studentId: text(row.id),
                    status: selections[text(row.id)],
                }));
            const result = await classAttendanceApi.save<AttendanceOverview>({
                classId,
                termId,
                week,
                entries,
            });
            setData(result);
            setSelections({ ...(result.statuses ?? {}) });
            setSuccess(
                weekComplete
                    ? 'Week complete. Semester progress updated.'
                    : `${entries.length} attendance record(s) saved.`,
            );
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : 'Unable to save attendance');
        } finally {
            setSaving(false);
        }
    };

    return (
        <Layout>
            <PageHeader
                title="Class Attendance"
                subtitle="Mark every student in your class. Progress follows completed semester weeks."
                icon="CalendarCheck"
            />

            <Notice error={error} success={success} />

            {loading && !data ? (
                <div className="card mt-4 flex items-center justify-center gap-2 py-12 text-muted">
                    <Icon name="CircleNotch" size={20} className="animate-spin" />
                    Loading class register...
                </div>
            ) : (
                <>
                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mt-4">
                        <StatTile
                            label="Students in class"
                            value={totalStudents}
                            icon="UsersThree"
                            tone="primary"
                            footer={text(data?.className) || 'No class selected'}
                        />
                        <StatTile
                            label="Weeks complete"
                            value={`${projectedCompletedWeeks} / ${numberOfWeeks}`}
                            icon="CalendarCheck"
                            tone="success"
                            footer={`${text(data?.termName) || 'Term'} · week ${week}`}
                        />
                        <StatTile
                            label="Marked this week"
                            value={markedCount}
                            icon="ListChecks"
                            tone="warning"
                            footer={`${totalStudents - markedCount} student(s) remaining`}
                        />
                        <StatTile
                            label="Semester attendance"
                            value={`${Math.round(semesterPercent)}%`}
                            icon="ChartLineUp"
                            tone="info"
                            footer="Completed weeks ÷ semester weeks"
                        />
                    </div>

                    <FormCard title="Progress" icon="ChartLineUp">
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                            <div>
                                <div className="flex items-center justify-between gap-3 mb-2">
                                    <span className="text-sm font-semibold text-gray-800">
                                        Semester progress
                                    </span>
                                    <span className="text-sm font-semibold text-primary">
                                        {Math.round(semesterPercent)}%
                                    </span>
                                </div>
                                <Progress value={semesterPercent} label="Semester attendance progress" />
                                <p className="text-xs text-muted mt-2">
                                    {projectedCompletedWeeks} of {numberOfWeeks} weeks complete ·{' '}
                                    {text(data?.weekStart)} to {text(data?.weekEnd)}
                                </p>
                            </div>
                            <div>
                                <div className="flex items-center justify-between gap-3 mb-2">
                                    <span className="text-sm font-semibold text-gray-800">
                                        This week's register
                                    </span>
                                    <span className="text-sm font-semibold text-primary">
                                        {Math.round(weekPercent)}%
                                    </span>
                                </div>
                                <Progress value={weekPercent} label="Current week attendance progress" />
                                <p className="text-xs text-muted mt-2">
                                    {markedCount} of {totalStudents} student(s) marked in week {week}
                                </p>
                            </div>
                        </div>
                    </FormCard>

                    <FormCard title="Attendance register" icon="ListChecks">
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                            <label className="block">
                                <span className="form-label">Class</span>
                                <select
                                    className="form-input w-full"
                                    value={classId}
                                    disabled={loading}
                                    onChange={(event) =>
                                        void load(event.target.value, termId, week)
                                    }
                                >
                                    {(data?.classes ?? []).map((row) => (
                                        <option key={text(row.id)} value={text(row.id)}>
                                            {text(row.name) || text(row.code)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="block">
                                <span className="form-label">Term</span>
                                <select
                                    className="form-input w-full"
                                    value={termId}
                                    disabled={loading}
                                    onChange={(event) => void load(classId, event.target.value, 1)}
                                >
                                    {(data?.terms ?? []).map((row) => (
                                        <option key={text(row.id)} value={text(row.id)}>
                                            {text(row.name)} · {text(row.numberOfWeeks)} weeks
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="block">
                                <span className="form-label">Week</span>
                                <select
                                    className="form-input w-full"
                                    value={week}
                                    disabled={loading}
                                    onChange={(event) =>
                                        void load(classId, termId, Number(event.target.value))
                                    }
                                >
                                    {(data?.weeks ?? []).map((row) => (
                                        <option key={text(row.number)} value={text(row.number)}>
                                            Week {text(row.number)} · {text(row.startDate)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="block">
                                <span className="form-label">Find student</span>
                                <input
                                    className="form-input w-full"
                                    value={search}
                                    placeholder="Search name or code..."
                                    onChange={(event) => setSearch(event.target.value)}
                                />
                            </label>
                        </div>

                        <div className="re-table-wrap mt-5">
                            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-6 py-4">
                                <div>
                                    <h2 className="font-semibold text-gray-900">
                                        Week {week} attendance
                                    </h2>
                                    <p className="text-xs text-gray-500">
                                        {text(data?.weekStart)} to {text(data?.weekEnd)} ·{' '}
                                        {totalStudents} student(s) in class
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    className="btn-primary"
                                    disabled={saving || markedCount === 0}
                                    onClick={() => void save()}
                                >
                                    <Icon name="FloppyDisk" size={16} />
                                    {saving ? 'Saving...' : 'Save attendance'}
                                </button>
                            </div>

                            <div className="attendance-roster-scroll">
                                <table className="re-table">
                                    <thead>
                                        <tr>
                                            <th>
                                                <span className="table-col">
                                                    <TableIcon label="No." />
                                                    <span>No.</span>
                                                </span>
                                            </th>
                                            <th>
                                                <span className="table-col">
                                                    <TableIcon label="Student" />
                                                    <span>Student</span>
                                                </span>
                                            </th>
                                            <th className="text-right">
                                                <span className="table-col justify-end">
                                                    <TableIcon name="ListChecks" />
                                                    <span>Attendance</span>
                                                </span>
                                            </th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredStudents.length === 0 ? (
                                            <TableEmptyState
                                                colSpan={3}
                                                message={
                                                    students.length === 0
                                                        ? 'No students are enrolled in this class'
                                                        : 'No students match your search'
                                                }
                                                icon="UsersThree"
                                            />
                                        ) : (
                                            filteredStudents.map((student, index) => {
                                                const studentId = text(student.id);
                                                const chosen = selections[studentId] ?? '';
                                                return (
                                                    <tr key={studentId}>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon label="No." />
                                                                <span>{index + 1}</span>
                                                            </div>
                                                        </td>
                                                        <td>
                                                            <div className="table-cell-content">
                                                                <TableIcon
                                                                    label="Student"
                                                                    value={studentName(student)}
                                                                />
                                                                <span className="min-w-0">
                                                                    <span className="block font-semibold text-gray-900">
                                                                        {studentName(student)}
                                                                    </span>
                                                                    <span className="block text-xs text-muted">
                                                                        {text(student.studentCode)}
                                                                    </span>
                                                                </span>
                                                            </div>
                                                        </td>
                                                        <td>
                                                            <div className="table-cell-content justify-end">
                                                                <TableIcon name="ListChecks" />
                                                                <div className="attendance-options">
                                                                    {STATUS_OPTIONS.map((option) => (
                                                                        <label
                                                                            key={option.value}
                                                                            className={`attendance-choice ${
                                                                                chosen === option.value
                                                                                    ? `selected selected-${option.value.toLowerCase()}`
                                                                                    : ''
                                                                            }`}
                                                                        >
                                                                            <input
                                                                                type="radio"
                                                                                name={`attendance-${studentId}`}
                                                                                value={option.value}
                                                                                checked={chosen === option.value}
                                                                                onChange={() =>
                                                                                    chooseStatus(
                                                                                        studentId,
                                                                                        option.value,
                                                                                    )
                                                                                }
                                                                                aria-label={`${option.label} for ${studentName(student)}`}
                                                                            />
                                                                            <Icon name={option.icon} size={14} />
                                                                            <span>{option.label}</span>
                                                                        </label>
                                                                    ))}
                                                                </div>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                );
                                            })
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </FormCard>
                </>
            )}
        </Layout>
    );
}
