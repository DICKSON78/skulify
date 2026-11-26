export type AppRole =
    | 'SUPER_ADMIN'
    | 'TENANT_ADMIN'
    | 'HEAD_MASTER'
    | 'SECOND_MASTER'
    | 'HEAD_TEACHER'
    | 'ACADEMIC_MASTER'
    | 'DISCIPLINE_MASTER'
    | 'TEACHER'
    | 'CLASS_TEACHER'
    | 'ACCOUNTANT'
    | 'REGISTRAR'
    | 'HR_MANAGER'
    | 'LIBRARIAN'
    | 'STORE_KEEPER'
    | 'CASHIER'
    | 'PARENT'
    | 'STUDENT';

const FULL_ADMIN: AppRole[] = [
    'SUPER_ADMIN',
    'TENANT_ADMIN',
    'HEAD_MASTER',
    'HEAD_TEACHER',
];
const ADMIN: AppRole[] = [...FULL_ADMIN, 'SECOND_MASTER'];
const ACADEMIC: AppRole[] = [
    ...ADMIN,
    'TEACHER',
    'CLASS_TEACHER',
    'ACADEMIC_MASTER',
    'DISCIPLINE_MASTER',
    'REGISTRAR',
];
const GUARDIAN: AppRole[] = ['PARENT', 'STUDENT'];
const FINANCE: AppRole[] = [...ADMIN, 'ACCOUNTANT'];
const LIBRARY_STAFF: AppRole[] = [...ADMIN, 'LIBRARIAN', 'ACADEMIC_MASTER'];
const STORE_STAFF: AppRole[] = [...ADMIN, 'STORE_KEEPER', 'CASHIER'];
const OPERATIONS: AppRole[] = [
    ...ADMIN,
    'REGISTRAR',
    'LIBRARIAN',
    'STORE_KEEPER',
    'CASHIER',
    'HR_MANAGER',
    'DISCIPLINE_MASTER',
    'ACADEMIC_MASTER',
    'TEACHER',
    'CLASS_TEACHER',
];
const ALL_ROLES: AppRole[] = [
    ...new Set<AppRole>([
        ...ADMIN,
        ...ACADEMIC,
        ...FINANCE,
        ...LIBRARY_STAFF,
        ...STORE_STAFF,
        ...GUARDIAN,
        'HR_MANAGER',
    ]),
];

