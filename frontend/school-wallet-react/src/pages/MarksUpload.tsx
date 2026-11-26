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

const rows = (value: unknown): Row[] => (Array.isArray(value) ? (value as Row[]) : []);

const personName = (row: Row): string =>
    `${text(row.firstName)} ${text(row.lastName)}`.trim();

const studentLabel = (row: Row): string =>
    personName(row) || text(row.studentCode) || text(row.id).slice(0, 8);

export default function MarksUpload() {
    const [classes, setClasses] = useState<Row[]>([]);
    const [subjects, setSubjects] = useState<Row[]>([]);
    const [students, setStudents] = useState<Row[]>([]);
    const [enrolments, setEnrolments] = useState<Row[]>([]);
    const [grades, setGrades] = useState<Row[]>([]);
    const [classId, setClassId] = useState('');
    const [subjectId, setSubjectId] = useState('');
    const [examType, setExamType] = useState('TEST');
    const [term, setTerm] = useState('TERM_1');
    const [maxScore, setMaxScore] = useState('100');
    const [scores, setScores] = useState<Record<string, string>>({});
    const [remarks, setRemarks] = useState<Record<string, string>>({});
    const [notify, setNotify] = useState(true);
    const [autoResults, setAutoResults] = useState(true);
    const [templateFile, setTemplateFile] = useState<File | null>(null);
    const [importResult, setImportResult] = useState<Row | null>(null);
    const [uploaded, setUploaded] = useState(0);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const loadGrades = useCallback(() => {
        api.list<Row>('grades', 0, 50, examType)
            .then((page) => setGrades(page.content))
            .catch((reason) => setError(message(reason)));
    }, [examType]);

    useEffect(() => {
        Promise.all([
            api.list<Row>('classes', 0, 100, ''),
            api.list<Row>('subjects', 0, 100, ''),
            api.list<Row>('students', 0, 100, ''),
            api.list<Row>('enrolments', 0, 100, ''),
        ])
            .then(([classPage, subjectPage, studentPage, enrolmentPage]) => {
                setClasses(classPage.content);
                setSubjects(subjectPage.content);
                setStudents(studentPage.content);
                setEnrolments(enrolmentPage.content);
                setClassId((current) => current || classPage.content[0]?.id?.toString() || '');
                setSubjectId((current) => current || subjectPage.content[0]?.id?.toString() || '');
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        loadGrades();
    }, [loadGrades]);

    // Keep only students actively enrolled in the chosen class.
    const enrolledStudents = students.filter((student) =>
        enrolments.some(
            (row) => text(row.studentId) === text(student.id)
                && text(row.classId) === classId
                && text(row.status) === 'ACTIVE',
        ),
    );

    const upload = async () => {
        setError('');
        setSuccess('');
        const entries = students
            .filter((student) => scores[text(student.id)] !== undefined && scores[text(student.id)] !== '')
            .map((student) => ({
                studentId: text(student.id),
                score: scores[text(student.id)],
                remarks: remarks[text(student.id)] ?? '',
            }));
        if (entries.length === 0) {
            setError('Enter at least one score before uploading.');
            return;
        }
        try {
            const saved = await academicsApi.uploadMarks<Row[]>({
                classId,
                subjectId,
                examType,
                term,
                maxScore,
                notify: String(notify),
                entries,
            });
            setUploaded(saved.length);
            setSuccess(`${saved.length} mark(s) saved with computed grades.`);
            loadGrades();
        } catch (reason) {
            setError(message(reason));
        }
    };

    const downloadTemplate = async () => {
        setError('');
        if (!classId) {
            setError('Choose a class before downloading the template.');
            return;
        }
        try {
            await academicsApi.downloadMarksTemplate(classId, examType, term);
            setSuccess('Marks template downloaded. Fill the score column and upload it here.');
        } catch (reason) {
            setError(message(reason));
        }
    };

    const importTemplate = async () => {
        setError('');
        setSuccess('');
        if (!templateFile || !classId || !subjectId) {
            setError('Choose a class, subject and filled template before importing.');
            return;
        }
        const form = new FormData();
        form.append('file', templateFile);
        form.append('classId', classId);
        form.append('subjectId', subjectId);
        form.append('examType', examType);
        form.append('term', term);
        form.append('maxScore', maxScore);
        form.append('notify', String(notify));
        form.append('autoResults', String(autoResults));
        try {
            const result = await academicsApi.importMarks<Row>(form);
            setImportResult(result);
            setUploaded(Number(result.grades) || 0);
            setSuccess(
                `${result.grades ?? 0} mark(s) imported. ${result.ranked ?? 0} students ranked and ` +
                `${result.letters ?? 0} parent letters generated automatically.`,
            );
            loadGrades();
        } catch (reason) {
            setError(message(reason));
        }
    };

    const regenerateLetters = async () => {
        setError('');
        if (!importResult?.examId) {
            setError('Import a marks template first, then regenerate letters.');
            return;
        }
        try {
            const letters = await academicsApi.generateLetters<Row[]>(
                text(importResult.examId),
                classId || undefined,
            );
            setSuccess(`${letters.length} parent letter(s) refreshed.`);
        } catch (reason) {
            setError(message(reason));
        }
    };

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Upload Marks"
                    subtitle="Upload a filled template or enter marks directly; results, ranking and letters are automated"
                    icon="FileArrowUp"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Students" value={students.length} icon="GraduationCap" tone="primary" footer="In the selected class" />
                    <StatTile label="Subjects" value={subjects.length} icon="Books" tone="info" footer="Available this term" />
                    <StatTile label="Scores entered" value={Object.values(scores).filter(Boolean).length} icon="PencilSimple" tone="warning" footer="Ready for publication" />
                    <StatTile label="Last upload" value={uploaded} icon="CheckCircle" tone="success" footer="Most recent marks import" />
                </div>

                <FormCard title="Select assessment" icon="SlidersHorizontal">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <label className="block">
                            <span className="form-label">Class</span>
                            <select className="form-input w-full" value={classId} onChange={(event) => setClassId(event.target.value)}>
                                <option value="">Choose a class...</option>
                                {classes.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>
                                        {text(row.name) || text(row.code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Subject</span>
                            <select className="form-input w-full" value={subjectId} onChange={(event) => setSubjectId(event.target.value)}>
                                <option value="">Choose a subject...</option>
                                {subjects.map((row) => (
                                    <option key={text(row.id)} value={text(row.id)}>
                                        {text(row.name) || text(row.code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Assessment type</span>
                            <input
                                className="form-input w-full"
                                value={examType}
                                onChange={(event) => setExamType(event.target.value.toUpperCase())}
                                placeholder="TEST / MID_TERM / FINAL"
                            />
                        </label>
                        <label className="block">
                            <span className="form-label">Term</span>
                            <select className="form-input w-full" value={term} onChange={(event) => setTerm(event.target.value)}>
                                <option value="TERM_1">Term 1</option>
                                <option value="TERM_2">Term 2</option>
                                <option value="TERM_3">Term 3</option>
                            </select>
                        </label>
                        <label className="block">
                            <span className="form-label">Maximum score</span>
                            <input
                                className="form-input w-full"
                                type="number"
                                min="1"
                                value={maxScore}
                                onChange={(event) => setMaxScore(event.target.value)}
                            />
                        </label>
                        <label className="mt-6 flex items-center gap-2 text-sm text-gray-700">
                            <input type="checkbox" checked={notify} onChange={(event) => setNotify(event.target.checked)} />
                            Notify guardians
                        </label>
                        <label className="mt-6 flex items-center gap-2 text-sm text-gray-700">
                            <input type="checkbox" checked={autoResults} onChange={(event) => setAutoResults(event.target.checked)} />
                            Rank class & generate letters
                        </label>
                    </div>
                </FormCard>

                <FormCard title="Spreadsheet upload & automation" icon="FileXls">
                    <p className="mb-4 text-sm text-gray-600">
                        Download the Excel-compatible template, fill the <strong>score</strong> column
                        for each student, then upload it. The system saves every mark, grades each
                        student, ranks the class and writes next-semester parent letters.
                    </p>
                    <div className="flex flex-wrap items-center gap-3">
                        <button type="button" onClick={() => void downloadTemplate()} className="btn-outline">
                            <Icon name="DownloadSimple" size={18} className="mr-2" /> Download template
                        </button>
                        <label className="flex items-center gap-2 text-sm text-gray-700">
                            <span className="form-label mb-0">Filled template</span>
                            <input
                                type="file"
                                accept=".csv,text/csv"
                                onChange={(event) => setTemplateFile(event.target.files?.[0] ?? null)}
                                className="form-input"
                            />
                        </label>
                        <button type="button" onClick={() => void importTemplate()} className="btn-primary">
                            <Icon name="FileArrowUp" size={18} className="mr-2" /> Import & automate
                        </button>
                        {Boolean(importResult?.examId) && (
                            <button type="button" onClick={() => void regenerateLetters()} className="btn-outline">
                                <Icon name="EnvelopeOpen" size={18} className="mr-2" /> Refresh letters
                            </button>
                        )}
                    </div>
                </FormCard>

                {importResult && (
                    <FormCard title="Automated class ranking & parent letters" icon="Medal">
                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                            <StatTile label="Marks saved" value={text(importResult.grades)} icon="Exam" tone="primary" footer="From the latest import" />
                            <StatTile label="Students ranked" value={text(importResult.ranked)} icon="Medal" tone="success" footer="Included in the class ranking" />
                            <StatTile label="Parent letters" value={text(importResult.letters)} icon="EnvelopeOpen" tone="info" footer="Generated after results publish" />
                            <StatTile
                                label="Top of class"
                                value={importResult.top ? `${text((importResult.top as Row).name)} · ${text((importResult.top as Row).average)}%` : '—'}
                                icon="Medal"
                                tone="warning"
                                footer="Highest average in the class"
                            />
                        </div>
                        <div className="overflow-x-auto">
                            <table className="min-w-full text-sm">
                                <thead>
                                    <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Position" /><span>Position</span></span></th>
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Student" /><span>Student</span></span></th>
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Registration" /><span>Registration</span></span></th>
                                        <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Average" /><span>Average</span></span></th>
                                        <th className="py-2"><span className="table-col"><TableIcon label="Grade" /><span>Grade</span></span></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows(importResult.students).map((student, index) => (
                                        <tr key={`${text(student.studentId)}-${index}`} className="border-t border-gray-100">
                                            <td className="py-2 pr-4 font-bold text-primary"><div className="table-cell-content"><TableIcon label="Position" value={student.position} /><span className="min-w-0">{text(student.position)}</span></div></td>
                                            <td className="py-2 pr-4 font-semibold text-gray-900"><div className="table-cell-content"><TableIcon label="Student" value={student.name} /><span className="min-w-0">{text(student.name)}</span></div></td>
                                            <td className="py-2 pr-4 font-mono text-xs text-gray-500"><div className="table-cell-content"><TableIcon label="Registration" value={student.registrationNo} /><span className="min-w-0">{text(student.registrationNo)}</span></div></td>
                                            <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Average" value={student.average} /><span className="min-w-0">{text(student.average)}%</span></div></td>
                                            <td className="py-2"><div className="table-cell-content"><TableIcon label="Grade" value={student.grade} /><span className="min-w-0"><span className="badge badge-primary">{text(student.grade)}</span></span></div></td>
                                        </tr>
                                    ))}
                                    {rows(importResult.students).length === 0 && (
                                        <TableEmptyState colSpan={5} message="No ranking generated yet." />
                                    )}
                                </tbody>
                            </table>
                        </div>
                        {rows(importResult.lettersDetail).length > 0 && (
                            <div className="mt-5 space-y-3">
                                <h3 className="text-sm font-bold text-gray-900">Generated parent letters</h3>
                                {rows(importResult.lettersDetail).map((letter) => (
                                    <div key={text(letter.reference)} className="rounded-xl border border-gray-200 bg-gray-50 p-4 text-sm">
                                        <p className="font-semibold text-gray-900">
                                            {text(letter.name)} · <span className="font-mono text-xs">{text(letter.reference)}</span>
                                        </p>
                                        <p className="mt-1 text-green-700"><Icon name="ArrowUp" size={18} className="mr-1" /> Strongest: {text(letter.strongest)}</p>
                                        <p className="text-amber-700"><Icon name="ArrowDown" size={18} className="mr-1" /> Needs attention: {text(letter.weak)}</p>
                                        <pre className="mt-2 whitespace-pre-wrap font-sans text-xs text-gray-600">{text(letter.requirements)}</pre>
                                    </div>
                                ))}
                            </div>
                        )}
                    </FormCard>
                )}

                <FormCard title="Student scores" icon="Table">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Student" /><span>Student</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Code" /><span>Code</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Score" /><span>Score / {maxScore}</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon label="Comment" /><span>Comment</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {enrolledStudents.map((student) => (
                                    <tr key={text(student.id)} className="border-t border-gray-100">
                                        <td className="py-2 pr-4 font-semibold text-gray-900"><div className="table-cell-content"><TableIcon label="Student" value={studentLabel(student)} /><span className="min-w-0">{studentLabel(student)}</span></div></td>
                                        <td className="py-2 pr-4 font-mono text-xs text-gray-500"><div className="table-cell-content"><TableIcon label="Code" value={student.studentCode} /><span className="min-w-0">{text(student.studentCode)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Score" value={scores[text(student.id)]} /><span className="min-w-0">
                                            <input
                                                className="form-input w-28"
                                                type="number"
                                                min="0"
                                                max={maxScore}
                                                value={scores[text(student.id)] ?? ''}
                                                onChange={(event) =>
                                                    setScores((current) => ({ ...current, [text(student.id)]: event.target.value }))
                                                }
                                                placeholder="—"
                                            />
                                        </span></div></td>
                                        <td className="py-2"><div className="table-cell-content"><TableIcon label="Comment" value={remarks[text(student.id)]} /><span className="min-w-0">
                                            <input
                                                className="form-input w-full"
                                                value={remarks[text(student.id)] ?? ''}
                                                onChange={(event) =>
                                                    setRemarks((current) => ({ ...current, [text(student.id)]: event.target.value }))
                                                }
                                                placeholder="Optional remark"
                                            />
                                        </span></div></td>
                                    </tr>
                                ))}
                                {enrolledStudents.length === 0 && (
                                    <TableEmptyState colSpan={4} message="No active enrolments found for this class." />
                                )}
                            </tbody>
                        </table>
                    </div>
                    <button
                        type="button"
                        disabled={!classId || !subjectId}
                        onClick={() => void upload()}
                        className="btn-primary mt-4"
                    >
                        <Icon name="FileArrowUp" size={18} className="mr-2" /> Upload marks
                    </button>
                </FormCard>

                <FormCard title="Recently recorded grades" icon="Receipt">
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Assessment" /><span>Assessment</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Score" /><span>Score</span></span></th>
                                    <th className="py-2 pr-4"><span className="table-col"><TableIcon label="Grade" /><span>Grade</span></span></th>
                                    <th className="py-2"><span className="table-col"><TableIcon label="Term" /><span>Term</span></span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {grades.slice(0, 20).map((row) => (
                                    <tr key={text(row.id)} className="border-t border-gray-100">
                                        <td className="py-2 pr-4 font-semibold"><div className="table-cell-content"><TableIcon label="Assessment" value={row.examType} /><span className="min-w-0">{text(row.examType)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Score" value={row.score} /><span className="min-w-0">{text(row.score)} / {text(row.maxScore)}</span></div></td>
                                        <td className="py-2 pr-4"><div className="table-cell-content"><TableIcon label="Grade" value={row.grade} /><span className="min-w-0"><span className="badge badge-primary">{text(row.grade)}</span></span></div></td>
                                        <td className="py-2 text-gray-500"><div className="table-cell-content"><TableIcon label="Term" value={row.term} /><span className="min-w-0">{text(row.term)}</span></div></td>
                                    </tr>
                                ))}
                                {grades.length === 0 && (
                                    <TableEmptyState colSpan={4} message="No grades recorded yet." />
                                )}
                            </tbody>
                        </table>
                    </div>
                </FormCard>
            </div>
        </Layout>
    );
}
