import Icon, { type IconName } from './Icon';

/** Semantic icon for each column label. Value-specific icons are applied first. */
const EXACT_ICONS: Record<string, IconName> = {
    actions: 'ListChecks',
    action: 'ListChecks',
    name: 'UserCircle',
    student: 'Student',
    students: 'UsersThree',
    'student code': 'IdentificationCard',
    'student id': 'Student',
    'staff member': 'ChalkboardTeacher',
    teacher: 'ChalkboardTeacher',
    'teacher name': 'ChalkboardTeacher',
    class: 'Buildings',
    'class name': 'Buildings',
    'class size': 'Users',
    'grade level': 'Medal',
    section: 'Buildings',
    capacity: 'Users',
    email: 'EnvelopeOpen',
    phone: 'Phone',
    'guardian phone': 'Phone',
    guardian: 'UsersThree',
    'guardian name': 'UsersThree',
    parent: 'UsersThree',
    gender: 'GenderNeuter',
    address: 'MapPin',
    specialization: 'Medal',
    employment: 'UserList',
    'employment type': 'UserList',
    'employee type': 'UserList',
    'student position': 'Medal',
    'admission date': 'CalendarPlus',
    'date of birth': 'CalendarBlank',
    'hire date': 'CalendarPlus',
    status: 'CheckCircle',
    subject: 'Books',
    'subject name': 'Books',
    room: 'Building',
    date: 'CalendarBlank',
    'start date': 'CalendarPlus',
    'end date': 'CalendarCheck',
    'due date': 'Hourglass',
    time: 'Clock',
    'start time': 'Clock',
    'end time': 'Clock',
    from: 'CalendarBlank',
    to: 'CalendarCheck',
    day: 'Calendar',
    term: 'Calendar',
    type: 'Info',
    'duty type': 'ClipboardText',
    duty: 'ClipboardText',
    'leave type': 'Airplane',
    leave: 'Airplane',
    test: 'Exam',
    exam: 'Exam',
    'exam type': 'Exam',
    assessment: 'Exam',
    questions: 'ListChecks',
    attempts: 'ChartLineUp',
    attended: 'CheckCircle',
    score: 'ChartLineUp',
    'max score': 'ChartLineUp',
    average: 'ChartBar',
    position: 'Medal',
    grade: 'Medal',
    reference: 'Receipt',
    ref: 'Receipt',
    code: 'Barcode',
    'access code': 'Barcode',
    registration: 'IdentificationCard',
    fee: 'Receipt',
    amount: 'Money',
    paid: 'Wallet',
    due: 'Hourglass',
    description: 'PencilSimple',
    comment: 'PencilSimple',
    remarks: 'PencilSimple',
    category: 'Stack',
    file: 'FileArrowUp',
    'file name': 'FileArrowUp',
    'document date': 'CalendarBlank',
    'uploaded by': 'UserCircle',
    'balance after': 'Wallet',
    balance: 'Wallet',
    submitted: 'CalendarCheck',
    'submitted at': 'CalendarCheck',
    'check-in': 'CalendarCheck',
    'check in': 'CalendarCheck',
    result: 'ChartLineUp',
};

const KEYWORD_ICONS: Array<[RegExp, IconName]> = [
    [/action/, 'ListChecks'],
    [/gender/, 'GenderNeuter'],
    [/phone/, 'Phone'],
    [/email/, 'EnvelopeOpen'],
    [/guardian|parent/, 'UsersThree'],
    [/student|learner/, 'Student'],
    [/teacher|staff|faculty/, 'ChalkboardTeacher'],
    [/class|section/, 'Buildings'],
    [/subject|course/, 'Books'],
    [/reference|\bref\b|registration|student code/, 'IdentificationCard'],
    [/balance|paid|wallet/, 'Wallet'],
    [/amount|fee|money|cost|collected/, 'Money'],
    [/due|overdue/, 'Hourglass'],
    [/submitted|check.?in|attend|present/, 'CalendarCheck'],
    [/date|day|term/, 'CalendarBlank'],
    [/time|hour/, 'Clock'],
    [/status/, 'CheckCircle'],
    [/grade|position|rank/, 'Medal'],
    [/score|average|attempt|result/, 'ChartLineUp'],
    [/remarks|description|comment/, 'PencilSimple'],
    [/room|address|building/, 'Building'],
    [/test|exam|assessment/, 'Exam'],
    [/duty|leave|category|type/, 'ClipboardText'],
];

