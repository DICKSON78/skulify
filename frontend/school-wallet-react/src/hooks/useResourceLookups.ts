import { useCallback, useEffect, useMemo, useState } from 'react';
import { asText, type FieldConfig, type Option, type ReferenceConfig, type Row } from '../components/ResourcePage';
import { api } from '../lib/api';

/** Loads dropdown options for every reference field on a resource form. */
export default function useResourceLookups(fields: FieldConfig[]): Record<string, Option[]> {
    const [lookups, setLookups] = useState<Record<string, Option[]>>({});

    const references = useMemo(() => {
        const seen = new Set<string>();
        const result: ReferenceConfig[] = [];
        fields.forEach((field) => {
            if (field.reference && !seen.has(field.reference.resource)) {
                seen.add(field.reference.resource);
                result.push(field.reference);
            }
        });
        return result;
    }, [fields]);

    const load = useCallback(async () => {
        if (references.length === 0) {
            setLookups({});
            return;
        }
        const pairs = await Promise.all(
            references.map(async (reference) => {
                try {
                    const data = await api.list<Row>(reference.resource, 0, 100, '');
                    return [
                        reference.resource,
                        data.content.map((row) => ({
                            value: asText(row.id),
                            label: reference.label(row),
                        })),
                    ] as [string, Option[]];
                } catch {
                    return [reference.resource, []] as [string, Option[]];
                }
            }),
        );
        setLookups(Object.fromEntries(pairs));
    }, [references]);

    useEffect(() => {
        void load();
    }, [load]);

    return lookups;
}
