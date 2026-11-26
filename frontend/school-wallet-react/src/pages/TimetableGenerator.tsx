import TableIcon from '../components/TableIcon';
import TableEmptyState from '../components/TableEmptyState';
import Icon from '../components/Icon';
import { useCallback, useEffect, useState } from 'react';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import { academicsApi, api } from '../lib/api';

type Row = Record<string, unknown>;
type Generation = { generated: number; skipped: number; periods: number };

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

const label = (row: Row): string =>
    text(row.name) || text(row.code) || text(row.id).slice(0, 8);

export default function TimetableGenerator() {
    const [classes, setClasses] = useState<Row[]>([]);
    const [periods, setPeriods] = useState<Row[]>([]);
    const [entries, setEntries] = useState<Row[]>([]);
    const [classId, setClassId] = useState('');
    const [room, setRoom] = useState('');
    const [days, setDays] = useState<string[]>(DAYS.slice(0, 5));
    const [replace, setReplace] = useState(false);
    const [result, setResult] = useState<Generation | null>(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const loadEntries = useCallback((value: string) => {
        if (!value) {
            setEntries([]);
            return;
        }
        api.list<Row>('timetable', 0, 100, '')
            .then((page) => setEntries(page.content.filter((row) => text(row.classId) === value)))
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        Promise.all([
            api.list<Row>('classes', 0, 100, ''),
            api.list<Row>('periods', 0, 100, ''),
        ])
            .then(([classPage, periodPage]) => {
                setClasses(classPage.content);
                setPeriods(periodPage.content);
                setClassId((current) => current || classPage.content[0]?.id?.toString() || '');
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        loadEntries(classId);
    }, [classId, loadEntries]);

    const toggleDay = (day: string) => {
        setDays((current) =>
            current.includes(day) ? current.filter((value) => value !== day) : [...current, day],
        );
    };

    const generate = async () => {
        setError('');
        setSuccess('');
        setResult(null);
        try {
            const response = await academicsApi.generateTimetable<Generation>({
                classId,
                room,
                days,
                replace: String(replace),
            });
            setResult(response);
            setSuccess(
                `Timetable generated: ${response.generated} lesson slot(s) created, `
                + `${response.skipped} clash(es) skipped.`,
            );
            loadEntries(classId);
        } catch (reason) {
            setError(message(reason));
        }
    };

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Generate Timetable"
                    subtitle="Build a clash-free weekly lesson timetable automatically"
                    icon="MagicWand"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Classes" value={classes.length} icon="Buildings" tone="primary" footer="Included in generation" />
                    <StatTile label="Periods" value={periods.length} icon="Clock" tone="info" footer="Available time slots" />
                    <StatTile label="Slots created" value={result?.generated ?? 0} icon="CheckCircle" tone="success" footer="From the latest run" />
                    <StatTile label="Clashes skipped" value={result?.skipped ?? 0} icon="Warning" tone="warning" footer="Overlapping entries avoided" />
                </div>

                <FormCard title="Generation settings" icon="SlidersHorizontal">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <label className="block">
                            <span className="form-label">Class</span>
                            <select className="form-input w-full" value={classId} onChange={(event) => setClassId(event.target.value)}>
                                <option value="">Choose a class...</option>
                                {classes.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>{label(row)}</option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Room (optional)</span>
                            <input
                                className="form-input w-full"
                                value={room}
                                onChange={(event) => setRoom(event.target.value)}
                                placeholder="Uses the class room when blank"
                            />
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

                    <label className="mt-4 flex items-center gap-2 text-sm text-gray-700">
                        <input
                            type="checkbox"
                            checked={replace}
                            onChange={(event) => setReplace(event.target.checked)}
                        />
                        Replace existing timetable entries for this class
                    </label>

                    <button
                        type="button"
                        disabled={!classId || days.length === 0}
                        onClick={() => void generate()}
                        className="btn-primary mt-5"
                    >
                        <Icon name="MagicWand" size={18} className="mr-2" /> Generate timetable
                    </button>
                </FormCard>

                <FormCard title="Available periods" icon="Clock">
                    <div className="flex flex-wrap gap-2">
                        {periods.length === 0 && <p className="text-sm text-gray-400">No periods defined yet.</p>}
                        {periods.map((row) => (
                            <span key={text(row.id)} className="badge badge-info">
                                {label(row)} · {text(row.startTime)}–{text(row.endTime)}
                            </span>
                        ))}
                    </div>
                </FormCard>

                <FormCard title="Generated timetable" icon="Table">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Day" /><span>Day</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Time" /><span>Time</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Subject" /><span>Subject</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Teacher" /><span>Teacher</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon label="Room" /><span>Room</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {entries.map((row) => (
                                    <tr key={text(row.id)} className="border-t border-gray-100">
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Day" value={row.dayOfWeek} /><span className="min-w-0"><span className="badge badge-primary">{text(row.dayOfWeek)}</span></span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Time" value={row.startTime} /><span className="min-w-0">{text(row.startTime)}–{text(row.endTime)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Subject" value={row.subjectName || row.subjectId} /><span className="min-w-0">{text(row.subjectName) || text(row.subjectId).slice(0, 8)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Teacher" value={row.teacherName || row.teacherId} /><span className="min-w-0">{text(row.teacherName) || text(row.teacherId).slice(0, 8)}</span></div></td>
                                        <td className="py-2 text-gray-500"><div className="table-cell-content"><TableIcon label="Room" value={row.room} /><span className="min-w-0">{text(row.room)}</span></div></td>
                                    </tr>
                                ))}
                                {entries.length === 0 && (
                                    <TableEmptyState colSpan={5} message="No timetable entries for this class yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>
            </div>
        </Layout>
    );
}
