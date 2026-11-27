import Icon from './Icon';
import TableEmptyState from './TableEmptyState';
import TableIcon from './TableIcon';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import Layout from './Layout';
import PageHeader from './PageHeader';
import { StatTile } from './ModuleUi';
import { useAuth } from '../auth/AuthContext';
import { canWriteResource } from '../auth/permissions';
import useResourceLookups from '../hooks/useResourceLookups';
import { api } from '../lib/api';

export type Row = Record<string, unknown>;

export type FieldType =
    | 'text'
    | 'email'
    | 'number'
    | 'date'
    | 'time'
    | 'textarea'
    | 'select'
    | 'reference'
    | 'boolean';

export type Option = { value: string; label: string; group?: string };

export type ReferenceConfig = {
    resource: string;
    label: (row: Row) => string;
};

export type FieldConfig = {
    key: string;
    label: string;
    type?: FieldType;
    options?: Array<string | Option>;
    reference?: ReferenceConfig;
    required?: boolean;
    placeholder?: string;
    defaultValue?: string | number | boolean;
    wide?: boolean;
    /** Supplies options from the current form values (for dependent selects). */
    optionsFor?: (values: Record<string, unknown>) => Option[];
    /** Clears these fields when this field changes. */
    clears?: string[];
    /** Disables the field until this field has a value. */
    dependsOn?: string;
};

export type ColumnFormat = 'text' | 'badge' | 'currency' | 'number' | 'date';

export type ColumnConfig = {
    key: string;
    label: string;
    icon?: string;
    format?: ColumnFormat;
    /** Resolves a foreign-key cell to the label of the referenced resource. */
    reference?: ReferenceConfig;
    render?: (row: Row, lookups: Record<string, Option[]>) => ReactNode;
    className?: string;
};

export type StatConfig = {
    label: string;
    icon: string;
    value: (rows: Row[], total: number) => string | number;
    hint?: string;
    tone?: 'primary' | 'success' | 'warning' | 'info' | 'danger';
};

export type ResourceConfig = {
    resource: string;
    title: string;
    subtitle: string;
    icon: string;
    singular: string;
    columns: ColumnConfig[];
    fields: FieldConfig[];
    stats?: StatConfig[];
    searchPlaceholder?: string;
    pageSize?: number;
};

type Notice = { kind: 'success' | 'error'; message: string } | null;

export const asText = (value: unknown): string =>
    value === null || value === undefined ? '' : String(value);

export const optionLabel = (options: Option[] | undefined, value: unknown): string => {
    const id = asText(value);
    if (!id || !options) {
        return '';
    }
    return options.find((option) => option.value === id)?.label ?? '';
};

export const pretty = (value: string): string =>
    value
        .replace(/_/g, ' ')
        .replace(/\b\w/g, (character) => character.toUpperCase());

export const countWhere = (rows: Row[], predicate: (row: Row) => boolean): number =>
    rows.filter(predicate).length;

export const sumNumbers = (rows: Row[], key: string): number =>
    rows.reduce((total, row) => total + (Number(row[key]) || 0), 0);

export const uniqueCount = (rows: Row[], key: string): number =>
    new Set(rows.map((row) => asText(row[key])).filter(Boolean)).size;

export const money = (value: unknown): string =>
    new Intl.NumberFormat('en-TZ', {
        style: 'currency',
        currency: 'TZS',
        maximumFractionDigits: 0,
    }).format(Number(value) || 0);

export const badgeClass = (value: string): string => {
    const normalised = value.toUpperCase();
    if (['ACTIVE', 'PRESENT', 'PAID', 'COMPLETED', 'APPROVED', 'FULL_TIME'].includes(normalised)) {
        return 'badge-success';
    }
    if (
        ['PENDING', 'PARTIAL', 'LATE', 'ON_LEAVE', 'DRAFT', 'VOLUNTEER', 'CONTRACT'].includes(
            normalised,
        )
    ) {
        return 'badge-warning';
    }
    if (
        [
            'INACTIVE',
            'ABSENT',
            'OVERDUE',
            'SUSPENDED',
            'ARCHIVED',
            'CANCELLED',
            'GRADUATED',
            'EXCUSED',
        ].includes(normalised)
    ) {
        return 'badge-danger';
    }
    return 'badge-info';
};

export const errorMessage = (error: unknown): string =>
    error instanceof Error ? error.message : 'Something went wrong';

