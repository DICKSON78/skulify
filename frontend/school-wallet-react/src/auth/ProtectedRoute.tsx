import Icon from '../components/Icon';
import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';

export default function ProtectedRoute({
    path,
    children,
}: {
    path: string;
    children: ReactNode;
}) {
    const { user, loading, can } = useAuth();
    const location = useLocation();

    if (loading) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-white text-primary">
                <Icon name="CircleNotch" size={28} className="animate-spin" />
            </div>
        );
    }
    if (!user) {
        return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    }
    if (!can(path)) {
        return <Navigate to="/dashboard" replace />;
    }
    return <>{children}</>;
}
