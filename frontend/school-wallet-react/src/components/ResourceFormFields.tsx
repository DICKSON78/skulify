import { asText, fieldOptions, type FieldConfig, type Option } from './ResourcePage';
import FormSection from './FormSection';
import TableIcon from './TableIcon';
import { groupFormFields } from '../lib/formSections';

type ResourceFormFieldsProps = {
    fields: FieldConfig[];
    values: Record<string, unknown>;
    lookups: Record<string, Option[]>;
    onChange: (key: string, value: unknown) => void;
};

const renderOption = (option: Option) => (
    <option key={option.value} value={option.value}>
        {option.label}
    </option>
);

const renderOptions = (items: Option[]) => {
    if (!items.some((item) => item.group)) {
        return items.map(renderOption);
    }

    const groups = items.reduce<Array<{ label: string; options: Option[] }>>(
        (result, option) => {
            const label = option.group ?? '';
            const group = result.find((entry) => entry.label === label);
            if (group) {
                group.options.push(option);
            } else {
                result.push({ label, options: [option] });
            }
            return result;
        },
        [],
    );

    return groups.map((group) =>
        group.label ? (
            <optgroup key={group.label} label={group.label}>
                {group.options.map(renderOption)}
            </optgroup>
        ) : (
            group.options.map(renderOption)
        ),
    );
};

export default function ResourceFormFields({
    fields,
    values,
    lookups,
    onChange,
}: ResourceFormFieldsProps) {
    const sections = groupFormFields(fields);

    return (
        <>
            {sections.map((section) => (
                <FormSection
                    key={section.id}
                    icon={section.icon}
                    title={section.title}
                    subtitle={section.subtitle}
                    tinted={section.tinted}
                >
                    <div className="form-grid">
                        {section.fields.map((field) => {
                            const options = fieldOptions(field, lookups, values);
                            const inputId = `field-${field.key}`;
                            const fieldIcon = (
                                <TableIcon
                                    label={field.label}
                                    value={values[field.key]}
                                    size={16}
                                    className="form-field-icon"
                                />
                            );
                            const dependentValue = field.dependsOn
                                ? asText(values[field.dependsOn])
                                : '';
                            const disabled = Boolean(field.dependsOn && !dependentValue);
                            const parentLabel = field.dependsOn
                                ? fields
                                      .find((item) => item.key === field.dependsOn)
                                      ?.label.toLowerCase()
                                : undefined;

                            if (field.type === 'boolean') {
                                return (
                                    <label
                                        key={field.key}
                                        htmlFor={inputId}
                                        className="form-check"
                                    >
                                        <input
                                            id={inputId}
                                            type="checkbox"
                                            checked={Boolean(values[field.key])}
                                            onChange={(event) =>
                                                onChange(field.key, event.target.checked)
                                            }
                                            className="form-check-input"
                                        />
                                        {fieldIcon}
                                        <span className="text-sm font-medium text-gray-800">
                                            {field.label}
                                            {field.required && (
                                                <span className="ml-0.5 text-red-500">*</span>
                                            )}
                                        </span>
                                    </label>
                                );
                            }

                            const isSelect =
                                field.type === 'select' ||
                                (field.type === 'reference' && options.length > 0);
                            const isTextarea = field.type === 'textarea';
                            const controlClassName = [
                                isTextarea ? 'form-textarea' : isSelect ? 'form-select' : 'form-input',
                                'with-icon',
                            ].join(' ');

                            const control = isTextarea ? (
                                <textarea
                                    id={inputId}
                                    rows={4}
                                    className={controlClassName}
                                    placeholder={field.placeholder}
                                    value={asText(values[field.key])}
                                    onChange={(event) => onChange(field.key, event.target.value)}
                                />
                            ) : isSelect ? (
                                <select
                                    id={inputId}
                                    className={controlClassName}
                                    value={asText(values[field.key])}
                                    disabled={disabled}
                                    onChange={(event) => onChange(field.key, event.target.value)}
                                >
                                    <option value="">
                                        {field.placeholder ?? `Select ${field.label.toLowerCase()}`}
                                    </option>
                                    {renderOptions(options)}
                                </select>
                            ) : (
                                <input
                                    id={inputId}
                                    type={
                                        field.type === 'number'
                                            ? 'number'
                                            : field.type === 'date'
                                              ? 'date'
                                              : field.type === 'time'
                                                ? 'time'
                                                : field.type === 'email'
                                                  ? 'email'
                                                  : 'text'
                                    }
                                    step={field.type === 'number' ? 'any' : undefined}
                                    className={controlClassName}
                                    placeholder={
                                        field.placeholder ??
                                        (field.reference
                                            ? `Enter ${field.label.toLowerCase()} ID (or add records first)`
                                            : undefined)
                                    }
                                    value={asText(values[field.key])}
                                    onChange={(event) => onChange(field.key, event.target.value)}
                                />
                            );

                            return (
                                <div
                                    key={field.key}
                                    className={`form-field${field.wide || isTextarea ? ' form-field--wide' : ''}`}
                                >
                                    <label className="form-label" htmlFor={inputId}>
                                        {field.label}
                                        {field.required && (
                                            <span className="ml-0.5 text-red-500" aria-hidden="true">
                                                *
                                            </span>
                                        )}
                                    </label>
                                    <div className="form-icon-wrapper">
                                        <span
                                            className={`form-icon${isTextarea ? ' form-icon--top' : ''}`}
                                            aria-hidden="true"
                                        >
                                            {fieldIcon}
                                        </span>
                                        {control}
                                    </div>
                                    {disabled && (
                                        <p className="form-hint">
                                            Select {parentLabel ?? field.dependsOn} first.
                                        </p>
                                    )}
                                    {field.type === 'reference' && options.length === 0 && (
                                        <p className="form-hint">
                                            No {field.reference?.resource} available yet — paste the
                                            ID manually.
                                        </p>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </FormSection>
            ))}
        </>
    );
}