export const fieldOptions = (
    field: FieldConfig,
    lookups: Record<string, Option[]>,
    values: Record<string, unknown> = {},
): Option[] => {
    if (field.optionsFor) {
        return field.optionsFor(values);
    }
    if (field.reference) {
        return lookups[field.reference.resource] ?? [];
    }
    return (field.options ?? []).map((option) =>
        typeof option === 'string' ? { value: option, label: pretty(option) } : option,
    );
};

export const blankForm = (fields: FieldConfig[]): Record<string, unknown> => {
    const form: Record<string, unknown> = {};
    fields.forEach((field) => {
        form[field.key] = field.defaultValue ?? (field.type === 'boolean' ? false : '');
    });
    return form;
};

export const buildPayload = (
    fields: FieldConfig[],
    values: Record<string, unknown>,
): Record<string, unknown> => {
    const payload: Record<string, unknown> = {};
    fields.forEach((field) => {
        const raw = values[field.key];
        if (field.type === 'number') {
            payload[field.key] =
                raw === '' || raw === null || raw === undefined ? null : Number(raw);
        } else if (field.type === 'boolean') {
            payload[field.key] = Boolean(raw);
        } else {
            const text = asText(raw);
            payload[field.key] = text === '' ? null : text;
        }
    });
    return payload;
};

export const missingFields = (
    fields: FieldConfig[],
    values: Record<string, unknown>,
): FieldConfig[] =>
    fields.filter((field) => field.required && asText(values[field.key]).trim() === '');

function NoticeBanner({ notice }: { notice: Notice }) {
    if (!notice) {
        return null;
    }
    const success = notice.kind === 'success';
    return (
        <div className={`notice ${success ? 'success' : 'error'} mx-6 mt-4`}>
            <Icon name={success ? 'CheckCircle' : 'WarningCircle'} size={18} />
            <span>{notice.message}</span>
        </div>
    );
}

