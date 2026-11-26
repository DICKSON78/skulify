import Icon from './Icon';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

type PageHeaderProps = {
    title: string;
    subtitle: string;
    icon: string;
    actions?: ReactNode;
    backTo?: string;
    backLabel?: string;
    badge?: ReactNode;
};

export default function PageHeader({
    title,
    subtitle,
    icon,
    actions,
    backTo,
    backLabel,
    badge,
}: PageHeaderProps) {
    return (
        <div className="page-heading">
            <div className="icon-tile">
                <Icon name={icon} size={20} />
            </div>
            <div className="min-w-0">
                <div className="page-title-row">
                    <h1 className="page-title">{title}</h1>
                    {badge}
                </div>
                <p className="page-subtitle">{subtitle}</p>
            </div>
            {(backTo || actions) && (
                <div className="page-header-actions">
                    {backTo && (
                        <Link
                            to={backTo}
                            className="page-back"
                            aria-label={backLabel ?? 'Back'}
                            title={backLabel ?? 'Back'}
                        >
                            <Icon name="ArrowLeft" size={18} />
                            {backLabel && <span className="page-back-label">{backLabel}</span>}
                        </Link>
                    )}
                    {actions}
                </div>
            )}
        </div>
    );
}
