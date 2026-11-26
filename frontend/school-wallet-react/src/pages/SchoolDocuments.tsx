import Icon from '../components/Icon';
import TableEmptyState from '../components/TableEmptyState';
import TableIcon from '../components/TableIcon';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import { FormCard, Notice, StatTile } from '../components/ModuleUi';
import { useAuth } from '../auth/AuthContext';
import { canWriteResource } from '../auth/permissions';
import { api, documentsApi } from '../lib/api';
import { asText, badgeClass, errorMessage, pretty } from '../components/ResourcePage';

type Row = Record<string, unknown>;
type NoticeState = { kind: 'success' | 'error'; message: string } | null;

const CATEGORIES = ['GENERAL', 'POLICY', 'ACADEMIC', 'HR', 'FINANCE', 'STUDENT', 'OTHER'];

const fileSize = (value: unknown): string => {
    const bytes = Number(value) || 0;
    if (bytes >= 1024 * 1024) {
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }
    if (bytes >= 1024) {
        return `${Math.round(bytes / 1024)} KB`;
    }
    return `${bytes} B`;
};

export default function SchoolDocuments() {
    const { user } = useAuth();
    const writable = canWriteResource('school-documents', user?.roles ?? []);
    const [rows, setRows] = useState<Row[]>([]);
    const [total, setTotal] = useState(0);
    const [search, setSearch] = useState('');
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(true);
    const [uploading, setUploading] = useState(false);
    const [notice, setNotice] = useState<NoticeState>(null);
    const [file, setFile] = useState<File | null>(null);
    const [form, setForm] = useState({
        title: '',
        category: 'GENERAL',
        description: '',
        documentDate: new Date().toISOString().slice(0, 10),
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await api.list<Row>('school-documents', 0, 100, query);
            setRows(data.content);
            setTotal(data.totalElements);
        } catch (error) {
            setNotice({ kind: 'error', message: errorMessage(error) });
        } finally {
            setLoading(false);
        }
    }, [query]);

    useEffect(() => {
        void load();
    }, [load]);

    useEffect(() => {
        const timer = setTimeout(() => {
            setQuery(search);
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

    const upload = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!file) {
            setNotice({ kind: 'error', message: 'Choose a file to upload' });
            return;
        }
        setUploading(true);
        setNotice(null);
        try {
            const body = new FormData();
            body.append('file', file);
            body.append('title', form.title);
            body.append('category', form.category);
            body.append('description', form.description);
            body.append('documentDate', form.documentDate);
            await documentsApi.upload<Row>(body);
            setFile(null);
            setForm({
                title: '',
                category: 'GENERAL',
                description: '',
                documentDate: new Date().toISOString().slice(0, 10),
            });
            const input = document.getElementById('school-document-file') as HTMLInputElement | null;
            if (input) {
                input.value = '';
            }
            setNotice({ kind: 'success', message: 'Document uploaded successfully' });
            await load();
        } catch (error) {
            setNotice({ kind: 'error', message: errorMessage(error) });
        } finally {
            setUploading(false);
        }
    };

    const remove = async (row: Row) => {
        const title = asText(row.title) || 'this document';
        if (!window.confirm(`Delete ${title}? The stored file will also be removed.`)) {
            return;
        }
        setNotice(null);
        try {
            await api.remove('school-documents', asText(row.id));
            setNotice({ kind: 'success', message: 'Document deleted successfully' });
            await load();
        } catch (error) {
            setNotice({ kind: 'error', message: errorMessage(error) });
        }
    };

    const download = async (row: Row) => {
        setNotice(null);
        try {
            await documentsApi.download(asText(row.id));
        } catch (error) {
            setNotice({ kind: 'error', message: errorMessage(error) });
        }
    };

    const active = rows.filter((row) => asText(row.status) === 'ACTIVE').length;
    const policies = rows.filter((row) => asText(row.category) === 'POLICY').length;
    const withFiles = rows.filter((row) => Boolean(asText(row.fileName))).length;

    return (
        <Layout>
            <div className="space-y-6">
                <PageHeader
                    title="School Documents"
                    subtitle="Upload, organise and download school records with role-based access"
                    icon="FileArrowUp"
                />

                <Notice error={notice?.kind === 'error' ? notice.message : ''} success={notice?.kind === 'success' ? notice.message : ''} />

                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    <StatTile
                        label="Documents"
                        value={total}
                        icon="FileArrowUp"
                        tone="primary"
                        footer="Records in the document store"
                    />
                    <StatTile
                        label="Active"
                        value={active}
                        icon="CheckCircle"
                        tone="success"
                        footer="Available to authorised staff"
                    />
                    <StatTile
                        label="Policies"
                        value={policies}
                        icon="Stack"
                        tone="warning"
                        footer="Official policy documents"
                    />
                    <StatTile
                        label="With files"
                        value={withFiles}
                        icon="DownloadSimple"
                        tone="info"
                        footer="Ready for download"
                    />
                </div>

                {writable && (
                    <FormCard title="Upload a document" icon="FileArrowUp">
                        <form className="form-grid p-6" onSubmit={(event) => void upload(event)}>
                            <div className="form-field">
                                <label className="form-label" htmlFor="document-title">Title *</label>
                                <input
                                    id="document-title"
                                    className="form-input"
                                    value={form.title}
                                    onChange={(event) => setForm({ ...form, title: event.target.value })}
                                    placeholder="e.g. School rules and regulations"
                                    required
                                />
                            </div>
                            <div className="form-field">
                                <label className="form-label" htmlFor="document-category">Category</label>
                                <select
                                    id="document-category"
                                    className="form-select"
                                    value={form.category}
                                    onChange={(event) => setForm({ ...form, category: event.target.value })}
                                >
                                    {CATEGORIES.map((category) => (
                                        <option key={category} value={category}>{pretty(category)}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="form-field">
                                <label className="form-label" htmlFor="document-date">Document date</label>
                                <input
                                    id="document-date"
                                    className="form-input"
                                    type="date"
                                    value={form.documentDate}
                                    onChange={(event) => setForm({ ...form, documentDate: event.target.value })}
                                />
                            </div>
                            <div className="form-field">
                                <label className="form-label" htmlFor="school-document-file">File *</label>
                                <input
                                    id="school-document-file"
                                    className="form-input"
                                    type="file"
                                    onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                                    required
                                />
                            </div>
                            <div className="form-field form-field--wide">
                                <label className="form-label" htmlFor="document-description">Description</label>
                                <textarea
                                    id="document-description"
                                    className="form-textarea"
                                    rows={2}
                                    value={form.description}
                                    onChange={(event) => setForm({ ...form, description: event.target.value })}
                                    placeholder="Short summary of the document"
                                />
                            </div>
                            <div className="form-actions form-field--wide">
                                <button type="submit" className="btn-primary" disabled={uploading}>
                                    <Icon name={uploading ? 'CircleNotch' : 'UploadSimple'} size={18} className={uploading ? 'animate-spin' : ''} />
                                    {uploading ? 'Uploading...' : 'Upload document'}
                                </button>
                            </div>
                        </form>
                    </FormCard>
                )}

                <div className="re-table-wrap">
                    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-6 py-4">
                        <div>
                            <h2 className="font-semibold text-gray-900">Document records</h2>
                            <p className="text-xs text-gray-500">
                                {total} {total === 1 ? 'document' : 'documents'} in the school store
                            </p>
                        </div>
                        <div className="relative w-full sm:w-72">
                            <Icon name="MagnifyingGlass" size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                            <input
                                className="form-input py-2 pl-9"
                                placeholder="Search documents..."
                                value={search}
                                onChange={(event) => setSearch(event.target.value)}
                            />
                        </div>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="re-table">
                            <thead>
                                <tr>
                                    {['Reference', 'Title', 'Category', 'Document date', 'File', 'Size', 'Status'].map((label) => (
                                        <th key={label}>
                                            <span className="table-col">
                                                <TableIcon label={label} />
                                                <span>{label}</span>
                                            </span>
                                        </th>
                                    ))}
                                    <th className="text-right">
                                        <span className="table-col">
                                            <TableIcon label="Actions" />
                                            <span>Actions</span>
                                        </span>
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {loading ? (
                                    <tr>
                                        <td colSpan={8} className="py-10 text-center text-gray-500">
                                            <Icon name="CircleNotch" size={18} className="animate-spin mr-2 text-primary" />
                                            Loading documents...
                                        </td>
                                    </tr>
                                ) : rows.length === 0 ? (
                                    <TableEmptyState colSpan={8} message="No documents found" icon="FileArrowUp">
                                        {writable ? 'Upload the first document above.' : 'No documents are available for your role.'}
                                    </TableEmptyState>
                                ) : (
                                    rows.map((row) => (
                                        <tr key={asText(row.id)}>
                                            {[
                                                asText(row.reference),
                                                asText(row.title),
                                                asText(row.category),
                                                asText(row.documentDate),
                                                asText(row.fileName),
                                                fileSize(row.fileSize),
                                            ].map((value, index) => (
                                                <td key={index} title={value || undefined}>
                                                    <div className="table-cell-content">
                                                        <TableIcon
                                                            label={['Reference', 'Title', 'Category', 'Document date', 'File', 'Size'][index]}
                                                            value={value}
                                                        />
                                                        <span className="min-w-0">{value || '—'}</span>
                                                    </div>
                                                </td>
                                            ))}
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Status" value={row.status} />
                                                    <span className="min-w-0">
                                                        {asText(row.status) ? (
                                                            <span className={`badge ${badgeClass(asText(row.status))}`}>
                                                                {pretty(asText(row.status))}
                                                            </span>
                                                        ) : (
                                                            '—'
                                                        )}
                                                    </span>
                                                </div>
                                            </td>
                                            <td>
                                                <div className="table-cell-content">
                                                    <TableIcon label="Actions" />
                                                    <span className="min-w-0">
                                                        <div className="flex items-center justify-end gap-1">
                                                            <button
                                                                type="button"
                                                                aria-label={`Download ${asText(row.title)}`}
                                                                title="Download"
                                                                className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-primary hover:bg-primary-light"
                                                                onClick={() => void download(row)}
                                                            >
                                                                <Icon name="DownloadSimple" size={14} />
                                                            </button>
                                                            {writable && (
                                                                <button
                                                                    type="button"
                                                                    aria-label={`Delete ${asText(row.title)}`}
                                                                    title="Delete"
                                                                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-red-500 hover:bg-red-50"
                                                                    onClick={() => void remove(row)}
                                                                >
                                                                    <Icon name="Trash" size={14} />
                                                                </button>
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
                </div>
            </div>
        </Layout>
    );
}
