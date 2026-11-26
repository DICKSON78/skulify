import Icon from '../components/Icon';
import { useCallback, useEffect, useState } from 'react';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile, text } from '../components/ModuleUi';
import { api, operationsApi } from '../lib/api';

type Row = Record<string, unknown>;
type Summary = { books: number; loans: number; activeLoans: number; labSessions: number };

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

export default function LibraryDesk() {
    const [students, setStudents] = useState<Row[]>([]);
    const [copies, setCopies] = useState<Row[]>([]);
    const [books, setBooks] = useState<Row[]>([]);
    const [loans, setLoans] = useState<Row[]>([]);
    const [summary, setSummary] = useState<Summary | null>(null);

    const [studentId, setStudentId] = useState('');
    const [copyId, setCopyId] = useState('');
    const [bookId, setBookId] = useState('');
    const [loanId, setLoanId] = useState('');
    const [lab, setLab] = useState('LAB-A');
    const [seat, setSeat] = useState('');
    const [startAt, setStartAt] = useState('');
    const [endAt, setEndAt] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const loadAll = useCallback(() => {
        Promise.all([
            api.list<Row>('students', 0, 100, ''),
            api.list<Row>('book-copies', 0, 100, ''),
            api.list<Row>('books', 0, 100, ''),
            api.list<Row>('book-loans', 0, 100, ''),
            operationsApi.librarySummary<Summary>(),
        ])
            .then(([studentPage, copyPage, bookPage, loanPage, overview]) => {
                setStudents(studentPage.content);
                setCopies(copyPage.content);
                setBooks(bookPage.content);
                setLoans(loanPage.content);
                setSummary(overview);
                setStudentId((current) => current || studentPage.content[0]?.id?.toString() || '');
                setCopyId((current) => current || copyPage.content.find((row) => text(row.status) === 'AVAILABLE')?.id?.toString() || '');
                setBookId((current) => current || bookPage.content[0]?.id?.toString() || '');
                setLoanId((current) => current || loanPage.content[0]?.id?.toString() || '');
            })
            .catch((reason) => setError(message(reason)));
    }, []);

    useEffect(() => {
        loadAll();
    }, [loadAll]);

    const run = async (action: () => Promise<unknown>, done: string) => {
        setError('');
        setSuccess('');
        try {
            await action();
            setSuccess(done);
            loadAll();
        } catch (reason) {
            setError(message(reason));
        }
    };

    return (
        <Layout>
            <div className="space-y-4">
                <PageHeader
                    title="Library & Computer Lab"
                    subtitle="Issue books, manage returns and reserve study time with digital copies"
                    icon="BookOpenText"
                />
                <Notice error={error} success={success} />

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatTile label="Titles" value={summary?.books ?? '—'} icon="Books" tone="primary" footer="Books in the catalogue" />
                    <StatTile label="Active loans" value={summary?.activeLoans ?? '—'} icon="BookmarkSimple" tone="warning" footer="Currently borrowed" />
                    <StatTile label="Lab bookings" value={summary?.labSessions ?? '—'} icon="Code" tone="info" footer="Reserved lab sessions" />
                    <StatTile label="Total loans" value={summary?.loans ?? '—'} icon="Receipt" tone="success" footer="All-time checkouts" />
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <FormCard title="Issue a book" icon="BookmarkSimple">
                        <div className="space-y-3">
                            <label className="block">
                                <span className="form-label">Student</span>
                                <select className="form-input w-full" value={studentId} onChange={(event) => setStudentId(event.target.value)}>
                                    <option value="">Choose student...</option>
                                    {students.map((student) => (
                                        <option key={text(student.id)} value={text(student.id)}>
                                            {text(student.firstName)} {text(student.lastName)} · {text(student.studentCode)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="block">
                                <span className="form-label">Available copy</span>
                                <select className="form-input w-full" value={copyId} onChange={(event) => setCopyId(event.target.value)}>
                                    <option value="">Choose copy...</option>
                                    {copies.filter((copy) => text(copy.status) === 'AVAILABLE').map((copy) => (
                                        <option key={text(copy.id)} value={text(copy.id)}>{text(copy.barcode)}</option>
                                    ))}
                                </select>
                            </label>
                            <button
                                type="button"
                                disabled={!studentId || !copyId}
                                onClick={() => void run(() => operationsApi.checkoutBook({ studentId, copyId }), 'Book issued for 14 days.')}
                                className="btn-primary"
                            >
                                <Icon name="BookmarkSimple" size={18} className="mr-2" /> Check out
                            </button>
                        </div>
                    </FormCard>

                    <FormCard title="Return or renew" icon="ArrowCounterClockwise">
                        <div className="space-y-3">
                            <label className="block">
                                <span className="form-label">Loan</span>
                                <select className="form-input w-full" value={loanId} onChange={(event) => setLoanId(event.target.value)}>
                                    <option value="">Choose loan...</option>
                                    {loans.filter((loan) => text(loan.status) !== 'RETURNED').map((loan) => (
                                        <option key={text(loan.id)} value={text(loan.id)}>
                                            {text(loan.studentCode) || text(loan.studentId).slice(0, 8)} · due {text(loan.dueAt).slice(0, 10)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <div className="flex gap-2">
                                <button
                                    type="button"
                                    disabled={!loanId}
                                    onClick={() => void run(() => operationsApi.returnBook(loanId), 'Return recorded; overdue fines calculated automatically.')}
                                    className="btn-primary"
                                >
                                    <Icon name="Check" size={18} className="mr-2" /> Return
                                </button>
                                <button
                                    type="button"
                                    disabled={!loanId}
                                    onClick={() => void run(() => operationsApi.renewBook(loanId), 'Loan renewed for 7 days.')}
                                    className="btn-outline"
                                >
                                    <Icon name="ArrowClockwise" size={18} className="mr-2" /> Renew
                                </button>
                            </div>
                        </div>
                    </FormCard>

                    <FormCard title="Reserve a book" icon="Bookmark">
                        <div className="space-y-3">
                            <label className="block">
                                <span className="form-label">Book</span>
                                <select className="form-input w-full" value={bookId} onChange={(event) => setBookId(event.target.value)}>
                                    <option value="">Choose book...</option>
                                    {books.map((book) => (
                                        <option key={text(book.id)} value={text(book.id)}>{text(book.title)}</option>
                                    ))}
                                </select>
                            </label>
                            <button
                                type="button"
                                disabled={!studentId || !bookId}
                                onClick={() => void run(() => operationsApi.reserveBook({ studentId, bookId }), 'Reservation placed.')}
                                className="btn-primary"
                            >
                                <Icon name="Bookmark" size={18} className="mr-2" /> Reserve
                            </button>
                        </div>
                    </FormCard>

                    <FormCard title="Book computer lab time" icon="Code">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <label className="block">
                                <span className="form-label">Lab</span>
                                <input className="form-input w-full" value={lab} onChange={(event) => setLab(event.target.value)} />
                            </label>
                            <label className="block">
                                <span className="form-label">Seat</span>
                                <input className="form-input w-full" value={seat} onChange={(event) => setSeat(event.target.value)} placeholder="e.g. 12" />
                            </label>
                            <label className="block">
                                <span className="form-label">Start</span>
                                <input className="form-input w-full" type="datetime-local" value={startAt} onChange={(event) => setStartAt(event.target.value)} />
                            </label>
                            <label className="block">
                                <span className="form-label">End</span>
                                <input className="form-input w-full" type="datetime-local" value={endAt} onChange={(event) => setEndAt(event.target.value)} />
                            </label>
                        </div>
                        <label className="block mt-3">
                            <span className="form-label">Digital book (optional)</span>
                            <select className="form-input w-full" value={bookId} onChange={(event) => setBookId(event.target.value)}>
                                <option value="">No specific book</option>
                                {books.filter((book) => text(book.digitalUrl)).map((book) => (
                                    <option key={text(book.id)} value={text(book.id)}>{text(book.title)}</option>
                                ))}
                            </select>
                        </label>
                        <button
                            type="button"
                            disabled={!studentId || !lab || !startAt || !endAt}
                            onClick={() =>
                                void run(
                                    () =>
                                        operationsApi.bookLab({
                                            studentId,
                                            bookId: bookId || undefined,
                                            lab,
                                            seat,
                                            startAt: new Date(startAt).toISOString(),
                                            endAt: new Date(endAt).toISOString(),
                                            purpose: 'Study session',
                                        }),
                                    'Lab session booked. Digital library access is linked to the booking.',
                                )
                            }
                            className="btn-primary mt-4"
                        >
                            <Icon name="CalendarCheck" size={18} className="mr-2" /> Book lab
                        </button>
                    </FormCard>
                </div>
            </div>
        </Layout>
    );
}
