import Icon from './Icon';
import type { ReactNode } from 'react';

export const text = (value: unknown): string =>
    value === null || value === undefined ? '' : String(value);

export const money = (value: unknown): string =>
    new Intl.NumberFormat('en-TZ', {
        style: 'currency',
        currency: 'TZS',
        maximumFractionDigits: 0,
    }).format(Number(value) || 0);

export function Notice({ error, success }: { error: string; success: string }) {
    if (error) {
        return <div className="notice error">{error}</div>;
    }
    if (success) {
        return <div className="notice success">{success}</div>;
    }
    return null;
}

const tones: Record<string, string> = {
    primary: 'tone-primary',
    success: 'tone-success',
    warning: 'tone-warning',
    danger: 'tone-danger',
    info: 'tone-info',
    violet: 'tone-violet',
};

export function StatTile({
    label,
    value,
    icon,
    tone = 'primary',
    footer = <>Current period snapshot</>,
}: {
    label: string;
    value: ReactNode;
    icon: string;
    tone?: string;
    footer?: ReactNode;
}) {
    return (
        <div className={`stat-card ${tones[tone] ?? tones.primary}`}>
            <div className="min-w-0 flex-1">
                <p className="stat-label truncate">{label}</p>
                <p className="stat-value">{value}</p>
                {footer && <div className="stat-footer">{footer}</div>}
            </div>
            <div className="stat-icon">
                <Icon name={icon} size={22} />
            </div>
        </div>
    );
}

export function FormCard({
    title,
    icon,
    children,
}: {
    title: string;
    icon: string;
    children: ReactNode;
}) {
    return (
        <section className="card mt-4">
            <div className="card-header">
                <h2 className="card-title">
                    <span className="icon-tile">
                        <Icon name={icon} size={18} />
                    </span>
                    {title}
                </h2>
            </div>
            {children}
        </section>
    );
}
