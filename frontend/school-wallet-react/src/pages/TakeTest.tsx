import Icon from '../components/Icon';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Notice, text } from '../components/ModuleUi';
import { publicTestApi } from '../lib/api';

type Row = Record<string, unknown>;
type Session = {
    attemptId: string;
    title: string;
    durationMinutes: number;
    studentName: string;
    questions: Row[];
};
type Result = {
    score: number;
    maxScore: number;
    correct: number;
    total: number;
    details: Row[];
};

const message = (reason: unknown): string =>
    reason instanceof Error ? reason.message : 'Request failed';

const clock = (seconds: number): string => {
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
};

export default function TakeTest() {
    const { token } = useParams<{ token: string }>();
    const [accessCode, setAccessCode] = useState('');
    const [registrationNo, setRegistrationNo] = useState('');
    const [session, setSession] = useState<Session | null>(null);
    const [answers, setAnswers] = useState<number[]>([]);
    const [remaining, setRemaining] = useState(0);
    const [result, setResult] = useState<Result | null>(null);
    const [submitted, setSubmitted] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    useEffect(() => {
        if (!session || result) {
            return;
        }
        const timer = window.setInterval(() => {
            setRemaining((current) => {
                if (current <= 1) {
                    window.clearInterval(timer);
                    return 0;
                }
                return current - 1;
            });
        }, 1000);
        return () => window.clearInterval(timer);
    }, [session, result]);

    const submit = async (automatic = false) => {
        if (!session || !token || result || submitted) {
            return;
        }
        setSubmitted(true);
        setError('');
        try {
            const response = await publicTestApi.submit<Result>(token, {
                accessCode,
                registrationNo,
                answers,
            });
            setResult(response);
            setSuccess(
                automatic
                    ? 'Time finished. Your answers were submitted automatically.'
                    : 'Your test was submitted successfully.',
            );
        } catch (reason) {
            setSubmitted(false);
            setError(message(reason));
        }
    };

    useEffect(() => {
        if (session && remaining === 0 && !result && !submitted) {
            void submit(true);
        }
        // submit is intentionally driven by the timer transition to zero.
    }, [remaining, session, result, submitted]);

    const start = async () => {
        setError('');
        if (!token) {
            setError('This test link is not valid.');
            return;
        }
        if (!accessCode.trim() || !registrationNo.trim()) {
            setError('Enter both the teacher code and your registration number.');
            return;
        }
        try {
            const response = await publicTestApi.start<Session>(token, {
                accessCode,
                registrationNo,
            });
            setSession(response);
            setAnswers(response.questions.map(() => -1));
            setRemaining((Number(response.durationMinutes) || 30) * 60);
        } catch (reason) {
            setError(message(reason));
        }
    };

    return (
        <div className="min-h-screen bg-surface px-4 py-8">
            <div className="mx-auto max-w-3xl space-y-4">
                <div className="flex items-center gap-3 rounded-2xl bg-navy px-5 py-4 text-white">
                    <div className="h-10 w-10 rounded-xl bg-white/10 flex items-center justify-center">
                        <Icon name="GraduationCap" size={18} className="text-primary" />
                    </div>
                    <div>
                        <h1 className="text-lg font-bold">Skulify Online Test</h1>
                        <p className="text-xs text-blue-200/70">Secure student assessment</p>
                    </div>
                    {session && !result && (
                        <div className="ml-auto rounded-xl bg-white/10 px-4 py-2 text-right">
                            <p className="text-[11px] uppercase tracking-wider text-blue-200">Time left</p>
                            <p className={`font-mono text-lg font-bold ${remaining < 60 ? 'text-red-300' : 'text-white'}`}>
                                {clock(remaining)}
                            </p>
                        </div>
                    )}
                </div>

                <Notice error={error} success={success} />

                {!session && (
                    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                        <h2 className="text-xl font-bold text-gray-900">Join your test</h2>
                        <p className="mt-1 text-sm text-gray-500">
                            Use the code given by your teacher and your school registration number.
                        </p>
                        <div className="mt-5 grid gap-4 sm:grid-cols-2">
                            <label className="block">
                                <span className="form-label">Teacher code</span>
                                <input
                                    className="form-input w-full"
                                    value={accessCode}
                                    onChange={(event) => setAccessCode(event.target.value)}
                                    placeholder="e.g. 428193"
                                />
                            </label>
                            <label className="block">
                                <span className="form-label">Registration number</span>
                                <input
                                    className="form-input w-full"
                                    value={registrationNo}
                                    onChange={(event) => setRegistrationNo(event.target.value)}
                                    placeholder="e.g. DEMO-STU-001"
                                />
                            </label>
                        </div>
                        <button type="button" onClick={() => void start()} className="btn-primary mt-6">
                            <Icon name="Play" size={18} className="mr-2" /> Start test
                        </button>
                    </section>
                )}

                {session && !result && (
                    <div className="space-y-4">
                        <section className="rounded-2xl border border-primary/20 bg-primary-light/50 p-5">
                            <h2 className="text-xl font-bold text-gray-900">{session.title}</h2>
                            <p className="text-sm text-gray-600">
                                {session.studentName} · {session.questions.length} question(s) · {session.durationMinutes} minutes
                            </p>
                        </section>
                        {session.questions.map((question, index) => (
                            <section key={index} className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                                <div className="flex items-start gap-3">
                                    <span className="rounded-lg bg-primary-light px-2.5 py-1 text-xs font-bold text-primary">
                                        {index + 1}
                                    </span>
                                    <div className="min-w-0 flex-1">
                                        <p className="font-semibold text-gray-900">{text(question.prompt)}</p>
                                        <div className="mt-3 space-y-2">
                                            {(Array.isArray(question.options) ? question.options : []).map(
                                                (option, optionIndex) => (
                                                    <label
                                                        key={optionIndex}
                                                        className={`flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 text-sm transition-colors ${
                                                            answers[index] === optionIndex
                                                                ? 'border-primary bg-primary-light/60 text-primary-dark'
                                                                : 'border-gray-200 text-gray-700 hover:border-primary'
                                                        }`}
                                                    >
                                                        <input
                                                            type="radio"
                                                            name={`question-${index}`}
                                                            checked={answers[index] === optionIndex}
                                                            onChange={() =>
                                                                setAnswers((current) => {
                                                                    const next = [...current];
                                                                    next[index] = optionIndex;
                                                                    return next;
                                                                })
                                                            }
                                                        />
                                                        <span>{text(option)}</span>
                                                    </label>
                                                ),
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </section>
                        ))}
                        <button
                            type="button"
                            onClick={() => void submit()}
                            disabled={submitted}
                            className="btn-primary w-full"
                        >
                            <Icon name="PaperPlaneTilt" size={18} className="mr-2" />
                            {submitted ? 'Submitting…' : 'Submit test'}
                        </button>
                    </div>
                )}

                {result && (
                    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                        <div className="text-center">
                            <div className="mx-auto h-14 w-14 rounded-full bg-primary-light flex items-center justify-center text-primary text-2xl">
                                <Icon name="CheckCircle" size={18} />
                            </div>
                            <h2 className="mt-3 text-2xl font-bold text-gray-900">Test submitted</h2>
                            <p className="mt-1 text-gray-600">
                                You scored {result.score} out of {result.maxScore} ({result.correct}/{result.total} correct)
                            </p>
                        </div>
                        <div className="mt-5 space-y-2">
                            {result.details.map((detail, index) => (
                                <div key={index} className="rounded-xl border border-gray-100 bg-gray-50 p-4 text-sm">
                                    <p className="font-semibold text-gray-900">{text(detail.question)}</p>
                                    <p className={detail.correct ? 'text-green-700' : 'text-red-700'}>
                                        {detail.correct ? 'Correct' : 'Incorrect'} · Your answer: {text(detail.yourAnswer) || '—'}
                                        {!detail.correct && ` · Correct answer: ${text(detail.correctAnswer)}`}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </section>
                )}
            </div>
        </div>
    );
}
