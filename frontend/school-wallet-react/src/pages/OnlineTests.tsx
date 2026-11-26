import TableIcon from '../components/TableIcon';
import TableEmptyState from '../components/TableEmptyState';
import Icon from '../components/Icon';
import { useCallback, useEffect, useState } from 'react';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import { api, testsApi } from '../lib/api';

type Row = Record<string, unknown>;
type Question = {
    prompt: string;
    options: string[];
    correctIndex: number;
    points: string;
};

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const rows = (value: unknown): Row[] => (Array.isArray(value) ? (value as Row[]) : []);

const blankQuestion = (): Question => ({
    prompt: '',
    options: ['', '', '', ''],
    correctIndex: 0,
    points: '1',
});

export default function OnlineTests() {
    const [classes, setClasses] = useState<Row[]>([]);
    const [subjects, setSubjects] = useState<Row[]>([]);
    const [tests, setTests] = useState<Row[]>([]);
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [classId, setClassId] = useState('');
    const [subjectId, setSubjectId] = useState('');
    const [duration, setDuration] = useState('30');
    const [accessCode, setAccessCode] = useState('');
    const [questions, setQuestions] = useState<Question[]>([blankQuestion()]);
    const [results, setResults] = useState<Row | null>(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = useCallback(() => {
        Promise.all([
            testsApi.list<Row[]>(),
            api.list<Row>('classes', 0, 100, ''),
            api.list<Row>('subjects', 0, 100, ''),
        ])
            .then(([testRows, classPage, subjectPage]) => {
                setTests(testRows);
                setClasses(classPage.content);
                setSubjects(subjectPage.content);
                setClassId((current) => current || text(classPage.content[0]?.id));
                setSubjectId((current) => current || text(subjectPage.content[0]?.id));
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const updateQuestion = (index: number, patch: Partial<Question>) => {
        setQuestions((current) =>
            current.map((question, position) => (position === index ? { ...question, ...patch } : question)),
        );
    };

    const create = async () => {
        setError('');
        setSuccess('');
        const prepared = questions.map((question) => ({
            prompt: question.prompt.trim(),
            options: question.options.map((option) => option.trim()).filter(Boolean),
            correctIndex: question.correctIndex,
            points: Number(question.points) || 1,
        }));
        if (!title.trim() || !classId || !subjectId) {
            setError('Title, class and subject are required.');
            return;
        }
        if (prepared.some((question) => !question.prompt || question.options.length < 2)) {
            setError('Every question needs a prompt and at least two options.');
            return;
        }
        try {
            await testsApi.create<Row>({
                title,
                description,
                classId,
                subjectId,
                durationMinutes: duration,
                accessCode,
                questions: prepared,
            });
            setTitle('');
            setDescription('');
            setAccessCode('');
            setQuestions([blankQuestion()]);
            setSuccess('Test prepared. Publish it, then share the generated link with your class.');
            load();
        } catch (reason) {
            setError(message(reason));
        }
    };

    const setStatus = async (testId: string, status: 'PUBLISHED' | 'CLOSED') => {
        setError('');
        try {
            await testsApi[status === 'PUBLISHED' ? 'publish' : 'close']<Row>(testId);
            setSuccess(`Test ${status === 'PUBLISHED' ? 'published' : 'closed'} successfully.`);
            load();
        } catch (reason) {
            setError(message(reason));
        }
    };

    const viewResults = async (testId: string) => {
        setError('');
        try {
            setResults(await testsApi.results<Row>(testId));
        } catch (reason) {
            setError(message(reason));
        }
    };

    const copyLink = async (test: Row) => {
        setError('');
        try {
            const response = await testsApi.link<{ link: string }>(text(test.id));
            await navigator.clipboard.writeText(`${window.location.origin}${response.link}`);
            setSuccess('Student test link copied to the clipboard.');
        } catch (reason) {
            setError(message(reason));
        }
    };

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Online Tests"
                    subtitle="Prepare computer-based tests, issue codes and see who attended"
                    icon="Laptop"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Tests prepared" value={tests.length} icon="Laptop" tone="primary" footer="Across all published states" />
                    <StatTile label="Published" value={tests.filter((test) => text(test.status) === 'PUBLISHED').length} icon="CheckCircle" tone="success" footer="Available to students" />
                    <StatTile label="Drafts" value={tests.filter((test) => text(test.status) === 'DRAFT').length} icon="Exam" tone="warning" footer="Awaiting publication" />
                    <StatTile label="Total attempts" value={tests.reduce((sum, test) => sum + (Number(test.attempts) || 0), 0)} icon="UserCheck" tone="info" footer="Student submissions recorded" />
                </div>

                <FormCard title="Prepare a new test" icon="Plus">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <label className="block sm:col-span-2">
                            <span className="form-label">Test title</span>
                            <input className="form-input w-full" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="e.g. Form 2 Mathematics quiz" />
                        </label>
                        <label className="block">
                            <span className="form-label">Teacher code</span>
                            <input className="form-input w-full" value={accessCode} onChange={(event) => setAccessCode(event.target.value)} placeholder="Auto-generated if blank" />
                        </label>
                        <label className="block">
                            <span className="form-label">Class</span>
                            <select className="form-input w-full" value={classId} onChange={(event) => setClassId(event.target.value)}>
                                <option value="">Choose a class...</option>
                                {classes.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>{text(row.name) || text(row.code)}</option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Subject</span>
                            <select className="form-input w-full" value={subjectId} onChange={(event) => setSubjectId(event.target.value)}>
                                <option value="">Choose a subject...</option>
                                {subjects.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>{text(row.name) || text(row.code)}</option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Duration (minutes)</span>
                            <input className="form-input w-full" type="number" min="1" value={duration} onChange={(event) => setDuration(event.target.value)} />
                        </label>
                        <label className="block sm:col-span-3">
                            <span className="form-label">Instructions for students</span>
                            <textarea className="form-input w-full" rows={2} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Optional guidance shown before the test starts" />
                        </label>
                    </div>

                    <div className="mt-5 space-y-3">
                        <div className="flex items-center justify-between">
                            <h3 className="text-sm font-bold text-gray-900">Questions</h3>
                            <button type="button" className="btn-outline" onClick={() => setQuestions((current) => [...current, blankQuestion()])}>
                                <Icon name="Plus" size={18} className="mr-2" /> Add question
                            </button>
                        </div>
                        {questions.map((question, index) => (
                            <div key={index} className="rounded-xl border border-gray-200 p-4">
                                <div className="flex items-start justify-between gap-3">
                                    <span className="rounded-lg bg-primary-light px-2.5 py-1 text-xs font-bold text-primary">
                                        Question {index + 1}
                                    </span>
                                    {questions.length > 1 && (
                                        <button
                                            type="button"
                                            className="text-xs text-red-600 hover:text-red-800"
                                            onClick={() => setQuestions((current) => current.filter((_, position) => position !== index))}
                                        >
                                            <Icon name="Trash" size={18} className="mr-1" /> Remove
                                        </button>
                                    )}
                                </div>
                                <input
                                    className="form-input w-full mt-3"
                                    value={question.prompt}
                                    onChange={(event) => updateQuestion(index, { prompt: event.target.value })}
                                    placeholder="Type the question"
                                />
                                <div className="grid sm:grid-cols-2 gap-3 mt-3">
                                    {question.options.map((option, optionIndex) => (
                                        <div key={optionIndex} className="flex items-center gap-2">
                                            <input
                                                type="radio"
                                                name={`correct-${index}`}
                                                checked={question.correctIndex === optionIndex}
                                                onChange={() => updateQuestion(index, { correctIndex: optionIndex })}
                                                aria-label={`Mark option ${optionIndex + 1} as correct`}
                                            />
                                            <input
                                                className="form-input w-full"
                                                value={option}
                                                onChange={(event) => {
                                                    const options = [...question.options];
                                                    options[optionIndex] = event.target.value;
                                                    updateQuestion(index, { options });
                                                }}
                                                placeholder={`Option ${optionIndex + 1}`}
                                            />
                                        </div>
                                    ))}
                                </div>
                                <div className="mt-3 w-32">
                                    <span className="form-label">Points</span>
                                    <input className="form-input w-full" type="number" min="1" value={question.points} onChange={(event) => updateQuestion(index, { points: event.target.value })} />
                                </div>
                            </div>
                        ))}
                    </div>

                    <button type="button" onClick={() => void create()} className="btn-primary mt-4">
                        <Icon name="FloppyDisk" size={18} className="mr-2" /> Save test
                    </button>
                </FormCard>

                <FormCard title="Prepared tests" icon="ListChecks">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Test" /><span>Test</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Code" /><span>Code</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Questions" /><span>Questions</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Attempts" /><span>Attempts</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Status" /><span>Status</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon label="Actions" /><span>Actions</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {tests.map((test) => (
                                    <tr key={text(test.id)} className="border-t border-gray-100 align-top">
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Test" value={test.title} /><span className="min-w-0">
                                            <p className="font-semibold text-gray-900">{text(test.title)}</p>
                                            <p className="text-xs text-gray-400">{text(test.code)}</p>
                                        </span></div></td>
                                        <td className="py-2 pr-4 font-mono text-xs"><div className="table-cell-content"><TableIcon label="Code" value={test.accessCode} /><span className="min-w-0">{text(test.accessCode)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Questions" value={test.questionCount} /><span className="min-w-0">{text(test.questionCount)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Attempts" value={test.attempts} /><span className="min-w-0">{text(test.attempts)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Status" value={test.status} /><span className="min-w-0"><span className="badge badge-primary">{text(test.status)}</span></span></div></td>
                                        <td className="py-2"><div className="table-cell-content"><TableIcon label="Actions" /><span className="min-w-0">
                                            <div className="flex flex-wrap gap-2">
                                                {text(test.status) !== 'PUBLISHED' && (
                                                    <button type="button" className="btn-primary btn-xs" onClick={() => void setStatus(text(test.id), 'PUBLISHED')}>
                                                        Publish
                                                    </button>
                                                )}
                                                {text(test.status) === 'PUBLISHED' && (
                                                    <button type="button" className="btn-outline btn-xs" onClick={() => void setStatus(text(test.id), 'CLOSED')}>
                                                        Close
                                                    </button>
                                                )}
                                                <button type="button" className="btn-outline btn-xs" onClick={() => void copyLink(test)}>
                                                    <Icon name="Link" size={18} className="mr-1" /> Copy link
                                                </button>
                                                <button type="button" className="btn-outline btn-xs" onClick={() => void viewResults(text(test.id))}>
                                                    <Icon name="Users" size={18} className="mr-1" /> Attendance
                                                </button>
                                            </div>
                                        </span></div></td>
                                    </tr>
                                ))}
                                {tests.length === 0 && (
                                    <TableEmptyState colSpan={6} message="No tests prepared yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>

                {results && (
                    <FormCard
                        title={`Attendance · ${text((results.test as Row | undefined)?.title)}`}
                        icon="UserCheck"
                    >
                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                            <StatTile label="Expected" value={Number(results.expected) || 0} icon="Users" tone="primary" footer="Students invited to sit the test" />
                            <StatTile label="Attended" value={Number(results.attended) || 0} icon="CheckCircle" tone="success" footer="Completed the test" />
                            <StatTile label="Not attended" value={Number(results.notAttended) || 0} icon="XCircle" tone="danger" footer="No submission recorded" />
                            <StatTile label="Average score" value={Number(results.averageScore) || 0} icon="ChartLineUp" tone="info" footer="Across all recorded attempts" />
                        </div>
                        <div className="overflow-x-auto">
                            <table className="min-w-full text-sm">
                                <thead>
                                    <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Student" /><span>Student</span></span></th>
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Registration" /><span>Registration</span></span></th>
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Attended" /><span>Attended</span></span></th>
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Score" /><span>Score</span></span></th>
                                        <th className="py-2"><span className="table-col"><TableIcon label="Submitted" /><span>Submitted</span></span></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows(results.students).map((student) => (
                                        <tr key={text(student.studentId)} className="border-t border-gray-100">
                                            <td className="py-2 pr-4 font-semibold"><div className="table-cell-content"><TableIcon label="Student" value={student.name} /><span className="min-w-0">{text(student.name)}</span></div></td>
                                            <td className="py-2 pr-4 font-mono text-xs"><div className="table-cell-content"><TableIcon label="Registration" value={student.registrationNo} /><span className="min-w-0">{text(student.registrationNo)}</span></div></td>
                                            <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Attended" value={student.attended} /><span className="min-w-0">
                                                <span className={`badge ${student.attended ? 'badge-success' : 'badge-danger'}`}>
                                                    {student.attended ? 'YES' : 'NOT ATTENDED'}
                                                </span>
                                            </span></div></td>
                                            <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Score" value={student.score} /><span className="min-w-0">{student.score === null || student.score === undefined ? '—' : `${text(student.score)} / ${text(student.maxScore)}`}</span></div></td>
                                            <td className="py-2"><div className="table-cell-content"><TableIcon label="Submitted" value={student.submittedAt} /><span className="min-w-0">{text(student.submittedAt) || '—'}</span></div></td>
                                        </tr>
                                    ))}
                                    {rows(results.students).length === 0 && (
                                        <TableEmptyState colSpan={5} message="No active enrolments in this class." />
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </FormCard>
                )}
            </div>
        </Layout>
    );
}
