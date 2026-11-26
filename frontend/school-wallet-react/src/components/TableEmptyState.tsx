import type { ReactNode } from 'react';
import Icon, { type IconName } from './Icon';

type TableEmptyStateProps = {
    colSpan: number;
    message: ReactNode;
    icon?: IconName | string;
    children?: ReactNode;
};

/** Centered table empty state: icon on top, message below, optional action last. */
export default function TableEmptyState({
    colSpan,
    message,
    icon = 'Tray',
    children,
}: TableEmptyStateProps) {
    return (
        <tr>
            <td colSpan={colSpan} className="py-12 text-center">
                <div className="table-empty">
                    <Icon name={icon} size={32} className="table-empty-icon" />
                    <div className="table-empty-message">{message}</div>
                    {children && <div className="table-empty-action">{children}</div>}
                </div>
            </td>
        </tr>
    );
}
