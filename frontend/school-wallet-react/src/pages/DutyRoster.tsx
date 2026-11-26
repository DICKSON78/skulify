import TableIcon from '../components/TableIcon';
import TableEmptyState from '../components/TableEmptyState';
import Icon from '../components/Icon';
import { useCallback, useEffect, useState } from 'react';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import { academicsApi, api } from '../lib/api';

type Row = Record<string, unknown>;

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const DEFAULT_DUTIES = 'ASSEMBLY, DISCIPLINE, LUNCH, DISMISSAL';

const today = () => new Date().toISOString().slice(0, 10);
const plusDays = (count: number) => {
    const date = new Date();
    date.setDate(date.getDate() + count);
    return date.toISOString().slice(0, 10);
};

export default function DutyRoster() {
    const [duties, setDuties] = useState<Row[]>([]);
    const [leaves, setLeaves] = useState<Row[]>([]);
    const [teachers, setTeachers] = useState<Row[]>([]);
    const [startDate, setStartDate] = useState(today());
    const [endDate, setEndDate] = useState(plusDays(30));
    const [days, setDays] = useState<string[]>(DAYS.slice(0, 5));
    const [dutyTypes, setDutyTypes] = useState(DEFAULT_DUTIES);
    const [perDay, setPerDay] = useState('1');
    const [location, setLocation] = useState('');
    const [term, setTerm] = useState('TERM_1');
    const [generated, setGenerated] = useState(0);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = useCallback(() => {
        Promise.all([
            api.list<Row>('duty-assignments', 0, 100, ''),
            api.list<Row>('staff-leaves', 0, 100, ''),
            api.list<Row>('teachers', 0, 100, ''),
        ])
            .then(([dutyPage, leavePage, teacherPage]) => {
                setDuties(dutyPage.content);
                setLeaves(leavePage.content);
                setTeachers(teacherPage.content);
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const teacherName = (id: unknown): string => {
        const found = teachers.find((row) => text(row.id) === text(id));
        if (!found) {
            return text(id).slice(0, 8);
        }
        return `${text(found.firstName)} ${text(found.lastName)}`.trim();
    };

    const toggleDay = (day: string) => {
        setDays((current) =>
            current.includes(day) ? current.filter((value) => value !== day) : [...current, day],
        );
    };

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

    const generate = async () => {
        setError('');
        setSuccess('');
        try {
            const result = await academicsApi.generateDuty<{ generated: number; skipped: number }>({
                startDate,
                endDate,
                days,
                dutyTypes,
                perDay,
                location,
                term,
            });
            setGenerated(result.generated);
            setSuccess(`${result.generated} duty assignment(s) created, ${result.skipped} skipped.`);
            load();
        } catch (reason) {
            setError(message(reason));
        }
    };

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Teacher Duty Roster"
                    subtitle="Automatically rotate teachers through duties, skipping approved leave"
                    icon="UserList"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Teachers" value={teachers.length} icon="ChalkboardTeacher" tone="primary" footer="Available for duty allocation" />
                    <StatTile label="Scheduled duties" value={duties.filter((row) => text(row.status) === 'SCHEDULED').length} icon="CheckCircle" tone="success" footer="Confirmed in this roster" />
                    <StatTile label="On leave" value={leaves.filter((row) => text(row.status) === 'APPROVED').length} icon="Airplane" tone="warning" footer="Approved leave requests" />
                    <StatTile label="Last generated" value={generated} icon="MagicWand" tone="info" footer="Most recent roster run" />
                </div>

                <FormCard title="Generate duty roster" icon="MagicWand">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <label className="block">
                            <span className="form-label">From</span>
                            <input className="form-input w-full" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
                        </label>
                        <label className="block">
                            <span className="form-label">To</span>
                            <input className="form-input w-full" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                        </label>
                        <label className="block">
                            <span className="form-label">Duty types (comma separated)</span>
                            <input className="form-input w-full" value={dutyTypes} onChange={(event) => setDutyTypes(event.target.value)} />
                        </label>
                        <label className="block">
                            <span className="form-label">Duties per day</span>
                            <input className="form-input w-full" type="number" min="1" value={perDay} onChange={(event) => setPerDay(event.target.value)} />
                        </label>
                        <label className="block">
                            <span className="form-label">Location (optional)</span>
                            <input className="form-input w-full" value={location} onChange={(event) => setLocation(event.target.value)} placeholder="e.g. School gate" />
                        </label>
                        <label className="block">
                            <span className="form-label">Term</span>
                            <select className="form-input w-full" value={term} onChange={(event) => setTerm(event.target.value)}>
                                <option value="TERM_1">Term 1</option>
                                <option value="TERM_2">Term 2</option>
                                <option value="TERM_3">Term 3</option>
                            </select>
                        </label>
                    </div>

                    <div className="mt-4">
                        <span className="form-label">School days</span>
                        <div className="mt-2 flex flex-wrap gap-2">
                            {DAYS.map((day) => (
                                <button
                                    key={day}
                                    type="button"
                                    onClick={() => toggleDay(day)}
                                    className={`rounded-full border px-3 py-1 text-xs font-semibold ${
                                        days.includes(day)
                                            ? 'border-primary bg-primary text-white'
                                            : 'border-gray-300 bg-white text-gray-600'
                                    }`}
                                >
                                    {day.charAt(0) + day.slice(1).toLowerCase()}
                                </button>
                            ))}
                        </div>
                    </div>

                    <button type="button" disabled={!startDate || !endDate} onClick={() => void generate()} className="btn-primary mt-5">
                        <Icon name="MagicWand" size={18} className="mr-2" /> Generate roster
                    </button>
                </FormCard>

                <FormCard title="Duty schedule" icon="CalendarCheck">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Date" /><span>Date</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Teacher" /><span>Teacher</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Duty" /><span>Duty</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Time" /><span>Time</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon name="ListChecks" /></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {duties.slice(0, 50).map((row) => (
                                    <tr key={text(row.id)} className="border-t border-gray-100">
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Date" value={row.dutyDate} /><span className="min-w-0">{text(row.dutyDate)}</span></div></td>
                                        <td className="py-2 pr-4 font-semibold"><div className="table-cell-content"><TableIcon label="Teacher" value={teacherName(row.teacherId)} /><span className="min-w-0">{teacherName(row.teacherId)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Duty" value={row.dutyType} /><span className="min-w-0"><span className="badge badge-info">{text(row.dutyType)}</span></span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Time" value={row.startTime} /><span className="min-w-0">{text(row.startTime)}–{text(row.endTime)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Status" value={row.status} /><span className="min-w-0"><span className="badge badge-primary">{text(row.status)}</span></span></div></td>
                                        <td className="py-2"><div className="table-cell-content"><TableIcon name="ListChecks" /><span className="min-w-0">
                                            {text(row.status) === 'SCHEDULED' && (
                                                <button
                                                    type="button"
                                                    className="text-xs font-semibold text-red-600 hover:underline"
                                                    onClick={() =>
                                                        void run(
                                                            () => academicsApi.cancelDuty(text(row.id)),
                                                            'Duty assignment cancelled.',
                                                        )
                                                    }
                                                >
                                                    Cancel
                                                </button>
                                            )}
                                        </span></div></td>
                                    </tr>
                                ))}
                                {duties.length === 0 && (
                                    <TableEmptyState colSpan={6} message="No duty assignments yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>

                <FormCard title="Staff leave requests" icon="Airplane">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Teacher" /><span>Teacher</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Type" /><span>Type</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="From" /><span>From</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="To" /><span>To</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon name="ListChecks" /></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {leaves.map((row) => (
                                    <tr key={text(row.id)} className="border-t border-gray-100">
                                        <td className="py-2 pr-4 font-semibold"><div className="table-cell-content"><TableIcon label="Teacher" value={teacherName(row.teacherId)} /><span className="min-w-0">{teacherName(row.teacherId)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Type" value={row.leaveType} /><span className="min-w-0"><span className="badge badge-info">{text(row.leaveType)}</span></span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="From" value={row.startDate} /><span className="min-w-0">{text(row.startDate)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="To" value={row.endDate} /><span className="min-w-0">{text(row.endDate)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Status" value={row.status} /><span className="min-w-0"><span className="badge badge-primary">{text(row.status)}</span></span></div></td>
                                        <td className="py-2"><div className="table-cell-content"><TableIcon name="ListChecks" /><span className="min-w-0">
                                            {text(row.status) === 'PENDING' && (
                                                <div className="flex gap-3">
                                                    <button
                                                        type="button"
                                                        className="text-xs font-semibold text-green-600 hover:underline"
                                                        onClick={() =>
                                                            void run(
                                                                () => academicsApi.decideLeave(text(row.id), 'APPROVE'),
                                                                'Leave approved. Duty cover task created.',
                                                            )
                                                        }
                                                    >
                                                        Approve
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="text-xs font-semibold text-red-600 hover:underline"
                                                        onClick={() =>
                                                            void run(
                                                                () => academicsApi.decideLeave(text(row.id), 'REJECT'),
                                                                'Leave request rejected.',
                                                            )
                                                        }
                                                    >
                                                        Reject
                                                    </button>
                                                </div>
                                            )}
                                        </span></div></td>
                                    </tr>
                                ))}
                                {leaves.length === 0 && (
                                    <TableEmptyState colSpan={6} message="No leave requests yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>
            </div>
        </Layout>
    );
}
