import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from 'react';
import {
    authApi,
    schoolStore,
    tokenStore,
    type AuthUser,
    type LoginResult,
    type SchoolOption,
} from '../lib/api';
import { canAccess } from './permissions';

type AuthContextValue = {
    user: AuthUser | null;
    loading: boolean;
    schools: SchoolOption[];
    selectedSchoolId: string | null;
    selectSchool: (schoolId: string) => void;
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
    const [schools, setSchools] = useState<SchoolOption[]>([]);
    const [selectedSchoolId, setSelectedSchoolId] = useState<string | null>(() => schoolStore.selected());

    const clearSchoolContext = useCallback(() => {
        schoolStore.clear();
        setSchools([]);
        setSelectedSchoolId(null);
    }, []);

    const loadSchoolContext = useCallback(async () => {
        try {
            const options = await authApi.schools();
            setSchools(options);
            const stored = schoolStore.selected();
            const selected = options.some((school) => school.id === stored)
                ? stored
                : options[0]?.id ?? null;
            if (selected) {
                schoolStore.save(selected);
            } else {
                schoolStore.clear();
            }
            setSelectedSchoolId(selected);
        } catch {
            clearSchoolContext();
        }
    }, [clearSchoolContext]);

    const loadMe = useCallback(async () => {
        if (!tokenStore.access()) {
            setUser(null);
            clearSchoolContext();
            setLoading(false);
            return;
        }
        try {
            const loaded = await authApi.me();
            setUser(loaded);
            if (loaded.roles.includes('SUPER_ADMIN')) {
                await loadSchoolContext();
            } else {
                clearSchoolContext();
            }
        } catch {
            tokenStore.clear();
            setUser(null);
            clearSchoolContext();
        } finally {
            setLoading(false);
        }
    }, [clearSchoolContext, loadSchoolContext]);

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
        clearSchoolContext();
    }, [clearSchoolContext]);

    const selectSchool = useCallback((schoolId: string) => {
        schoolStore.save(schoolId);
        setSelectedSchoolId(schoolId);
    }, []);

    const value = useMemo<AuthContextValue>(
        () => ({
            user,
            loading,
            schools,
            selectedSchoolId,
            selectSchool,
            login,
            completeMfa,
            logout,
            hasRole: (role) => Boolean(user?.roles.includes(role)),
            can: (path) => canAccess(path, user?.roles ?? []),
        }),
        [user, loading, schools, selectedSchoolId, selectSchool, login, completeMfa, logout],
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
