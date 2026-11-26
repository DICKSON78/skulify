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

const today = () => new Date().toISOString().slice(0, 10);

export default function ResultsPlanning() {
    const [summary, setSummary] = useState<Row | null>(null);
    const [exams, setExams] = useState<Row[]>([]);
    const [classes, setClasses] = useState<Row[]>([]);
    const [cards, setCards] = useState<Row[]>([]);
    const [sessions, setSessions] = useState<Row[]>([]);
    const [examId, setExamId] = useState('');
    const [classId, setClassId] = useState('');
    const [scheduleStart, setScheduleStart] = useState(today());
    const [periodMinutes, setPeriodMinutes] = useState('120');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = useCallback(() => {
        Promise.all([
            academicsApi.summary<Row>(),
            api.list<Row>('exams', 0, 100, ''),
            api.list<Row>('classes', 0, 100, ''),
            api.list<Row>('report-cards', 0, 100, ''),
            api.list<Row>('exam-sessions', 0, 100, ''),
        ])
            .then(([summaryData, examPage, classPage, cardPage, sessionPage]) => {
                setSummary(summaryData);
                setExams(examPage.content);
                setClasses(classPage.content);
                setCards(cardPage.content);
                setSessions(sessionPage.content);
                setExamId((current) => current || examPage.content[0]?.id?.toString() || '');
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

    const generate = () =>
        run(
            () =>
                academicsApi.generateResults({
                    examId,
                    ...(classId ? { classId } : {}),
                }),
            'Report cards generated from uploaded marks.',
        );

    const schedule = () =>
        run(
            () =>
                academicsApi.scheduleExam(examId, {
                    startDate: scheduleStart,
                    periodMinutes,
                }),
            'Exam timetable scheduled.',
        );

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Results & Exam Planning"
                    subtitle="Schedule exam papers, generate ranked results and publish report cards"
                    icon="Medal"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Exams" value={text(summary?.exams)} icon="ClipboardText" tone="primary" footer="Scheduled this term" />
                    <StatTile label="Papers scheduled" value={text(summary?.examSessions)} icon="CalendarBlank" tone="info" footer="Timetabled exam sessions" />
                    <StatTile label="Report cards" value={text(summary?.reportCards)} icon="Medal" tone="success" footer="Published for this term" />
                    <StatTile label="Draft results" value={text(summary?.draftResults)} icon="Exam" tone="warning" footer="Awaiting publication" />
                </div>

                <FormCard title="Plan an exam" icon="CalendarBlank">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        <label className="block">
                            <span className="form-label">Exam</span>
                            <select className="form-input w-full" value={examId} onChange={(event) => setExamId(event.target.value)}>
                                <option value="">Choose an exam...</option>
                                {exams.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>
                                        {text(row.name) || text(row.code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Schedule from</span>
                            <input className="form-input w-full" type="date" value={scheduleStart} onChange={(event) => setScheduleStart(event.target.value)} />
                        </label>
                        <label className="block">
                            <span className="form-label">Minutes per paper</span>
                            <input className="form-input w-full" type="number" min="1" value={periodMinutes} onChange={(event) => setPeriodMinutes(event.target.value)} />
                        </label>
                        <div className="flex items-end">
                            <button type="button" disabled={!examId} onClick={() => void schedule()} className="btn-primary w-full justify-center">
                                <Icon name="CalendarPlus" size={18} className="mr-2" /> Schedule papers
                            </button>
                        </div>
                    </div>
                </FormCard>

                <FormCard title="Generate results" icon="MagicWand">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <label className="block">
                            <span className="form-label">Exam</span>
                            <select className="form-input w-full" value={examId} onChange={(event) => setExamId(event.target.value)}>
                                <option value="">Choose an exam...</option>
                                {exams.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>
                                        {text(row.name) || text(row.code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Class (optional — all when blank)</span>
                            <select className="form-input w-full" value={classId} onChange={(event) => setClassId(event.target.value)}>
                                <option value="">All classes</option>
                                {classes.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>
                                        {text(row.name) || text(row.code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                    </div>
                    <button type="button" disabled={!examId} onClick={() => void generate()} className="btn-primary mt-4">
                        <Icon name="Medal" size={18} className="mr-2" /> Generate report cards
                    </button>
                </FormCard>

                <FormCard title="Scheduled papers" icon="CalendarCheck">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Date" /><span>Date</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Time" /><span>Time</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Subject" /><span>Subject</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Room" /><span>Room</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {sessions.slice(0, 20).map((row) => (
                                    <tr key={text(row.id)} className="border-t border-gray-100">
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Date" value={row.examDate} /><span className="min-w-0">{text(row.examDate)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Time" value={row.startTime} /><span className="min-w-0">{text(row.startTime)}–{text(row.endTime)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Subject" value={row.subjectName || row.subjectId} /><span className="min-w-0">{text(row.subjectName) || text(row.subjectId).slice(0, 8)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Room" value={row.room} /><span className="min-w-0">{text(row.room)}</span></div></td>
                                        <td className="py-2"><div className="table-cell-content"><TableIcon label="Status" value={row.status} /><span className="min-w-0"><span className="badge badge-primary">{text(row.status)}</span></span></div></td>
                                    </tr>
                                ))}
                                {sessions.length === 0 && (
                                    <TableEmptyState colSpan={5} message="No exam papers scheduled yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>

                <FormCard title="Report cards" icon="Certificate">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Reference" /><span>Reference</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Student" /><span>Student</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Average" /><span>Average</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Grade" /><span>Grade</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Position" /><span>Position</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon name="ListChecks" /></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {cards.slice(0, 20).map((row) => (
                                    <tr key={text(row.id)} className="border-t border-gray-100">
                                        <td className="py-2 pr-4 font-mono text-xs"><div className="table-cell-content"><TableIcon label="Reference" value={row.reference} /><span className="min-w-0">{text(row.reference)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Student" value={row.studentName || row.studentId} /><span className="min-w-0">{text(row.studentName) || text(row.studentId).slice(0, 8)}</span></div></td>
                                        <td className="py-2 pr-4 font-semibold"><div className="table-cell-content"><TableIcon label="Average" value={row.average} /><span className="min-w-0">{text(row.average)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Grade" value={row.grade} /><span className="min-w-0"><span className="badge badge-info">{text(row.grade)}</span></span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Position" value={row.position} /><span className="min-w-0">{text(row.position)} / {text(row.classSize)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Status" value={row.status} /><span className="min-w-0"><span className="badge badge-primary">{text(row.status)}</span></span></div></td>
                                        <td className="py-2"><div className="table-cell-content"><TableIcon name="ListChecks" /><span className="min-w-0">
                                            {text(row.status) === 'DRAFT' && (
                                                <button
                                                    type="button"
                                                    className="text-xs font-semibold text-primary hover:underline"
                                                    onClick={() =>
                                                        void run(
                                                            () => academicsApi.publishResult(text(row.id)),
                                                            'Report card published and guardian notified.',
                                                        )
                                                    }
                                                >
                                                    Publish
                                                </button>
                                            )}
                                        </span></div></td>
                                    </tr>
                                ))}
                                {cards.length === 0 && (
                                    <TableEmptyState colSpan={7} message="No report cards generated yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>

                <FormCard title="Automation controls" icon="Robot">
                    <p className="text-sm text-gray-600">
                        Run the full daily school automation now: publish due results, send duty and exam
                        reminders, escalate overdue tasks and prepare tomorrow's staff roll call.
                    </p>
                    <button
                        type="button"
                        onClick={() =>
                            void run(
                                () => academicsApi.runDaily(),
                                'Daily school automation completed.',
                            )
                        }
                        className="btn-primary mt-4"
                    >
                        <Icon name="ArrowClockwise" size={18} className="mr-2" /> Run daily automation
                    </button>
                </FormCard>
            </div>
        </Layout>
    );
}
