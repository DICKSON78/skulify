import Icon from '../components/Icon';
import SystemPreloader from '../components/SystemPreloader';
import { useRef, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

type LocationState = { from?: string } | null;

const AUTH_PRELOADER_MS = 4000;

export default function Login() {
    const [identifier, setIdentifier] = useState('');
    const [password, setPassword] = useState('');
    const [mfaCode, setMfaCode] = useState('');
    const [mfaToken, setMfaToken] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const { login, completeMfa, user } = useAuth();
    const destination = (location.state as LocationState)?.from ?? '/dashboard';
    const authStartedAt = useRef(0);

    const startAuth = () => {
        authStartedAt.current = Date.now();
        setError('');
        setBusy(true);
    };

    const finishAuth = async () => {
        const remaining = AUTH_PRELOADER_MS - (Date.now() - authStartedAt.current);
        if (remaining > 0) {
            await new Promise<void>((resolve) => window.setTimeout(resolve, remaining));
        }
        setBusy(false);
    };

    const handleSubmit = async (event: FormEvent) => {
        event.preventDefault();
        if (!identifier || !password) {
            setError('Please enter your username/email and password');
            return;
        }
        startAuth();
        try {
            const result = await login(identifier, password);
            if (result.mfaRequired && result.mfaChallengeToken) {
                setMfaToken(result.mfaChallengeToken);
                await finishAuth();
                return;
            }
            await finishAuth();
            navigate(destination, { replace: true });
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : 'Unable to sign in');
            await finishAuth();
        }
    };

    const handleMfa = async (event: FormEvent) => {
        event.preventDefault();
        startAuth();
        try {
            await completeMfa(mfaToken, mfaCode);
            await finishAuth();
            navigate(destination, { replace: true });
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : 'Unable to verify code');
            await finishAuth();
        }
    };

    const signedInContent = (
        <div className="login-shell">
            <div className="login-card text-center">
                <span className="login-mark">
                    <Icon name="GraduationCap" size={24} />
                </span>
                <p className="page-subtitle">You are already signed in.</p>
                <button className="btn-primary mt-4" onClick={() => navigate(destination)}>
                    Continue to dashboard
                </button>
            </div>
        </div>
    );

    return (
        <>
            {busy && <SystemPreloader />}
            {user ? signedInContent : (
            <div className="login-shell">
                <div className="w-full max-w-[420px]">
                    <div className="login-card">
                        <span className="login-mark">
                            <Icon name="GraduationCap" size={24} />
                        </span>

                        <div className="text-center mb-5">
                            <p className="login-overline">{mfaToken ? 'Verification' : 'Sign in'}</p>
                            <h1 className="login-title">{mfaToken ? 'Enter your code' : 'Welcome back'}</h1>
                            <p className="login-subtitle">
                                {mfaToken
                                    ? 'Enter the 6-digit code from your authenticator app'
                                    : 'Access your Skulify workspace'}
                            </p>
                        </div>

                        {error && (
                            <div className="notice error mb-5">
                                <Icon name="WarningCircle" size={18} />
                                <span>{error}</span>
                            </div>
                        )}

                        {mfaToken ? (
                            <form onSubmit={handleMfa} className="space-y-3">
                                <div>
                                    <label htmlFor="mfaCode" className="form-label">Authentication code</label>
                                    <input
                                        id="mfaCode"
                                        inputMode="numeric"
                                        autoComplete="one-time-code"
                                        maxLength={6}
                                        value={mfaCode}
                                        onChange={(event) => setMfaCode(event.target.value)}
                                        placeholder="000000"
                                        className="login-input text-center tracking-[6px]"
                                        autoFocus
                                    />
                                </div>
                                <button type="submit" className="btn-primary w-full" disabled={busy}>
                                    {busy ? <Icon name="CircleNotch" size={18} className="animate-spin mr-2" /> : null}
                                    Verify and continue
                                </button>
                                <button
                                    type="button"
                                    className="w-full text-sm text-primary hover:underline"
                                    onClick={() => { setMfaToken(''); setMfaCode(''); setError(''); }}
                                >
                                    Use a different account
                                </button>
                            </form>
                        ) : (
                            <form onSubmit={handleSubmit} className="space-y-3">
                                <div>
                                    <label htmlFor="identifier" className="form-label">Username or Email</label>
                                    <input
                                        id="identifier"
                                        type="text"
                                        name="identifier"
                                        value={identifier}
                                        onChange={(event) => setIdentifier(event.target.value)}
                                        required
                                        autoFocus
                                        autoComplete="username"
                                        placeholder="you@school.com"
                                        className="login-input"
                                    />
                                </div>

                                <div>
                                    <div className="flex items-baseline justify-between mb-1">
                                        <label htmlFor="password" className="form-label mb-0">Password</label>
                                        <span className="text-xs text-gray-400">Forgot password? Contact your admin</span>
                                    </div>
                                    <div className="relative">
                                        <input
                                            id="password"
                                            name="password"
                                            required
                                            autoComplete="current-password"
                                            placeholder="••••••••"
                                            type={showPassword ? 'text' : 'password'}
                                            value={password}
                                            onChange={(event) => setPassword(event.target.value)}
                                            className="login-input pr-12"
                                        />
                                        <button
                                            type="button"
                                            onClick={() => setShowPassword(!showPassword)}
                                            className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-primary hover:text-primary-dark transition-colors"
                                            aria-label={showPassword ? 'Hide password' : 'Show password'}
                                            title={showPassword ? 'Hide password' : 'Show password'}
                                        >
                                            <Icon name={showPassword ? 'EyeSlash' : 'Eye'} size={18} />
                                        </button>
                                    </div>
                                </div>

                                <button type="submit" className="btn-primary w-full" disabled={busy}>
                                    {busy ? <Icon name="CircleNotch" size={18} className="animate-spin mr-2" /> : null}
                                    Sign in
                                </button>
                            </form>
                        )}
                    </div>

                    <p className="mt-4 pt-4 border-t border-border text-center text-xs text-muted">
                        School Management System
                    </p>
                </div>
            </div>
            )}
        </>
    );
}