export default function ResourcePage({ config }: { config: ResourceConfig }) {
    const location = useLocation();
    const { user } = useAuth();
    const writable = canWriteResource(config.resource, user?.roles ?? []);
    const lookups = useResourceLookups(config.fields);
    const referencesByKey = useMemo(
        () =>
            new Map(
                config.fields
                    .filter((field) => field.reference)
                    .map((field) => [field.key, field.reference as ReferenceConfig]),
            ),
        [config.fields],
    );
    const [rows, setRows] = useState<Row[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [statsRows, setStatsRows] = useState<Row[]>([]);
    const [statsTotal, setStatsTotal] = useState(0);
    const [search, setSearch] = useState('');
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(true);
    const [notice, setNotice] = useState<Notice>(null);
    const [deleting, setDeleting] = useState<Row | null>(null);
    const [deleteBusy, setDeleteBusy] = useState(false);

    const pageSize = config.pageSize ?? 10;

    useEffect(() => {
        setPage(0);
        setSearch('');
        setQuery('');
        setNotice(null);
    }, [config.resource]);

    useEffect(() => {
        const state = location.state as { notice?: string } | null;
        if (state?.notice) {
            setNotice({ kind: 'success', message: state.notice });
            window.history.replaceState({}, '');
        }
    }, [location.state]);

    useEffect(() => {
        const timer = setTimeout(() => {
            setQuery(search);
            setPage(0);
        }, 250);
        return () => clearTimeout(timer);
    }, [search]);

    useEffect(() => {
        if (!notice) {
            return;
        }
        const timer = setTimeout(() => setNotice(null), 5000);
        return () => clearTimeout(timer);
    }, [notice]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await api.list<Row>(config.resource, page, pageSize, query);
            setRows(data.content);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
        } catch (error) {
            setNotice({ kind: 'error', message: errorMessage(error) });
        } finally {
            setLoading(false);
        }
    }, [config.resource, page, pageSize, query]);

    useEffect(() => {
        void load();
    }, [load]);

    const loadStats = useCallback(async () => {
        if (!config.stats?.length) {
            return;
        }
        try {
            const data = await api.list<Row>(config.resource, 0, 100, '');
            setStatsRows(data.content);
            setStatsTotal(data.totalElements);
        } catch {
            // Stats are optional; the table still works if they fail.
        }
    }, [config.resource, config.stats]);

    useEffect(() => {
        void loadStats();
    }, [loadStats, totalElements]);

    const remove = async () => {
        if (!deleting) {
            return;
        }
        setDeleteBusy(true);
        setNotice(null);
        try {
            await api.remove(config.resource, asText(deleting.id));
            setDeleting(null);
            setNotice({ kind: 'success', message: `${config.singular} deleted successfully` });
            await Promise.all([load(), loadStats()]);
        } catch (error) {
            setNotice({ kind: 'error', message: errorMessage(error) });
        } finally {
            setDeleteBusy(false);
        }
    };

    const referenceFor = (column: ColumnConfig): ReferenceConfig | undefined =>
        column.reference ?? referencesByKey.get(column.key);

    const displayValue = (row: Row, column: ColumnConfig): unknown => {
        const raw = row[column.key];
        const reference = referenceFor(column);
        return reference ? optionLabel(lookups[reference.resource], raw) : raw;
    };

    const renderCell = (row: Row, column: ColumnConfig) => {
        const value = displayValue(row, column);
        if (referenceFor(column)) {
            return value ? (
                <span className="font-medium text-gray-900">{asText(value)}</span>
            ) : (
                <span className="text-gray-400">—</span>
            );
        }
        if (column.render) {
            return column.render(row, lookups);
        }
        const raw = row[column.key];
        if (column.format === 'badge') {
            const badgeValue = typeof raw === 'boolean' ? (raw ? 'ACTIVE' : 'INACTIVE') : asText(raw);
            if (!badgeValue) {
                return <span className="text-gray-400">—</span>;
            }
            return <span className={`badge ${badgeClass(badgeValue)}`}>{pretty(badgeValue)}</span>;
        }
        if (column.format === 'currency') {
            return raw === null || raw === undefined ? (
                <span className="text-gray-400">—</span>
            ) : (
                money(raw)
            );
        }
        if (value === null || value === undefined || value === '') {
            return <span className="text-gray-400">—</span>;
        }
        return asText(value);
    };

    const from = totalElements === 0 ? 0 : page * pageSize + 1;
    const to = Math.min((page + 1) * pageSize, totalElements);
    const deleteLabel = deleting
        ? config.columns
              .slice(0, 2)
              .map((column) => asText(displayValue(deleting, column)))
              .filter(Boolean)
              .join(' · ')
        : '';

    return (
        <Layout>
            <div className="space-y-6">
                <PageHeader
                    title={config.title}
                    subtitle={config.subtitle}
                    icon={config.icon}
                    actions={writable && (
                        <Link to={`/${config.resource}/new`} className="btn-primary">
                            <Icon name="Plus" size={18} />
                            Add {config.singular}
                        </Link>
                    )}
                />

                {config.stats && config.stats.length > 0 && (
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
                        {config.stats.map((stat) => (
                            <StatTile
                                key={stat.label}
                                label={stat.label}
                                value={stat.value(statsRows, statsTotal)}
                                icon={stat.icon}
                                tone={stat.tone ?? 'primary'}
                                footer={stat.hint}
                            />
                        ))}
                    </div>
                )}

                <div className="re-table-wrap">
                    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-6 py-4">
                        <div>
                            <h2 className="font-semibold text-gray-900">{config.title} records</h2>
                            <p className="text-xs text-gray-500">
                                {totalElements} {totalElements === 1 ? 'record' : 'records'} in this module
                            </p>
                        </div>
                        <div className="relative w-full sm:w-72">
                            <Icon name="MagnifyingGlass" size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                            <input
                                className="form-input py-2 pl-9"
                                placeholder={config.searchPlaceholder ?? `Search ${config.title.toLowerCase()}...`}
                                value={search}
                                onChange={(event) => setSearch(event.target.value)}
                            />
                        </div>
                    </div>

                    <NoticeBanner notice={notice} />

                    <div className="overflow-x-auto">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    {config.columns.map((column) => (
                                        <th key={column.key}>
                                            <span className="table-col">
                                                <TableIcon label={column.label} />
                                                <span>{column.label}</span>
                                            </span>
                                        </th>
                                    ))}
                                    <th className="text-right">
                                        <span className="table-col">
                                            <TableIcon name="ListChecks" />
                                            <span>Actions</span>
                                        </span>
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {loading ? (
                                    <tr>
                                        <td colSpan={config.columns.length + 1} className="py-10 text-center text-gray-500">
                                            <Icon name="CircleNotch" size={18} className="animate-spin mr-2 text-primary" />
                                            Loading records...
                                        </td>
                                    </tr>
                                ) : rows.length === 0 ? (
                                    <TableEmptyState
                                        colSpan={config.columns.length + 1}
                                        message="No records found"
                                    >
                                        {writable && (
                                            <Link to={`/${config.resource}/new`} className="text-sm font-semibold text-primary hover:underline">
                                                Add the first {config.singular.toLowerCase()}
                                            </Link>
                                        )}
                                    </TableEmptyState>
                                ) : (
                                    rows.map((row) => (
                                        <tr key={asText(row.id)}>
                                            {config.columns.map((column) => {
                                                const value = displayValue(row, column);
                                                return (
                                                    <td
                                                        key={column.key}
                                                        className={column.className}
                                                        title={asText(value) || undefined}
                                                    >
                                                        <div className="table-cell-content">
                                                            <TableIcon label={column.label} value={value} />
                                                            <span className="min-w-0">{renderCell(row, column)}</span>
                                                        </div>
                                                    </td>
                                                );
                                            })}
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon name="ListChecks" />
                                                    <span className="min-w-0">
                                                        <div className="flex items-center justify-end gap-1">
                                                            <Link
                                                                to={`/${config.resource}/${asText(row.id)}`}
                                                                aria-label={`View ${config.singular}`}
                                                                title="View"
                                                                className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-primary hover:bg-primary-light"
                                                            >
                                                                <Icon name="Eye" size={14} />
                                                            </Link>
                                                            {writable && (
                                                                <>
                                                                    <Link
                                                                        to={`/${config.resource}/${asText(row.id)}/edit`}
                                                                        aria-label={`Edit ${config.singular}`}
                                                                        title="Edit"
                                                                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-primary hover:bg-primary-light"
                                                                    >
                                                                        <Icon name="Pencil" size={14} />
                                                                    </Link>
                                                                    {config.resource === 'reports' && (
                                                                        <button
                                                                            type="button"
                                                                            aria-label="Download report"
                                                                            title="Download CSV"
                                                                            className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-emerald-600 hover:bg-emerald-50"
                                                                            onClick={() => void api.downloadReport(asText(row.id))}
                                                                        >
                                                                            <Icon name="DownloadSimple" size={14} />
                                                                        </button>
                                                                    )}
                                                                    <button
                                                                        type="button"
                                                                        aria-label={`Delete ${config.singular}`}
                                                                        title="Delete"
                                                                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-red-500 hover:bg-red-50"
                                                                        onClick={() => setDeleting(row)}
                                                                    >
                                                                        <Icon name="Trash" size={14} />
                                                                    </button>
                                                                </>
                                                            )}
                                                        </div>
                                                    </span>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 px-6 py-4 text-sm text-gray-500">
                        <span>
                            Showing <strong className="text-gray-700">{from}</strong>–<strong className="text-gray-700">{to}</strong> of{' '}
                            <strong className="text-gray-700">{totalElements}</strong>
                        </span>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                className="btn-outline px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-40"
                                disabled={page === 0 || loading}
                                onClick={() => setPage((current) => current - 1)}
                            >
                                <Icon name="CaretLeft" size={18} /> Previous
                            </button>
                            <span className="px-2 text-xs">
                                Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
                            </span>
                            <button
                                type="button"
                                className="btn-outline px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-40"
                                disabled={page >= totalPages - 1 || loading}
                                onClick={() => setPage((current) => current + 1)}
                            >
                                Next <Icon name="CaretRight" size={18} />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {deleting && (
                <div className="modal-backdrop" role="presentation" onClick={() => setDeleting(null)}>
                    <div
                        className="modal-card"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="delete-dialog-title"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <button
                            type="button"
                            className="modal-close"
                            onClick={() => setDeleting(null)}
                            aria-label="Close"
                        >
                            <Icon name="X" size={18} />
                        </button>
                        <div className="modal-icon danger">
                            <Icon name="Warning" size={22} />
                        </div>
                        <h3 id="delete-dialog-title" className="modal-title">Delete {config.singular}?</h3>
                        <p className="modal-subtitle">This action cannot be undone.</p>
                        <div className="modal-body-box">
                            <strong>{deleteLabel || config.singular}</strong>
                            <p>
                                {deleteLabel
                                    ? 'This record will be permanently removed from your workspace.'
                                    : 'This record will be permanently removed.'}
                            </p>
                        </div>
                        <div className="modal-footer">
                            <button type="button" className="btn-outline" onClick={() => setDeleting(null)}>
                                Cancel
                            </button>
                            <button type="button" className="btn-danger" onClick={() => void remove()} disabled={deleteBusy}>
                                {deleteBusy ? (
                                    <Icon name="CircleNotch" size={18} className="animate-spin" />
                                ) : (
                                    <Icon name="Trash" size={18} />
                                )}
                                Delete
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </Layout>
    );
}
