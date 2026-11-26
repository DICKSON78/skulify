import type { FieldConfig } from '../components/ResourcePage';
import type { IconName } from '../components/Icon';

export type FormSectionId =
    | 'identity'
    | 'placement'
    | 'location'
    | 'schedule'
    | 'people'
    | 'contact'
    | 'financial'
    | 'details'
    | 'settings';

export type FormSectionData = {
    id: FormSectionId;
    icon: IconName;
    title: string;
    subtitle: string;
    fields: FieldConfig[];
    tinted: boolean;
};

type SectionDefinition = Omit<FormSectionData, 'fields' | 'tinted'>;

const SECTION_DEFINITIONS: SectionDefinition[] = [
    {
        id: 'identity',
        icon: 'IdentificationCard',
        title: 'Identity',
        subtitle: 'The core details that identify this record.',
    },
    {
        id: 'placement',
        icon: 'Buildings',
        title: 'School placement',
        subtitle: 'Classes, subjects and where this record belongs.',
    },
    {
        id: 'location',
        icon: 'MapPin',
        title: 'Location',
        subtitle: 'Mkoa, wilaya na mtaa for Tanzania bara and Zanzibar.',
    },
    {
        id: 'schedule',
        icon: 'CalendarBlank',
        title: 'Dates & schedule',
        subtitle: 'Dates, times, terms and ordering.',
    },
    {
        id: 'people',
        icon: 'UsersThree',
        title: 'People & guardians',
        subtitle: 'Staff, guardians and other responsible people.',
    },
    {
        id: 'contact',
        icon: 'EnvelopeOpen',
        title: 'Contact & address',
        subtitle: 'How this record can be reached.',
    },
    {
        id: 'financial',
        icon: 'Wallet',
        title: 'Financial details',
        subtitle: 'Amounts, payments and account information.',
    },
    {
        id: 'details',
        icon: 'PencilSimple',
        title: 'Additional details',
        subtitle: 'Notes and other useful context.',
    },
    {
        id: 'settings',
        icon: 'SlidersHorizontal',
        title: 'Record settings',
        subtitle: 'Status and visibility of this record.',
    },
];

const normalise = (value: string): string =>
    value
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, ' ')
        .trim()
        .replace(/\s+/g, ' ');

const sectionForField = (field: FieldConfig): FormSectionId => {
    const text = normalise(`${field.key} ${field.label}`);

    if (/date of birth|dateofbirth|\bdob\b/.test(text)) {
        return 'identity';
    }
    if (/guardian|parent|head teacher|headteacher|recorded by|recordedby/.test(text)) {
        return 'people';
    }
    if (/region|mkoa|district|wilaya|street|mtaa/.test(text)) {
        return 'location';
    }
    if (/email|phone|address|digital url|digitalurl|website|url/.test(text)) {
        return 'contact';
    }
    if (/code|isbn|reference|ref|number|no$/.test(text)) {
        return 'identity';
    }
    if (
        /amount|budget|salary|fee|paid|cost|price|balance|bank|account|tin|nssf|copies/.test(
            text,
        )
    ) {
        return 'financial';
    }
    if (
        /\bdate\b|\btime\b|\bday\b|\bterm\b|\bstart\b|\bend\b|last generated|lastgenerated|hire|admission|\bdue\b|sort order|sortorder|\border\b|check in|checkin/.test(
            text,
        )
    ) {
        return 'schedule';
    }
    if (/status|active|format/.test(text)) {
        return 'settings';
    }
    if (/description|remarks|note|comment/.test(text)) {
        return 'details';
    }
    if (
        /student|teacher|class|subject|department|room|location|section|grade|specialization|employment|report|exam|category|type/.test(
            text,
        )
    ) {
        return 'placement';
    }
    if (/name|gender|title|author/.test(text)) {
        return 'identity';
    }
    return 'details';
};

/** Groups fields into a small number of icon-led sections for create/edit/show views. */
export const groupFormFields = (fields: FieldConfig[]): FormSectionData[] => {
    const grouped = new Map<FormSectionId, FieldConfig[]>();
    fields.forEach((field) => {
        const id = sectionForField(field);
        const bucket = grouped.get(id) ?? [];
        bucket.push(field);
        grouped.set(id, bucket);
    });

    const sections: FormSectionData[] = SECTION_DEFINITIONS.map((definition) => ({
        ...definition,
        fields: grouped.get(definition.id) ?? [],
        tinted: false,
    }));

    const byId = (id: FormSectionId) => sections.find((section) => section.id === id);
    const remove = (id: FormSectionId) => {
        const index = sections.findIndex((section) => section.id === id);
        if (index >= 0) {
            sections.splice(index, 1);
        }
    };

    // Keep short forms compact: merge related one-field sections instead of
    // showing a heading for a single input.
    const placement = byId('placement');
    const schedule = byId('schedule');
    if (placement && schedule && placement.fields.length + schedule.fields.length <= 4) {
        placement.fields = [...placement.fields, ...schedule.fields];
        placement.title = 'Placement & schedule';
        placement.icon = 'CalendarBlank';
        placement.subtitle = 'Where this record belongs and when it applies.';
        remove('schedule');
    }

    const settings = byId('settings');
    const identity = byId('identity');
    if (settings && identity && settings.fields.length === 1 && identity.fields.length <= 7) {
        identity.fields = [...identity.fields, ...settings.fields];
        remove('settings');
    }

    const people = byId('people');
    if (people && people.fields.length === 1) {
        const target = placement ?? byId('contact') ?? identity;
        if (target) {
            target.fields = [...target.fields, ...people.fields];
            remove('people');
        }
    }

    const visible = sections.filter((section) => section.fields.length > 0);
    visible.forEach((section, index) => {
        section.tinted = index % 2 === 1;
    });
    return visible;
};