/** Mirrors the backend ResourceAccessPolicy so navigation matches permissions. */
export const PATH_ACCESS: Record<string, AppRole[]> = {
    '/dashboard': ALL_ROLES,
    '/parent-portal': [...ADMIN, 'REGISTRAR', ...GUARDIAN],
    '/students': [...ACADEMIC, 'ACCOUNTANT', 'LIBRARIAN', 'STORE_KEEPER', 'HR_MANAGER', ...GUARDIAN],
    '/teachers': [...ACADEMIC, 'LIBRARIAN', 'STORE_KEEPER', 'HR_MANAGER'],
    '/classes': [...ACADEMIC, 'LIBRARIAN', 'HR_MANAGER'],
    '/subjects': [...ACADEMIC, 'LIBRARIAN', 'HR_MANAGER'],
    '/teacher-assignments': [...ADMIN, 'REGISTRAR', 'ACADEMIC_MASTER', 'TEACHER', 'CLASS_TEACHER'],
    '/school-documents': [...ADMIN, 'HR_MANAGER'],
    '/attendance': [...ACADEMIC, ...GUARDIAN],
    '/grades': [...ACADEMIC, ...GUARDIAN],
    '/timetable': [...ACADEMIC, 'LIBRARIAN', ...GUARDIAN],
    '/fees': [...FINANCE, ...GUARDIAN, 'CASHIER'],
    '/fee-categories': FINANCE,
    '/fee-plans': [...FINANCE, ...GUARDIAN],
    '/invoices': [...FINANCE, ...GUARDIAN, 'CASHIER'],
    '/invoice-items': FINANCE,
    '/payments': [...FINANCE, 'CASHIER'],
    '/pocket-money': [...FINANCE, 'HEAD_TEACHER', 'CASHIER', ...GUARDIAN],
    '/pocket-cards': [...FINANCE, 'CASHIER', ...GUARDIAN],
    '/spending-limits': [...FINANCE, ...GUARDIAN],
    '/pocket-transactions': [...FINANCE, 'STORE_KEEPER', 'CASHIER', ...GUARDIAN],
    '/store-pos': [...ADMIN, 'CASHIER'],
    '/store-items': [...STORE_STAFF, 'ACCOUNTANT', 'TEACHER', 'CLASS_TEACHER', ...GUARDIAN],
    '/store-terminals': [...STORE_STAFF, 'ACCOUNTANT'],
    '/inventory-items': [...ADMIN, 'STORE_KEEPER', 'ACCOUNTANT'],
    '/stock-movements': [...ADMIN, 'STORE_KEEPER', 'ACCOUNTANT'],
    '/purchase-requests': [...ADMIN, 'STORE_KEEPER', 'ACCOUNTANT'],
    '/departments': [...OPERATIONS, 'ACADEMIC_MASTER'],
    '/staff-attendance': [...ADMIN, 'REGISTRAR', 'HR_MANAGER', 'TEACHER', 'CLASS_TEACHER'],
    '/school-tasks': OPERATIONS,
    '/library-desk': [...LIBRARY_STAFF, ...ACADEMIC, ...GUARDIAN],
    '/books': [...LIBRARY_STAFF, ...ACADEMIC, ...GUARDIAN],
    '/book-copies': [...LIBRARY_STAFF, ...ACADEMIC, ...GUARDIAN],
    '/book-loans': [...LIBRARY_STAFF, ...ACADEMIC, ...GUARDIAN],
    '/book-reservations': [...LIBRARY_STAFF, ...ACADEMIC, ...GUARDIAN],
    '/lab-sessions': [...LIBRARY_STAFF, ...ACADEMIC, ...GUARDIAN],
    '/operations-hub': OPERATIONS,
    '/timetable-generator': [...ADMIN, 'REGISTRAR'],
    '/marks-upload': [...ADMIN, 'TEACHER', 'CLASS_TEACHER', 'REGISTRAR', 'ACADEMIC_MASTER'],
    '/duty-roster': [...ADMIN, 'TEACHER', 'CLASS_TEACHER', 'REGISTRAR', 'ACADEMIC_MASTER'],
    '/results-planning': [...ADMIN, 'TEACHER', 'CLASS_TEACHER', 'REGISTRAR', 'ACADEMIC_MASTER'],
    '/online-tests': [...ADMIN, 'TEACHER', 'CLASS_TEACHER', 'REGISTRAR'],
    '/periods': [...ACADEMIC, ...GUARDIAN],
    '/exams': [...ACADEMIC, ...GUARDIAN],
    '/exam-sessions': [...ACADEMIC, ...GUARDIAN],
    '/duty-assignments': [...ACADEMIC],
    '/report-cards': [...ACADEMIC, ...GUARDIAN],
    '/parent-letters': [...ACADEMIC, ...GUARDIAN],
    '/staff-leaves': [...ACADEMIC],
    '/school-events': [...ACADEMIC, 'ACCOUNTANT', 'LIBRARIAN', 'STORE_KEEPER', 'CASHIER'],
    '/reports': [...ADMIN, 'ACCOUNTANT', 'HR_MANAGER'],
    '/settings': FULL_ADMIN,
};

export const canAccess = (path: string, roles: string[]): boolean => {
    const allowed = PATH_ACCESS[path];
    if (!allowed) {
        return true;
    }
    return roles.some((role) => allowed.includes(role as AppRole));
};

