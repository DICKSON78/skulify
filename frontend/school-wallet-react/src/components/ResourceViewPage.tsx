import { useEffect, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import Icon from './Icon';
import TableIcon from './TableIcon';
import FormSection from './FormSection';
import Layout from './Layout';
import PageHeader from './PageHeader';
import {
    asText,
    badgeClass,
    errorMessage,
    pretty,
    type FieldConfig,
    type ResourceConfig,
    type Row,
} from './ResourcePage';
import { groupFormFields } from '../lib/formSections';
import { useAuth } from '../auth/AuthContext';
import { canWriteResource } from '../auth/permissions';
import { api } from '../lib/api';

type ResourceViewPageProps = {
    config: ResourceConfig;
};

const displayValue = (field: FieldConfig, value: unknown): ReactNode => {
    if (field.type === 'boolean') {
        return value ? 'Yes' : 'No';
    }

    const text = asText(value);
    if (!text) {
        return <span className="text-gray-400">—</span>;
    }

    if (field.label.toLowerCase() === 'status') {
        return <span className={`badge ${badgeClass(text)}`}>{pretty(text)}</span>;
    }

    if (
        field.type === 'date' ||
        field.type === 'time' ||
        field.type === 'number' ||
        text.includes('@') ||
        /^\d{4}-\d{2}-\d{2}/.test(text)
    ) {
        return text;
    }

    const option = (field.options ?? [])
        .map((item) => (typeof item === 'string' ? { value: item, label: pretty(item) } : item))
        .find((item) => item.value === text);

    if (option) {
        return option.label;
    }

    return /^[A-Z0-9_ /-]+$/.test(text) ? pretty(text) : text;
};

export default function ResourceViewPage({ config }: ResourceViewPageProps) {
    const { id } = useParams();
    const { user } = useAuth();
    const writable = canWriteResource(config.resource, user?.roles ?? []);
    const [record, setRecord] = useState<Row | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        if (!id) {
            setError('No record selected.');
            setLoading(false);
            return;
        }

        let active = true;
        setLoading(true);
        setError('');
        api
            .get<Row>(config.resource, id)
            .then((data) => {
                if (active) {
                    setRecord(data);
                }
            })
            .catch((reason) => {
                if (active) {
                    setError(errorMessage(reason));
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [config.resource, id]);

    const recordLabel =
        (record && [asText(record.firstName), asText(record.lastName)].filter(Boolean).join(' ')) ||
        asText(record?.name) ||
        asText(record?.[config.columns[0]?.key ?? 'id']) ||
        config.singular;

    const sections = groupFormFields(config.fields);

    return (
        <Layout>
            <div className="space-y-6">
                <PageHeader
                    title={recordLabel}
                    subtitle={`${config.singular} details · read only`}
                    icon={config.icon}
                    backTo={`/${config.resource}`}
                    backLabel={`Back to ${config.title}`}
                    actions={
                        writable && record ? (
                            <Link
                                to={`/${config.resource}/${asText(record.id)}/edit`}
                                className="btn-primary"
                            >
                                <Icon name="Pencil" size={16} />
                                Edit {config.singular}
                            </Link>
                        ) : undefined
                    }
                />

                <div className="form-shell view-page-shell">
                    {error && (
                        <div className="form-alert" role="alert">
                            <Icon name="WarningCircle" size={18} />
                            <span>{error}</span>
                        </div>
                    )}

                    {loading ? (
                        <div className="form-loading">
                            <Icon name="CircleNotch" size={28} className="animate-spin text-primary" />
                            <span className="text-sm text-gray-500">Loading record...</span>
                        </div>
                    ) : record ? (
                        <>
                            <div className="form-shell-intro">
                                <div>
                                    <h2 className="form-shell-title">Record details</h2>
                                    <p className="form-shell-subtitle">
                                        Read only view of this {config.singular.toLowerCase()}
                                    </p>
                                </div>
                                <span className="badge badge-primary">
                                    <Icon name="Eye" size={16} className="mr-1" />
                                    Read only
                                </span>
                            </div>

                            {sections.map((section) => (
                                <FormSection
                                    key={section.id}
                                    icon={section.icon}
                                    title={section.title}
                                    subtitle={section.subtitle}
                                    tinted={section.tinted}
                                >
                                    <div className="view-grid">
                                        {section.fields.map((field) => (
                                            <div key={field.key} className="view-tile">
                                                <div className="view-tile-label">
                                                    <TableIcon
                                                        label={field.label}
                                                        value={record[field.key]}
                                                        size={14}
                                                        className="form-field-icon"
                                                    />
                                                    {field.label}
                                                </div>
                                                <div className="view-tile-value">
                                                    {displayValue(field, record[field.key])}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </FormSection>
                            ))}
                        </>
                    ) : (
                        <div className="form-loading">
                            <Icon name="Tray" size={28} className="text-gray-300" />
                            <span className="text-sm text-gray-500">Record not found</span>
                        </div>
                    )}
                </div>
            </div>
        </Layout>
    );
}
