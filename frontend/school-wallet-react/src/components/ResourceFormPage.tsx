import Icon from './Icon';
import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Layout from './Layout';
import PageHeader from './PageHeader';
import ResourceFormFields from './ResourceFormFields';
import useResourceLookups from '../hooks/useResourceLookups';
import { useAuth } from '../auth/AuthContext';
import { canWriteResource } from '../auth/permissions';
import {
    blankForm,
    buildPayload,
    errorMessage,
    missingFields,
    type ResourceConfig,
    type Row,
} from './ResourcePage';
import { api } from '../lib/api';

type ResourceFormPageProps = {
    config: ResourceConfig;
    mode: 'create' | 'edit';
};

export default function ResourceFormPage({ config, mode }: ResourceFormPageProps) {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user, loading: authLoading } = useAuth();
    const writable = canWriteResource(config.resource, user?.roles ?? []);
    const [values, setValues] = useState<Record<string, unknown>>(() => blankForm(config.fields));
    const [loading, setLoading] = useState(mode === 'edit');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const lookups = useResourceLookups(config.fields);

    useEffect(() => {
        if (!authLoading && !writable) {
            navigate(`/${config.resource}`, { replace: true });
        }
    }, [authLoading, writable, navigate, config.resource]);

    useEffect(() => {
        setValues(blankForm(config.fields));
        setError('');

        if (mode !== 'edit' || !id) {
            setLoading(false);
            return;
        }

        let active = true;
        setLoading(true);
        api
            .get<Row>(config.resource, id)
            .then((record) => {
                if (!active) {
                    return;
                }
                const next: Record<string, unknown> = {};
                config.fields.forEach((field) => {
                    next[field.key] =
                        record[field.key] ?? (field.type === 'boolean' ? false : '');
                });
                setValues(next);
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
    }, [config.fields, config.resource, id, mode]);

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        const missing = missingFields(config.fields, values);
        if (missing.length > 0) {
            setError(`Please fill: ${missing.map((field) => field.label).join(', ')}`);
            return;
        }

        setSaving(true);
        setError('');
        try {
            const payload = buildPayload(config.fields, values);
            if (mode === 'create') {
                await api.create(config.resource, payload);
            } else if (id) {
                await api.update(config.resource, id, payload);
            }
            navigate(`/${config.resource}`, {
                state: {
                    notice: `${config.singular} ${mode === 'create' ? 'added' : 'updated'} successfully`,
                },
            });
        } catch (reason) {
            setError(errorMessage(reason));
        } finally {
            setSaving(false);
        }
    };

    const setField = (key: string, value: unknown) => {
        const field = config.fields.find((item) => item.key === key);
        setValues((previous) => {
            const next = { ...previous, [key]: value };
            field?.clears?.forEach((dependentKey) => {
                next[dependentKey] = '';
            });
            return next;
        });
    };

    return (
        <Layout>
            <div className="space-y-6">
                <PageHeader
                    title={`${mode === 'create' ? 'Add' : 'Edit'} ${config.singular}`}
                    subtitle={
                        mode === 'create'
                            ? `Create a new ${config.singular.toLowerCase()} record`
                            : `Update ${config.singular.toLowerCase()} details`
                    }
                    icon={config.icon}
                    backTo={`/${config.resource}`}
                    backLabel={`Back to ${config.title}`}
                />

                <form className="form-shell" onSubmit={submit}>
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
                    ) : (
                        <>
                            <div className="form-shell-intro">
                                <div>
                                    <h2 className="form-shell-title">
                                        {mode === 'create' ? 'New record details' : 'Record details'}
                                    </h2>
                                    <p className="form-shell-subtitle">
                                        Fields marked <span className="text-red-500">*</span> are required
                                    </p>
                                </div>
                                <span className="badge badge-primary">
                                    <Icon name={config.icon} size={16} className="mr-1" />
                                    {config.title}
                                </span>
                            </div>

                            <ResourceFormFields
                                fields={config.fields}
                                values={values}
                                lookups={lookups}
                                onChange={setField}
                            />

                            <div className="form-actions">
                                <Link to={`/${config.resource}`} className="btn-outline">
                                    Cancel
                                </Link>
                                <button type="submit" className="btn-primary" disabled={saving}>
                                    {saving ? (
                                        <Icon name="CircleNotch" size={18} className="animate-spin" />
                                    ) : (
                                        <Icon name="FloppyDisk" size={18} />
                                    )}
                                    {mode === 'create' ? 'Create record' : 'Save changes'}
                                </button>
                            </div>
                        </>
                    )}
                </form>
            </div>
        </Layout>
    );
}
