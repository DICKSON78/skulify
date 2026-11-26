import type { ReactNode } from 'react';
import Icon, { type IconName } from './Icon';

type FormSectionProps = {
    icon: IconName;
    title: string;
    subtitle?: string;
    tinted?: boolean;
    children: ReactNode;
};

/** A titled, icon-led block inside a form shell (create/edit/show shared pattern). */
export default function FormSection({
    icon,
    title,
    subtitle,
    tinted = false,
    children,
}: FormSectionProps) {
    return (
        <section className={`form-section${tinted ? ' form-section--tinted' : ''}`}>
            <div className="form-section-heading">
                <span className="form-section-icon">
                    <Icon name={icon} size={20} />
                </span>
                <div className="min-w-0">
                    <h2 className="form-section-title">{title}</h2>
                    {subtitle && <p className="form-section-subtitle">{subtitle}</p>}
                </div>
            </div>
            {children}
        </section>
    );
}
