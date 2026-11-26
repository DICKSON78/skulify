import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from 'react';
import { authApi, tokenStore, type AuthUser, type LoginResult } from '../lib/api';
import { canAccess } from './permissions';

type AuthContextValue = {
    user: AuthUser | null;
    loading: boolean;
    login: (identifier: string, password: string) => Promise<LoginResult>;
    completeMfa: (token: string, code: string) => Promise<void>;
    logout: () => Promise<void>;
    hasRole: (role: string) => boolean;
    can: (path: string) => boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [loading, setLoading] = useState(true);

    const loadMe = useCallback(async () => {
        if (!tokenStore.access()) {
            setUser(null);
            setLoading(false);
            return;
        }
        try {
            setUser(await authApi.me());
        } catch {
            tokenStore.clear();
            setUser(null);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadMe();
    }, [loadMe]);

    const login = useCallback(async (identifier: string, password: string) => {
        const result = await authApi.login(identifier, password);
        if (result.accessToken && result.refreshToken) {
            tokenStore.save(result.accessToken, result.refreshToken);
            await loadMe();
        }
        return result;
    }, [loadMe]);

    const completeMfa = useCallback(
        async (token: string, code: string) => {
            const result = await authApi.verifyMfa(token, code);
            if (result.accessToken && result.refreshToken) {
                tokenStore.save(result.accessToken, result.refreshToken);
                await loadMe();
            }
        },
        [loadMe],
    );

    const logout = useCallback(async () => {
        await authApi.logout();
        setUser(null);
    }, []);

    const value = useMemo<AuthContextValue>(
        () => ({
            user,
            loading,
            login,
            completeMfa,
            logout,
            hasRole: (role) => Boolean(user?.roles.includes(role)),
            can: (path) => canAccess(path, user?.roles ?? []),
        }),
        [user, loading, login, completeMfa, logout],
    );

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used inside AuthProvider');
    }
    return context;
}