const VALUE_ICONS: Array<[(value: string) => boolean, IconName]> = [
    [(value) => ['MALE', 'M'].includes(value), 'GenderMale'],
    [(value) => ['FEMALE', 'F'].includes(value), 'GenderFemale'],
    [
        (value) => ['OTHER', 'NON_BINARY', 'NONBINARY', 'NON-BINARY', 'NON BINARY'].includes(value),
        'GenderNonbinary',
    ],
    [
        (value) =>
            [
                'ACTIVE', 'PRESENT', 'PAID', 'COMPLETED', 'APPROVED', 'AVAILABLE', 'FULL_TIME',
                'FULL TIME', 'PUBLISHED', 'SCHEDULED', 'ATTENDED', 'YES', 'TRUE', '1',
            ].includes(value),
        'CheckCircle',
    ],
    [
        (value) =>
            [
                'INACTIVE', 'SUSPENDED', 'ARCHIVED', 'CLOSED', 'REJECTED', 'NO', 'FALSE', '0',
                'ABSENT', 'FAILED', 'EXPIRED', 'LOST', 'CANCELLED', 'CANCELED',
                'NOT_ATTENDED', 'NOT ATTENDED',
            ].includes(value),
        'XCircle',
    ],
    [
        (value) =>
            [
                'PENDING', 'ON_LEAVE', 'ON LEAVE', 'ON_HOLD', 'ON HOLD', 'DUE', 'UNPAID',
                'OVERDUE', 'GRADUATED', 'RESERVED', 'BORROWED', 'PROCESSING', 'PARTIAL',
                'DRAFT', 'EXCUSED',
            ].includes(value),
        'Hourglass',
    ],
    [(value) => ['LATE'].includes(value), 'Clock'],
    [
        (value) => ['PREFECT', 'LIBRARIAN', 'LAB_TECHNICIAN'].includes(value),
        'Medal',
    ],
    [
        (value) => ['TEACHER', 'SECURITY_GUARD', 'CLEANER', 'ADMINISTRATIVE'].includes(value),
        'UserList',
    ],
    [(value) => /^\d{4}-\d{2}-\d{2}/.test(value) || /^\d{1,4}[/-]\d{1,2}[/-]\d{1,4}/.test(value), 'CalendarBlank'],
    [(value) => /^\d{1,2}:\d{2}/.test(value), 'Clock'],
    [(value) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value), 'EnvelopeOpen'],
    [(value) => /^\+?\d[\d\s-]{6,}$/.test(value), 'Phone'],
    [(value) => /^(TZS|TSH|USD|\$|€|£)\s?[\d,]+/i.test(value), 'Money'],
];

const normalise = (value: string): string =>
    value
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, ' ')
        .trim()
        .replace(/\s+/g, ' ');

const normaliseValue = (value: unknown): string => {
    if (value === null || value === undefined) {
        return '';
    }
    const text = typeof value === 'boolean' ? (value ? 'TRUE' : 'FALSE') : String(value);
    return text.trim().toUpperCase();
};

const iconForValue = (value: unknown): IconName | null => {
    const text = normaliseValue(value);
    if (!text) {
        return null;
    }
    for (const [matches, icon] of VALUE_ICONS) {
        if (matches(text)) {
            return icon;
        }
    }
    return null;
};

const iconForLabel = (label: string): IconName => {
    const key = normalise(label);
    if (EXACT_ICONS[key]) {
        return EXACT_ICONS[key];
    }
    for (const [pattern, icon] of KEYWORD_ICONS) {
        if (pattern.test(key)) {
            return icon;
        }
    }
    return 'Table';
};

type TableIconProps = {
    label?: string;
    name?: IconName | string;
    value?: unknown;
    size?: number;
    className?: string;
};

export default function TableIcon({
    label = '',
    name,
    value,
    size = 15,
    className = 'table-icon',
}: TableIconProps) {
    const resolved = name ?? iconForValue(value) ?? iconForLabel(label);
    return <Icon name={resolved} size={size} className={className} />;
}