export const isWriteRole = (roles: string[]): boolean =>
    roles.some((role) =>
        [
            ...FULL_ADMIN,
            'SECOND_MASTER',
            'TEACHER',
            'CLASS_TEACHER',
            'ACADEMIC_MASTER',
            'DISCIPLINE_MASTER',
            'ACCOUNTANT',
            'REGISTRAR',
            'HR_MANAGER',
            'LIBRARIAN',
            'STORE_KEEPER',
            'CASHIER',
        ].includes(role as AppRole),
    );

export const isGuardian = (roles: string[]): boolean =>
    roles.some((role) => role === 'PARENT' || role === 'STUDENT');

const GUARDIAN_WRITABLE = ['spending-limits', 'book-reservations', 'lab-sessions'];

const FINANCE_RESOURCES = [
    'fees',
    'payments',
    'invoices',
    'invoice-items',
    'fee-categories',
    'fee-plans',
    'pocket-cards',
    'pocket-transactions',
];

const STORE_RESOURCES = [
    'store-items',
    'store-terminals',
    'inventory-items',
    'stock-movements',
    'purchase-requests',
];

const ACADEMIC_RESOURCES = [
    'students',
    'teachers',
    'classes',
    'subjects',
    'attendance',
    'grades',
    'timetable',
    'periods',
    'exams',
    'exam-sessions',
    'duty-assignments',
    'teacher-assignments',
    'report-cards',
    'school-events',
];

const STRUCTURE_RESOURCES = [
    'academic-years',
    'terms',
    'enrolments',
    'teacher-assignments',
    'notifications',
    'staff-leaves',
];

const CLASS_MANAGEMENT = [
    'students',
    'classes',
    'attendance',
    'grades',
    'timetable',
    'teacher-assignments',
];

export const canWriteResource = (resource: string, roles: string[]): boolean => {
    if (roles.some((role) => FULL_ADMIN.includes(role as AppRole))) {
        return true;
    }
    if (isGuardian(roles)) {
        return GUARDIAN_WRITABLE.includes(resource);
    }
    if (roles.includes('SECOND_MASTER')) {
        return ![...FINANCE_RESOURCES, ...STORE_RESOURCES, 'reports', 'settings'].includes(resource);
    }
    if (roles.includes('HR_MANAGER')) {
        return [
            'departments',
            'staff-attendance',
            'school-tasks',
            'notifications',
            'school-documents',
            'teachers',
        ].includes(resource);
    }
    if (roles.includes('DISCIPLINE_MASTER')) {
        return ['students', 'attendance', 'school-tasks', 'notifications'].includes(resource);
    }
    if (roles.includes('ACADEMIC_MASTER')) {
        return [...ACADEMIC_RESOURCES, ...STRUCTURE_RESOURCES, 'marks-upload'].includes(resource);
    }
    if (roles.includes('CLASS_TEACHER') || roles.includes('TEACHER')) {
        return [
            ...CLASS_MANAGEMENT,
            'attendance',
            'grades',
            'lab-sessions',
            'marks-upload',
            'online-tests',
            'staff-attendance',
            'school-tasks',
        ].includes(resource);
    }
    if (roles.includes('ACCOUNTANT')) {
        return [
            ...FINANCE_RESOURCES,
            'purchase-requests',
            'stock-movements',
            'inventory-items',
            'store-terminals',
            'reports',
        ].includes(resource);
    }
    if (roles.includes('REGISTRAR')) {
        return [
            ...ACADEMIC_RESOURCES,
            'staff-leaves',
            'parent-letters',
            'online-tests',
            'departments',
            'school-tasks',
            'books',
            'book-copies',
            'book-loans',
        ].includes(resource);
    }
    if (roles.includes('LIBRARIAN')) {
        return ['books', 'book-copies', 'book-loans', 'book-reservations', 'lab-sessions'].includes(resource);
    }
    if (roles.includes('STORE_KEEPER')) {
        return [...STORE_RESOURCES, 'school-tasks'].includes(resource);
    }
    if (roles.includes('CASHIER')) {
        return ['school-tasks'].includes(resource);
    }
    return false;
};
