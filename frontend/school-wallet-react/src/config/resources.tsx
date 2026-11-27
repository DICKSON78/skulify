import Icon from '../components/Icon';
import {
    asText,
    countWhere,
    money,
    sumNumbers,
    uniqueCount,
    type ResourceConfig,
    type Row,
} from '../components/ResourcePage';
import {
    districtOptions,
    regionOptions,
    streetOptions,
} from '../data/tanzaniaLocations';

const personName = (row: Row): string =>
    `${asText(row.firstName)} ${asText(row.lastName)}`.trim();

const studentLabel = (row: Row): string =>
    personName(row) || asText(row.studentCode) || asText(row.id).slice(0, 8);

const teacherLabel = (row: Row): string =>
    personName(row) || asText(row.employeeCode) || asText(row.id).slice(0, 8);

const classLabel = (row: Row): string =>
    asText(row.name) || asText(row.code) || asText(row.id).slice(0, 8);

const subjectLabel = (row: Row): string =>
    asText(row.name) || asText(row.code) || asText(row.id).slice(0, 8);

const statusOptions = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'GRADUATED'];

const labelOf =
    (...keys: string[]) =>
    (row: Row): string =>
        keys.map((key) => asText(row[key])).find(Boolean) ?? asText(row.id).slice(0, 8);

export const RESOURCE_CONFIGS: Record<string, ResourceConfig> = {
    students: {
        resource: 'students',
        title: 'Students',
        subtitle: 'Manage student records and admissions',
        icon: 'GraduationCap',
        singular: 'Student',
        stats: [
            {
                label: 'Total students',
                icon: 'GraduationCap',
                value: (_rows, total) => total,
                hint: 'All registered learners',
                tone: 'primary',
            },
            {
                label: 'Active',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ACTIVE'),
                hint: 'Currently enrolled',
                tone: 'success',
            },
            {
                label: 'Inactive / suspended',
                icon: 'UserMinus',
                value: (rows) =>
                    countWhere(rows, (row) =>
                        ['INACTIVE', 'SUSPENDED'].includes(asText(row.status)),
                    ),
                tone: 'warning',
            },
            {
                label: 'Classes',
                icon: 'Buildings',
                value: (rows) => uniqueCount(rows, 'className'),
                tone: 'info',
            },
        ],
        searchPlaceholder: 'Search by name, code, class or guardian...',
        columns: [
            {
                key: 'studentCode',
                label: 'Student',
                render: (row) => (
                    <div>
                        <p className="font-semibold text-gray-900">{personName(row) || '—'}</p>
                        <p className="text-xs text-gray-400">{asText(row.studentCode)}</p>
                    </div>
                ),
            },
            { key: 'className', label: 'Class' },
            { key: 'studentPosition', label: 'Student position', format: 'badge' },
            { key: 'gender', label: 'Gender' },
            { key: 'dateOfBirth', label: 'Date of birth' },
            { key: 'admissionDate', label: 'Admission date' },
            { key: 'email', label: 'Email' },
            { key: 'guardianName', label: 'Guardian' },
            { key: 'guardianPhone', label: 'Guardian phone' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'studentCode', label: 'Student code', required: true, placeholder: 'e.g. STU-001' },
            { key: 'firstName', label: 'First name', required: true },
            { key: 'lastName', label: 'Last name', required: true },
            { key: 'email', label: 'Email', type: 'email' },
            { key: 'phone', label: 'Phone' },
            { key: 'gender', label: 'Gender', type: 'select', options: ['MALE', 'FEMALE'] },
            { key: 'dateOfBirth', label: 'Date of birth', type: 'date' },
            { key: 'admissionDate', label: 'Admission date', type: 'date' },
            { key: 'className', label: 'Class', placeholder: 'e.g. Form 1A' },
            {
                key: 'studentPosition',
                label: 'Student position',
                type: 'select',
                options: ['NONE', 'PREFECT', 'LIBRARIAN', 'LAB_TECHNICIAN'],
                defaultValue: 'NONE',
            },
            { key: 'guardianName', label: 'Guardian name' },
            { key: 'guardianPhone', label: 'Guardian phone' },
            {
                key: 'guardianRegion',
                label: 'Guardian region (Mkoa)',
                type: 'select',
                placeholder: 'Select guardian region (mkoa)',
                options: regionOptions,
                required: true,
                clears: ['guardianDistrict', 'guardianStreet'],
            },
            {
                key: 'guardianDistrict',
                label: 'Guardian district (Wilaya)',
                type: 'select',
                placeholder: 'Select guardian district (wilaya)',
                optionsFor: (values) => districtOptions(asText(values.guardianRegion)),
                dependsOn: 'guardianRegion',
                clears: ['guardianStreet'],
                required: true,
            },
            {
                key: 'guardianStreet',
                label: 'Guardian street (Mtaa)',
                type: 'select',
                placeholder: 'Select guardian street (mtaa)',
                optionsFor: (values) =>
                    streetOptions(asText(values.guardianRegion), asText(values.guardianDistrict)),
                dependsOn: 'guardianDistrict',
                required: true,
            },
            {
                key: 'region',
                label: 'Region (Mkoa)',
                type: 'select',
                placeholder: 'Select region (mkoa)',
                options: regionOptions,
                required: true,
                clears: ['district', 'street'],
            },
            {
                key: 'district',
                label: 'District (Wilaya)',
                type: 'select',
                placeholder: 'Select district (wilaya)',
                optionsFor: (values) => districtOptions(asText(values.region)),
                required: true,
                dependsOn: 'region',
                clears: ['street'],
            },
            {
                key: 'street',
                label: 'Street (Mtaa)',
                type: 'select',
                placeholder: 'Select street (mtaa)',
                required: true,
                optionsFor: (values) =>
                    streetOptions(asText(values.region), asText(values.district)),
                dependsOn: 'district',
            },
            {
                key: 'address',
                label: 'Address',
                type: 'textarea',
                wide: true,
            },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: statusOptions,
                defaultValue: 'ACTIVE',
                required: true,
            },
        ],
    },

    teachers: {
        resource: 'teachers',
        title: 'Teachers & Staff',
        subtitle: 'Manage teachers and staff members',
        icon: 'ChalkboardTeacher',
        singular: 'Staff Member',
        stats: [
            {
                label: 'Total staff',
                icon: 'ChalkboardTeacher',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Active',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ACTIVE'),
                tone: 'success',
            },
            {
                label: 'On leave',
                icon: 'Airplane',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ON_LEAVE'),
                tone: 'warning',
            },
            {
                label: 'Specializations',
                icon: 'Medal',
                value: (rows) => uniqueCount(rows, 'specialization'),
                tone: 'info',
            },
        ],
        searchPlaceholder: 'Search by name, code or specialization...',
        columns: [
            {
                key: 'employeeCode',
                label: 'Staff member',
                render: (row) => (
                    <div>
                        <p className="font-semibold text-gray-900">{personName(row) || '—'}</p>
                        <p className="text-xs text-gray-400">{asText(row.employeeCode)}</p>
                    </div>
                ),
            },
            { key: 'specialization', label: 'Specialization' },
            { key: 'employeeType', label: 'Employee type', format: 'badge' },
            { key: 'position', label: 'Position' },
            { key: 'email', label: 'Email' },
            { key: 'phone', label: 'Phone' },
            { key: 'employmentType', label: 'Employment', format: 'badge' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'employeeCode', label: 'Employee code', required: true, placeholder: 'e.g. EMP-001' },
            { key: 'firstName', label: 'First name', required: true },
            { key: 'lastName', label: 'Last name', required: true },
            { key: 'email', label: 'Email', type: 'email' },
            { key: 'phone', label: 'Phone' },
            { key: 'specialization', label: 'Specialization' },
            {
                key: 'employeeType',
                label: 'Employee type',
                type: 'select',
                options: ['TEACHER', 'SECURITY_GUARD', 'CLEANER', 'ADMINISTRATIVE'],
                defaultValue: 'TEACHER',
            },
            { key: 'position', label: 'Position / role', placeholder: 'e.g. Class Teacher, Walinzi' },
            {
                key: 'employmentType',
                label: 'Employment type',
                type: 'select',
                options: ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'VOLUNTEER'],
                defaultValue: 'FULL_TIME',
            },
            { key: 'hireDate', label: 'Hire date', type: 'date' },
            { key: 'address', label: 'Address', type: 'textarea', wide: true },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['ACTIVE', 'ON_LEAVE', 'INACTIVE'],
                defaultValue: 'ACTIVE',
                required: true,
            },
        ],
    },

    classes: {
        resource: 'classes',
        title: 'Classes',
        subtitle: 'Manage classes, sections and capacity',
        icon: 'Buildings',
        singular: 'Class',
        stats: [
            {
                label: 'Total classes',
                icon: 'Buildings',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Active',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ACTIVE'),
                tone: 'success',
            },
            {
                label: 'Archived',
                icon: 'Archive',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ARCHIVED'),
                tone: 'warning',
            },
            {
                label: 'Total capacity',
                icon: 'Users',
                value: (rows) => sumNumbers(rows, 'capacity'),
                tone: 'info',
            },
        ],
        searchPlaceholder: 'Search by class name, code or grade...',
        columns: [
            {
                key: 'name',
                label: 'Class',
                render: (row) => (
                    <div>
                        <p className="font-semibold text-gray-900">{asText(row.name) || '—'}</p>
                        <p className="text-xs text-gray-400">{asText(row.code)}</p>
                    </div>
                ),
            },
            { key: 'gradeLevel', label: 'Grade level' },
            { key: 'section', label: 'Section' },
            { key: 'room', label: 'Room' },
            { key: 'capacity', label: 'Capacity', format: 'number' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'code', label: 'Class code', required: true, placeholder: 'e.g. F1A' },
            { key: 'name', label: 'Class name', required: true, placeholder: 'e.g. Form 1A' },
            { key: 'gradeLevel', label: 'Grade level', placeholder: 'e.g. Form 1' },
            { key: 'section', label: 'Section' },
            { key: 'room', label: 'Room' },
            { key: 'capacity', label: 'Capacity', type: 'number', defaultValue: 40 },
            {
                key: 'classTeacherId',
                label: 'Class teacher',
                type: 'reference',
                reference: { resource: 'teachers', label: teacherLabel },
            },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['ACTIVE', 'ARCHIVED'],
                defaultValue: 'ACTIVE',
                required: true,
            },
        ],
    },

    'teacher-assignments': {
        resource: 'teacher-assignments',
        title: 'Class Management',
        subtitle: 'Assign teachers to classes and subjects',
        icon: 'ChalkboardTeacher',
        singular: 'Assignment',
        stats: [
            {
                label: 'Total assignments',
                icon: 'ListChecks',
                value: (_rows, total) => total,
                hint: 'Teacher, class and subject links',
                tone: 'primary',
            },
            {
                label: 'Class teachers',
                icon: 'ChalkboardTeacher',
                value: (rows) => countWhere(rows, (row) => asText(row.scope) === 'CLASS_TEACHER'),
                hint: 'Leaders assigned to classes',
                tone: 'success',
            },
            {
                label: 'Subject teachers',
                icon: 'Books',
                value: (rows) => countWhere(rows, (row) => asText(row.scope) === 'SUBJECT_TEACHER'),
                hint: 'Specialist teaching assignments',
                tone: 'info',
            },
            {
                label: 'Active',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ACTIVE'),
                hint: 'Assignments currently in force',
                tone: 'warning',
            },
        ],
        searchPlaceholder: 'Search assignments...',
        columns: [
            {
                key: 'teacherId',
                label: 'Teacher',
                reference: { resource: 'teachers', label: teacherLabel },
            },
            {
                key: 'classId',
                label: 'Class',
                reference: { resource: 'classes', label: classLabel },
            },
            {
                key: 'subjectId',
                label: 'Subject',
                reference: { resource: 'subjects', label: subjectLabel },
            },
            { key: 'scope', label: 'Scope', format: 'badge' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            {
                key: 'teacherId',
                label: 'Teacher',
                type: 'reference',
                required: true,
                reference: { resource: 'teachers', label: teacherLabel },
            },
            {
                key: 'classId',
                label: 'Class',
                type: 'reference',
                required: true,
                reference: { resource: 'classes', label: classLabel },
            },
            {
                key: 'subjectId',
                label: 'Subject',
                type: 'reference',
                reference: { resource: 'subjects', label: subjectLabel },
            },
            {
                key: 'academicYearId',
                label: 'Academic year',
                type: 'reference',
                reference: { resource: 'academic-years', label: labelOf('name', 'code') },
            },
            {
                key: 'scope',
                label: 'Scope',
                type: 'select',
                options: ['CLASS_TEACHER', 'SUBJECT_TEACHER', 'HEAD_TEACHER'],
                defaultValue: 'CLASS_TEACHER',
                required: true,
            },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['ACTIVE', 'INACTIVE'],
                defaultValue: 'ACTIVE',
                required: true,
            },
        ],
    },

    'staff-attendance': {
        resource: 'staff-attendance',
        title: 'Staff Attendance',
        subtitle: 'Record employee check-in and check-out with automatic HR alerts',
        icon: 'ClockCounterClockwise',
        singular: 'Attendance Record',
        stats: [
            {
                label: 'Records',
                icon: 'ListChecks',
                value: (_rows, total) => total,
                hint: 'Staff attendance entries',
                tone: 'primary',
            },
            {
                label: 'Checked in',
                icon: 'CalendarCheck',
                value: (rows) => countWhere(rows, (row) => Boolean(asText(row.checkIn))),
                hint: 'Arrivals recorded for staff',
                tone: 'success',
            },
            {
                label: 'Checked out',
                icon: 'ClockCounterClockwise',
                value: (rows) => countWhere(rows, (row) => Boolean(asText(row.checkOut))),
                hint: 'Departures recorded for staff',
                tone: 'info',
            },
            {
                label: 'Present',
                icon: 'UserCheck',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'PRESENT'),
                hint: 'Marked present in this period',
                tone: 'warning',
            },
        ],
        searchPlaceholder: 'Search staff attendance...',
        columns: [
            {
                key: 'staffId',
                label: 'Staff member',
                render: (row) => (
                    <div>
                        <p className="font-semibold text-gray-900">
                            {asText(row.staffId).slice(0, 8) || '—'}
                        </p>
                        <p className="text-xs text-gray-400">Employee ID</p>
                    </div>
                ),
            },
            { key: 'attendanceDate', label: 'Date' },
            { key: 'status', label: 'Status', format: 'badge' },
            { key: 'checkIn', label: 'Check in' },
            { key: 'checkOut', label: 'Check out' },
            { key: 'recordedBy', label: 'Recorded by' },
            { key: 'remarks', label: 'Remarks' },
        ],
        fields: [
            {
                key: 'staffId',
                label: 'Staff member',
                type: 'reference',
                required: true,
                reference: { resource: 'teachers', label: teacherLabel },
            },
            {
                key: 'attendanceDate',
                label: 'Attendance date',
                type: 'date',
                required: true,
                defaultValue: new Date().toISOString().slice(0, 10),
            },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['PRESENT', 'LATE', 'ABSENT', 'ON_LEAVE'],
                defaultValue: 'PRESENT',
                required: true,
            },
            { key: 'checkIn', label: 'Check in', type: 'time' },
            { key: 'checkOut', label: 'Check out', type: 'time' },
            {
                key: 'recordedBy',
                label: 'Recorded by',
                defaultValue: 'Teacher portal',
            },
            { key: 'remarks', label: 'Remarks', type: 'textarea', wide: true },
        ],
    },

    subjects: {
        resource: 'subjects',
        title: 'Subjects',
        subtitle: 'Manage subjects offered by the school',
        icon: 'Books',
        singular: 'Subject',
        stats: [
            {
                label: 'Total subjects',
                icon: 'Books',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Active',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ACTIVE'),
                tone: 'success',
            },
            {
                label: 'Departments',
                icon: 'Buildings',
                value: (rows) => uniqueCount(rows, 'department'),
                tone: 'info',
            },
            {
                label: 'Total credits',
                icon: 'Medal',
                value: (rows) => sumNumbers(rows, 'credits'),
                tone: 'warning',
            },
        ],
        searchPlaceholder: 'Search by subject name, code or department...',
        columns: [
            {
                key: 'name',
                label: 'Subject',
                render: (row) => (
                    <div>
                        <p className="font-semibold text-gray-900">{asText(row.name) || '—'}</p>
                        <p className="text-xs text-gray-400">{asText(row.code)}</p>
                    </div>
                ),
            },
            { key: 'department', label: 'Department' },
            { key: 'credits', label: 'Credits', format: 'number' },
            { key: 'description', label: 'Description' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'code', label: 'Subject code', required: true, placeholder: 'e.g. MATH' },
            { key: 'name', label: 'Subject name', required: true },
            { key: 'department', label: 'Department' },
            { key: 'credits', label: 'Credits', type: 'number', defaultValue: 1 },
            { key: 'description', label: 'Description', type: 'textarea', wide: true },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['ACTIVE', 'INACTIVE'],
                defaultValue: 'ACTIVE',
                required: true,
            },
        ],
    },

    attendance: {
        resource: 'attendance',
        title: 'Attendance',
        subtitle: 'Track daily student attendance',
        icon: 'CalendarCheck',
        singular: 'Attendance Record',
        stats: [
            {
                label: 'Total records',
                icon: 'CalendarCheck',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Present',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'PRESENT'),
                tone: 'success',
            },
            {
                label: 'Late / excused',
                icon: 'Clock',
                value: (rows) =>
                    countWhere(rows, (row) =>
                        ['LATE', 'EXCUSED'].includes(asText(row.status)),
                    ),
                tone: 'warning',
            },
            {
                label: 'Attendance rate',
                icon: 'ChartPie',
                value: (rows) => {
                    if (rows.length === 0) {
                        return '0%';
                    }
                    const present = countWhere(rows, (row) => asText(row.status) === 'PRESENT');
                    return `${Math.round((present / rows.length) * 100)}%`;
                },
                tone: 'info',
            },
        ],
        searchPlaceholder: 'Search by student, date, status or remarks...',
        columns: [
            {
                key: 'studentId',
                label: 'Student',
                render: (row) => (
                    <span className="font-medium text-gray-900">{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span>
                ),
            },
            { key: 'attendanceDate', label: 'Date', format: 'date' },
            { key: 'status', label: 'Status', format: 'badge' },
            { key: 'checkIn', label: 'Check-in' },
            { key: 'recordedBy', label: 'Recorded by' },
            { key: 'remarks', label: 'Remarks' },
        ],
        fields: [
            {
                key: 'studentId',
                label: 'Student',
                type: 'reference',
                reference: { resource: 'students', label: studentLabel },
                required: true,
            },
            { key: 'attendanceDate', label: 'Date', type: 'date', required: true },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED'],
                defaultValue: 'PRESENT',
                required: true,
            },
            { key: 'checkIn', label: 'Check-in time', type: 'time' },
            { key: 'recordedBy', label: 'Recorded by', defaultValue: 'Admin' },
            { key: 'remarks', label: 'Remarks', type: 'textarea', wide: true },
        ],
    },

    grades: {
        resource: 'grades',
        title: 'Grades & Exams',
        subtitle: 'Manage assessments, scores and grades',
        icon: 'Exam',
        singular: 'Grade Record',
        stats: [
            {
                label: 'Total records',
                icon: 'Exam',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Exams recorded',
                icon: 'ClipboardText',
                value: (rows) => uniqueCount(rows, 'examType'),
                tone: 'info',
            },
            {
                label: 'Average score',
                icon: 'ChartLineUp',
                value: (rows) => {
                    const scores = rows
                        .map((row) => Number(row.score))
                        .filter((score) => !Number.isNaN(score));
                    if (scores.length === 0) {
                        return '—';
                    }
                    const average = scores.reduce((sum, score) => sum + score, 0) / scores.length;
                    return `${Math.round(average)}%`;
                },
                tone: 'success',
            },
            {
                label: 'Grade bands',
                icon: 'Medal',
                value: (rows) => uniqueCount(rows, 'grade'),
                tone: 'warning',
            },
        ],
        searchPlaceholder: 'Search by exam, term, grade or remarks...',
        columns: [
            {
                key: 'studentId',
                label: 'Student',
                render: (row) => (
                    <span className="font-medium text-gray-900">{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span>
                ),
            },
            {
                key: 'subjectId',
                label: 'Subject',
                render: (row) => (
                    <span className="font-medium text-gray-900">{asText(row.subjectName) || asText(row.subjectId).slice(0, 8)}</span>
                ),
            },
            { key: 'examType', label: 'Exam' },
            { key: 'term', label: 'Term' },
            {
                key: 'score',
                label: 'Score',
                render: (row) =>
                    row.score === null || row.score === undefined
                        ? '—'
                        : `${asText(row.score)} / ${asText(row.maxScore ?? 100)}`,
            },
            { key: 'grade', label: 'Grade', format: 'badge' },
            { key: 'remarks', label: 'Remarks' },
        ],
        fields: [
            {
                key: 'studentId',
                label: 'Student',
                type: 'reference',
                reference: { resource: 'students', label: studentLabel },
                required: true,
            },
            {
                key: 'subjectId',
                label: 'Subject',
                type: 'reference',
                reference: { resource: 'subjects', label: subjectLabel },
                required: true,
            },
            { key: 'examType', label: 'Exam type', required: true, placeholder: 'e.g. Mid-term' },
            {
                key: 'term',
                label: 'Term',
                type: 'select',
                options: ['TERM_1', 'TERM_2', 'TERM_3'],
            },
            { key: 'score', label: 'Score', type: 'number', required: true },
            { key: 'maxScore', label: 'Maximum score', type: 'number', defaultValue: 100 },
            { key: 'grade', label: 'Grade', placeholder: 'e.g. A' },
            { key: 'remarks', label: 'Remarks', type: 'textarea', wide: true },
        ],
    },

    fees: {
        resource: 'fees',
        title: 'Fees',
        subtitle: 'Manage fee records and payments',
        icon: 'Wallet',
        singular: 'Fee Record',
        stats: [
            {
                label: 'Total billed',
                icon: 'Invoice',
                value: (rows) => money(sumNumbers(rows, 'amount')),
                hint: 'Across loaded records',
                tone: 'primary',
            },
            {
                label: 'Collected',
                icon: 'CheckCircle',
                value: (rows) => money(sumNumbers(rows, 'paidAmount')),
                tone: 'success',
            },
            {
                label: 'Outstanding',
                icon: 'Hourglass',
                value: (rows) =>
                    money(sumNumbers(rows, 'amount') - sumNumbers(rows, 'paidAmount')),
                tone: 'warning',
            },
            {
                label: 'Overdue',
                icon: 'Warning',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'OVERDUE'),
                tone: 'danger',
            },
        ],
        searchPlaceholder: 'Search by reference, fee type or status...',
        columns: [
            { key: 'reference', label: 'Reference', render: (row) => <span className="font-semibold text-gray-900">{asText(row.reference)}</span> },
            {
                key: 'studentId',
                label: 'Student',
                render: (row) => (
                    <span className="font-medium text-gray-900">{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span>
                ),
            },
            { key: 'feeType', label: 'Fee type' },
            { key: 'amount', label: 'Amount', format: 'currency' },
            { key: 'paidAmount', label: 'Paid', format: 'currency' },
            { key: 'dueDate', label: 'Due date', format: 'date' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'reference', label: 'Reference', required: true, placeholder: 'e.g. FEE-2026-001' },
            {
                key: 'studentId',
                label: 'Student',
                type: 'reference',
                reference: { resource: 'students', label: studentLabel },
                required: true,
            },
            { key: 'feeType', label: 'Fee type', required: true, placeholder: 'e.g. Tuition' },
            { key: 'amount', label: 'Amount', type: 'number', required: true },
            { key: 'paidAmount', label: 'Paid amount', type: 'number', defaultValue: 0 },
            { key: 'dueDate', label: 'Due date', type: 'date' },
            { key: 'paidDate', label: 'Paid date', type: 'date' },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['PENDING', 'PARTIAL', 'PAID', 'OVERDUE', 'CANCELLED'],
                defaultValue: 'PENDING',
                required: true,
            },
            { key: 'remarks', label: 'Remarks', type: 'textarea', wide: true },
        ],
    },

    timetable: {
        resource: 'timetable',
        title: 'Timetable',
        subtitle: 'Manage class schedules and weekly lessons',
        icon: 'Table',
        singular: 'Timetable Entry',
        stats: [
            {
                label: 'Total entries',
                icon: 'Table',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Classes covered',
                icon: 'Buildings',
                value: (rows) => uniqueCount(rows, 'classId'),
                tone: 'info',
            },
            {
                label: 'Teachers assigned',
                icon: 'ChalkboardTeacher',
                value: (rows) => uniqueCount(rows, 'teacherId'),
                tone: 'success',
            },
            {
                label: 'Rooms used',
                icon: 'DoorOpen',
                value: (rows) => uniqueCount(rows, 'room'),
                tone: 'warning',
            },
        ],
        searchPlaceholder: 'Search by class, subject, teacher or room...',
        columns: [
            {
                key: 'classId',
                label: 'Class',
                render: (row) => (
                    <span className="font-medium text-gray-900">{asText(row.className) || asText(row.classId).slice(0, 8)}</span>
                ),
            },
            {
                key: 'subjectId',
                label: 'Subject',
                render: (row) => (
                    <span className="font-medium text-gray-900">{asText(row.subjectName) || asText(row.subjectId).slice(0, 8)}</span>
                ),
            },
            {
                key: 'teacherId',
                label: 'Teacher',
                render: (row) => (
                    <span className="font-medium text-gray-900">{asText(row.teacherName) || asText(row.teacherId).slice(0, 8)}</span>
                ),
            },
            { key: 'dayOfWeek', label: 'Day', format: 'badge' },
            {
                key: 'startTime',
                label: 'Time',
                render: (row) => `${asText(row.startTime)} – ${asText(row.endTime)}`,
            },
            { key: 'room', label: 'Room' },
        ],
        fields: [
            {
                key: 'classId',
                label: 'Class',
                type: 'reference',
                reference: { resource: 'classes', label: classLabel },
                required: true,
            },
            {
                key: 'subjectId',
                label: 'Subject',
                type: 'reference',
                reference: { resource: 'subjects', label: subjectLabel },
                required: true,
            },
            {
                key: 'teacherId',
                label: 'Teacher',
                type: 'reference',
                reference: { resource: 'teachers', label: teacherLabel },
            },
            {
                key: 'dayOfWeek',
                label: 'Day of week',
                type: 'select',
                options: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
                defaultValue: 'MONDAY',
                required: true,
            },
            { key: 'startTime', label: 'Start time', type: 'time', required: true },
            { key: 'endTime', label: 'End time', type: 'time', required: true },
            { key: 'room', label: 'Room' },
        ],
    },

    reports: {
        resource: 'reports',
        title: 'Reports',
        subtitle: 'Configure and generate school reports',
        icon: 'ChartBar',
        singular: 'Report',
        stats: [
            {
                label: 'Total reports',
                icon: 'ChartBar',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Active',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ACTIVE'),
                tone: 'success',
            },
            {
                label: 'Drafts',
                icon: 'Exam',
                value: (rows) => countWhere(rows, (row) => asText(row.status) === 'DRAFT'),
                tone: 'warning',
            },
            {
                label: 'Formats',
                icon: 'Export',
                value: (rows) => uniqueCount(rows, 'format'),
                tone: 'info',
            },
        ],
        searchPlaceholder: 'Search by report name, type or format...',
        columns: [
            { key: 'name', label: 'Report', render: (row) => <span className="font-semibold text-gray-900">{asText(row.name)}</span> },
            { key: 'reportType', label: 'Type' },
            { key: 'format', label: 'Format', format: 'badge' },
            { key: 'lastGenerated', label: 'Last generated', format: 'date' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'name', label: 'Report name', required: true },
            { key: 'reportType', label: 'Report type', required: true, placeholder: 'e.g. Attendance summary' },
            { key: 'description', label: 'Description', type: 'textarea', wide: true },
            {
                key: 'format',
                label: 'Format',
                type: 'select',
                options: ['PDF', 'CSV', 'XLSX'],
                defaultValue: 'PDF',
                required: true,
            },
            { key: 'lastGenerated', label: 'Last generated', type: 'date' },
            {
                key: 'status',
                label: 'Status',
                type: 'select',
                options: ['ACTIVE', 'DRAFT', 'ARCHIVED'],
                defaultValue: 'ACTIVE',
                required: true,
            },
        ],
    },

    settings: {
        resource: 'settings',
        title: 'Settings',
        subtitle: 'Manage school system settings',
        icon: 'GearSix',
        singular: 'Setting',
        stats: [
            {
                label: 'Total settings',
                icon: 'GearSix',
                value: (_rows, total) => total,
                tone: 'primary',
            },
            {
                label: 'Active',
                icon: 'CheckCircle',
                value: (rows) => countWhere(rows, (row) => row.active === true),
                tone: 'success',
            },
            {
                label: 'Disabled',
                icon: 'XCircle',
                value: (rows) => countWhere(rows, (row) => row.active === false),
                tone: 'danger',
            },
            {
                label: 'Categories',
                icon: 'Stack',
                value: (rows) => uniqueCount(rows, 'category'),
                tone: 'info',
            },
        ],
        searchPlaceholder: 'Search by key, category or description...',
        columns: [
            { key: 'settingKey', label: 'Key', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.settingKey)}</span> },
            { key: 'settingValue', label: 'Value' },
            { key: 'category', label: 'Category', format: 'badge' },
            { key: 'description', label: 'Description' },
            { key: 'active', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'settingKey', label: 'Setting key', required: true, placeholder: 'e.g. school.name' },
            { key: 'settingValue', label: 'Setting value', required: true },
            {
                key: 'category',
                label: 'Category',
                type: 'select',
                options: ['GENERAL', 'ACADEMIC', 'FINANCE', 'SECURITY'],
                defaultValue: 'GENERAL',
            },
            { key: 'description', label: 'Description', type: 'textarea', wide: true },
            { key: 'active', label: 'Active', type: 'boolean', defaultValue: true },
        ],
    },

    'fee-categories': {
        resource: 'fee-categories',
        title: 'Fee Categories',
        subtitle: 'Group fee items such as tuition, transport and meals',
        icon: 'Stack',
        singular: 'Fee Category',
        stats: [
            { label: 'Categories', icon: 'Stack', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Active', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => row.active === true), tone: 'success' },
        ],
        searchPlaceholder: 'Search by code or name...',
        columns: [
            { key: 'code', label: 'Code', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.code)}</span> },
            { key: 'name', label: 'Category', render: (row) => <span className="font-semibold text-gray-900">{asText(row.name)}</span> },
            { key: 'description', label: 'Description' },
            { key: 'active', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'code', label: 'Code', required: true, placeholder: 'e.g. TUITION' },
            { key: 'name', label: 'Name', required: true },
            { key: 'description', label: 'Description', type: 'textarea', wide: true },
            { key: 'active', label: 'Active', type: 'boolean', defaultValue: true },
        ],
    },

    'fee-plans': {
        resource: 'fee-plans',
        title: 'Fee Plans',
        subtitle: 'Define fee amounts by class, term and category',
        icon: 'ListChecks',
        singular: 'Fee Plan',
        stats: [
            { label: 'Plans', icon: 'ListChecks', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Plan value', icon: 'Coins', value: (rows) => money(sumNumbers(rows, 'amount')), tone: 'info' },
            { label: 'Active', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => row.active === true), tone: 'success' },
        ],
        searchPlaceholder: 'Search by code, name or frequency...',
        columns: [
            { key: 'code', label: 'Code' },
            { key: 'name', label: 'Plan' },
            { key: 'frequency', label: 'Frequency', format: 'badge' },
            { key: 'amount', label: 'Amount', format: 'currency' },
            { key: 'dueDate', label: 'Due date', format: 'date' },
            { key: 'active', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'code', label: 'Code', required: true, placeholder: 'e.g. F1-T1-TUITION' },
            { key: 'name', label: 'Name', required: true },
            { key: 'categoryId', label: 'Category', type: 'reference', reference: { resource: 'fee-categories', label: labelOf('name', 'code') } },
            { key: 'classId', label: 'Class', type: 'reference', reference: { resource: 'classes', label: classLabel } },
            { key: 'termId', label: 'Term', type: 'reference', reference: { resource: 'terms', label: labelOf('name', 'code') } },
            { key: 'amount', label: 'Amount', type: 'number', required: true },
            { key: 'frequency', label: 'Frequency', type: 'select', options: ['ONCE', 'MONTHLY', 'TERMLY', 'YEARLY'], defaultValue: 'TERMLY' },
            { key: 'dueDate', label: 'Due date', type: 'date' },
            { key: 'active', label: 'Active', type: 'boolean', defaultValue: true },
        ],
    },

    invoices: {
        resource: 'invoices',
        title: 'Invoices',
        subtitle: 'Issue and track student fee invoices',
        icon: 'Invoice',
        singular: 'Invoice',
        stats: [
            { label: 'Invoiced', icon: 'Invoice', value: (rows) => money(sumNumbers(rows, 'totalAmount')), tone: 'primary' },
            { label: 'Collected', icon: 'CheckCircle', value: (rows) => money(sumNumbers(rows, 'paidAmount')), tone: 'success' },
            { label: 'Outstanding', icon: 'Hourglass', value: (rows) => money(sumNumbers(rows, 'totalAmount') - sumNumbers(rows, 'paidAmount')), tone: 'warning' },
            { label: 'Overdue', icon: 'Warning', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'OVERDUE'), tone: 'danger' },
        ],
        searchPlaceholder: 'Search by invoice number, status or due date...',
        columns: [
            { key: 'invoiceNo', label: 'Invoice', render: (row) => <span className="font-semibold text-gray-900">{asText(row.invoiceNo)}</span> },
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span> },
            { key: 'totalAmount', label: 'Amount', format: 'currency' },
            { key: 'paidAmount', label: 'Paid', format: 'currency' },
            { key: 'dueDate', label: 'Due', format: 'date' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'invoiceNo', label: 'Invoice number', required: true, placeholder: 'e.g. INV-2026-001' },
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'feePlanId', label: 'Fee plan', type: 'reference', reference: { resource: 'fee-plans', label: labelOf('name', 'code') } },
            { key: 'issueDate', label: 'Issue date', type: 'date' },
            { key: 'dueDate', label: 'Due date', type: 'date' },
            { key: 'totalAmount', label: 'Total amount', type: 'number', required: true },
            { key: 'paidAmount', label: 'Paid amount', type: 'number', defaultValue: 0 },
            { key: 'status', label: 'Status', type: 'select', options: ['DRAFT', 'ISSUED', 'PARTIAL', 'PAID', 'OVERDUE', 'CANCELLED'], defaultValue: 'ISSUED' },
            { key: 'remarks', label: 'Remarks', type: 'textarea', wide: true },
        ],
    },

    'invoice-items': {
        resource: 'invoice-items',
        title: 'Invoice Items',
        subtitle: 'Line items on fee invoices',
        icon: 'Receipt',
        singular: 'Invoice Item',
        columns: [
            { key: 'invoiceId', label: 'Invoice', render: (row) => <span className="font-mono text-xs">{asText(row.invoiceId).slice(0, 8)}</span> },
            { key: 'description', label: 'Description' },
            { key: 'quantity', label: 'Qty', format: 'number' },
            { key: 'unitAmount', label: 'Unit', format: 'currency' },
            { key: 'amount', label: 'Amount', format: 'currency' },
        ],
        fields: [
            { key: 'invoiceId', label: 'Invoice', type: 'reference', reference: { resource: 'invoices', label: labelOf('invoiceNo') }, required: true },
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'categoryId', label: 'Category', type: 'reference', reference: { resource: 'fee-categories', label: labelOf('name', 'code') } },
            { key: 'description', label: 'Description' },
            { key: 'quantity', label: 'Quantity', type: 'number', defaultValue: 1 },
            { key: 'unitAmount', label: 'Unit amount', type: 'number' },
            { key: 'amount', label: 'Amount', type: 'number' },
        ],
    },

    'pocket-cards': {
        resource: 'pocket-cards',
        title: 'Pocket Money Cards',
        subtitle: 'School cards loaded and controlled by parents',
        icon: 'CreditCard',
        singular: 'Pocket Card',
        stats: [
            { label: 'Cards', icon: 'CreditCard', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Total balance', icon: 'Wallet', value: (rows) => money(sumNumbers(rows, 'balance')), tone: 'info' },
            { label: 'Active', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'ACTIVE'), tone: 'success' },
        ],
        searchPlaceholder: 'Search by card UID or status...',
        columns: [
            { key: 'cardUid', label: 'Card UID', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.cardUid)}</span> },
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span> },
            { key: 'balance', label: 'Balance', format: 'currency' },
            { key: 'dailyLimit', label: 'Daily limit', format: 'currency' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'cardUid', label: 'Card UID (NFC/RFID/QR)', placeholder: 'e.g. CARD-ABCD1234' },
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'balance', label: 'Opening balance', type: 'number', defaultValue: 0 },
            { key: 'dailyLimit', label: 'Daily limit', type: 'number', defaultValue: 0 },
            { key: 'weeklyLimit', label: 'Weekly limit', type: 'number', defaultValue: 0 },
            { key: 'monthlyLimit', label: 'Monthly limit', type: 'number', defaultValue: 0 },
            { key: 'status', label: 'Status', type: 'select', options: ['ACTIVE', 'FROZEN', 'LOST', 'CLOSED'], defaultValue: 'ACTIVE' },
        ],
    },

    'spending-limits': {
        resource: 'spending-limits',
        title: 'Spending Limits',
        subtitle: 'Parent-set daily, weekly and per-category limits',
        icon: 'Shield',
        singular: 'Spending Limit',
        columns: [
            { key: 'category', label: 'Category', format: 'badge' },
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentId).slice(0, 8)}</span> },
            { key: 'dailyLimit', label: 'Daily', format: 'currency' },
            { key: 'weeklyLimit', label: 'Weekly', format: 'currency' },
            { key: 'monthlyLimit', label: 'Monthly', format: 'currency' },
            { key: 'active', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'cardId', label: 'Card', type: 'reference', reference: { resource: 'pocket-cards', label: labelOf('cardUid') }, required: true },
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'category', label: 'Category', required: true, placeholder: 'e.g. SNACKS' },
            { key: 'dailyLimit', label: 'Daily limit', type: 'number' },
            { key: 'weeklyLimit', label: 'Weekly limit', type: 'number' },
            { key: 'monthlyLimit', label: 'Monthly limit', type: 'number' },
            { key: 'active', label: 'Active', type: 'boolean', defaultValue: true },
        ],
    },

    'pocket-transactions': {
        resource: 'pocket-transactions',
        title: 'Card Transactions',
        subtitle: 'Top-ups, store purchases and refunds ledger',
        icon: 'ArrowsLeftRight',
        singular: 'Transaction',
        stats: [
            { label: 'Top-ups', icon: 'Plus', value: (rows) => money(sumNumbers(rows.filter((row) => asText(row.type) === 'TOPUP'), 'amount')), tone: 'success' },
            { label: 'Spent', icon: 'Minus', value: (rows) => money(sumNumbers(rows.filter((row) => asText(row.type) === 'PURCHASE'), 'amount')), tone: 'warning' },
            { label: 'Transactions', icon: 'Receipt', value: (_rows, total) => total, tone: 'primary' },
        ],
        searchPlaceholder: 'Search by reference, type or category...',
        columns: [
            { key: 'reference', label: 'Reference', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.reference)}</span> },
            { key: 'type', label: 'Type', format: 'badge' },
            { key: 'category', label: 'Category' },
            { key: 'amount', label: 'Amount', format: 'currency' },
            { key: 'balanceAfter', label: 'Balance after', format: 'currency' },
            { key: 'description', label: 'Description' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'cardId', label: 'Card', type: 'reference', reference: { resource: 'pocket-cards', label: labelOf('cardUid') }, required: true },
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'type', label: 'Type', type: 'select', options: ['TOPUP', 'PURCHASE', 'REFUND', 'ADJUSTMENT'], defaultValue: 'TOPUP' },
            { key: 'amount', label: 'Amount', type: 'number', required: true },
            { key: 'category', label: 'Category' },
            { key: 'description', label: 'Description', type: 'textarea', wide: true },
        ],
    },

    'store-items': {
        resource: 'store-items',
        title: 'Store Items',
        subtitle: 'Products sold to students through the school store',
        icon: 'Basket',
        singular: 'Store Item',
        stats: [
            { label: 'Products', icon: 'Basket', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Low stock', icon: 'Warning', value: (rows) => countWhere(rows, (row) => Number(row.stockQuantity) <= Number(row.reorderLevel)), tone: 'warning' },
            { label: 'Stock value', icon: 'Coins', value: (rows) => money(rows.reduce((sum, row) => sum + Number(row.price || 0) * Number(row.stockQuantity || 0), 0)), tone: 'info' },
        ],
        searchPlaceholder: 'Search by SKU, name or category...',
        columns: [
            { key: 'sku', label: 'SKU', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.sku)}</span> },
            { key: 'name', label: 'Item', render: (row) => <span className="font-semibold text-gray-900">{asText(row.name)}</span> },
            { key: 'category', label: 'Category', format: 'badge' },
            { key: 'price', label: 'Price', format: 'currency' },
            { key: 'stockQuantity', label: 'Stock', format: 'number' },
            { key: 'active', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'sku', label: 'SKU', required: true, placeholder: 'e.g. SNACK-001' },
            { key: 'name', label: 'Item name', required: true },
            { key: 'category', label: 'Category', placeholder: 'e.g. SNACKS' },
            { key: 'price', label: 'Price', type: 'number', required: true },
            { key: 'stockQuantity', label: 'Stock quantity', type: 'number', defaultValue: 0 },
            { key: 'reorderLevel', label: 'Reorder level', type: 'number', defaultValue: 10 },
            { key: 'unit', label: 'Unit', defaultValue: 'PIECE' },
            { key: 'location', label: 'Shelf / location' },
            { key: 'active', label: 'Active', type: 'boolean', defaultValue: true },
        ],
    },

    'store-terminals': {
        resource: 'store-terminals',
        title: 'Store Terminals',
        subtitle: 'NFC/RFID/QR reader terminals registered for POS',
        icon: 'Scan',
        singular: 'Terminal',
        columns: [
            { key: 'code', label: 'Code', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.code)}</span> },
            { key: 'name', label: 'Terminal' },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status', format: 'badge' },
            { key: 'lastSeenAt', label: 'Last seen', format: 'date' },
        ],
        fields: [
            { key: 'code', label: 'Terminal code', required: true, placeholder: 'e.g. POS-01' },
            { key: 'name', label: 'Name', required: true },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status', type: 'select', options: ['ACTIVE', 'INACTIVE', 'MAINTENANCE'], defaultValue: 'ACTIVE' },
        ],
    },

    'inventory-items': {
        resource: 'inventory-items',
        title: 'Inventory',
        subtitle: 'School-wide stock beyond the student store',
        icon: 'Package',
        singular: 'Inventory Item',
        stats: [
            { label: 'Items', icon: 'Package', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Below reorder', icon: 'Warning', value: (rows) => countWhere(rows, (row) => Number(row.quantity) <= Number(row.reorderLevel)), tone: 'warning' },
            { label: 'Stock value', icon: 'Coins', value: (rows) => money(rows.reduce((sum, row) => sum + Number(row.unitCost || 0) * Number(row.quantity || 0), 0)), tone: 'info' },
        ],
        searchPlaceholder: 'Search by SKU, name or supplier...',
        columns: [
            { key: 'sku', label: 'SKU' },
            { key: 'name', label: 'Item' },
            { key: 'category', label: 'Category', format: 'badge' },
            { key: 'quantity', label: 'Quantity', format: 'number' },
            { key: 'reorderLevel', label: 'Reorder at', format: 'number' },
            { key: 'supplier', label: 'Supplier' },
        ],
        fields: [
            { key: 'sku', label: 'SKU', required: true },
            { key: 'name', label: 'Item name', required: true },
            { key: 'category', label: 'Category' },
            { key: 'quantity', label: 'Quantity', type: 'number', defaultValue: 0 },
            { key: 'reorderLevel', label: 'Reorder level', type: 'number', defaultValue: 10 },
            { key: 'unitCost', label: 'Unit cost', type: 'number' },
            { key: 'location', label: 'Location' },
            { key: 'supplier', label: 'Supplier' },
            { key: 'active', label: 'Active', type: 'boolean', defaultValue: true },
        ],
    },

    'stock-movements': {
        resource: 'stock-movements',
        title: 'Stock Movements',
        subtitle: 'Receipts, issues and adjustments audit trail',
        icon: 'Truck',
        singular: 'Movement',
        columns: [
            { key: 'reference', label: 'Reference', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.reference)}</span> },
            { key: 'itemId', label: 'Item', render: (row) => <span>{asText(row.itemName) || asText(row.itemId).slice(0, 8)}</span> },
            { key: 'type', label: 'Type', format: 'badge' },
            { key: 'quantity', label: 'Quantity', format: 'number' },
            { key: 'reason', label: 'Reason' },
            { key: 'performedBy', label: 'Recorded by' },
        ],
        fields: [
            { key: 'itemId', label: 'Inventory item', type: 'reference', reference: { resource: 'inventory-items', label: labelOf('name', 'sku') }, required: true },
            { key: 'type', label: 'Type', type: 'select', options: ['IN', 'OUT', 'ADJUST'], defaultValue: 'IN', required: true },
            { key: 'quantity', label: 'Quantity', type: 'number', required: true },
            { key: 'reason', label: 'Reason' },
        ],
    },

    'purchase-requests': {
        resource: 'purchase-requests',
        title: 'Purchase Requests',
        subtitle: 'Department requests, approvals and receiving',
        icon: 'SealCheck',
        singular: 'Purchase Request',
        stats: [
            { label: 'Pending', icon: 'Hourglass', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'PENDING'), tone: 'warning' },
            { label: 'Approved', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'APPROVED'), tone: 'success' },
            { label: 'Requests', icon: 'SealCheck', value: (_rows, total) => total, tone: 'primary' },
        ],
        searchPlaceholder: 'Search by reference, purpose or status...',
        columns: [
            { key: 'reference', label: 'Reference', render: (row) => <span className="font-semibold text-gray-900">{asText(row.reference)}</span> },
            { key: 'departmentId', label: 'Department', render: (row) => <span>{asText(row.departmentName) || asText(row.departmentId).slice(0, 8)}</span> },
            { key: 'purpose', label: 'Purpose' },
            { key: 'quantity', label: 'Qty', format: 'number' },
            { key: 'status', label: 'Status', format: 'badge' },
            { key: 'approvedBy', label: 'Approved by' },
        ],
        fields: [
            { key: 'reference', label: 'Reference', required: true, placeholder: 'e.g. PR-2026-001' },
            { key: 'departmentId', label: 'Department', type: 'reference', reference: { resource: 'departments', label: labelOf('name', 'code') } },
            { key: 'itemId', label: 'Inventory item', type: 'reference', reference: { resource: 'inventory-items', label: labelOf('name', 'sku') } },
            { key: 'quantity', label: 'Quantity', type: 'number', required: true },
            { key: 'purpose', label: 'Purpose', type: 'textarea', wide: true },
            { key: 'status', label: 'Status', type: 'select', options: ['PENDING', 'APPROVED', 'REJECTED', 'RECEIVED', 'CANCELLED'], defaultValue: 'PENDING' },
        ],
    },

    departments: {
        resource: 'departments',
        title: 'Departments',
        subtitle: 'School departments, heads and budgets',
        icon: 'Buildings',
        singular: 'Department',
        columns: [
            { key: 'code', label: 'Code' },
            { key: 'name', label: 'Department', render: (row) => <span className="font-semibold text-gray-900">{asText(row.name)}</span> },
            { key: 'headTeacherId', label: 'Head', render: (row) => <span>{asText(row.headName) || asText(row.headTeacherId).slice(0, 8)}</span> },
            { key: 'budget', label: 'Budget', format: 'currency' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'code', label: 'Code', required: true, placeholder: 'e.g. SCIENCE' },
            { key: 'name', label: 'Name', required: true },
            { key: 'headTeacherId', label: 'Head of department', type: 'reference', reference: { resource: 'teachers', label: teacherLabel } },
            { key: 'budget', label: 'Budget', type: 'number' },
            { key: 'status', label: 'Status', type: 'select', options: ['ACTIVE', 'INACTIVE'], defaultValue: 'ACTIVE' },
        ],
    },

    'school-tasks': {
        resource: 'school-tasks',
        title: 'School Tasks',
        subtitle: 'Automated and manual work queue for all staff',
        icon: 'ListChecks',
        singular: 'Task',
        stats: [
            { label: 'Open', icon: 'Hourglass', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'OPEN'), tone: 'warning' },
            { label: 'Completed', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'COMPLETED'), tone: 'success' },
            { label: 'High priority', icon: 'Flag', value: (rows) => countWhere(rows, (row) => asText(row.priority) === 'HIGH'), tone: 'danger' },
        ],
        searchPlaceholder: 'Search by title, status or priority...',
        columns: [
            { key: 'title', label: 'Task', render: (row) => <span className="font-semibold text-gray-900">{asText(row.title)}</span> },
            { key: 'departmentId', label: 'Department', render: (row) => <span>{asText(row.departmentName) || asText(row.departmentId).slice(0, 8)}</span> },
            { key: 'priority', label: 'Priority', format: 'badge' },
            { key: 'dueDate', label: 'Due', format: 'date' },
            { key: 'status', label: 'Status', format: 'badge' },
            { key: 'completedAt', label: 'Completed', format: 'date' },
        ],
        fields: [
            { key: 'title', label: 'Title', required: true },
            { key: 'description', label: 'Description', type: 'textarea', wide: true },
            { key: 'assignedTo', label: 'Assigned to', type: 'reference', reference: { resource: 'teachers', label: teacherLabel } },
            { key: 'departmentId', label: 'Department', type: 'reference', reference: { resource: 'departments', label: labelOf('name', 'code') } },
            { key: 'dueDate', label: 'Due date', type: 'date' },
            { key: 'priority', label: 'Priority', type: 'select', options: ['LOW', 'NORMAL', 'HIGH', 'URGENT'], defaultValue: 'NORMAL' },
            { key: 'status', label: 'Status', type: 'select', options: ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'], defaultValue: 'OPEN' },
        ],
    },

    books: {
        resource: 'books',
        title: 'Library Books',
        subtitle: 'Physical and digital library catalogue',
        icon: 'BookOpen',
        singular: 'Book',
        stats: [
            { label: 'Titles', icon: 'BookOpen', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Copies', icon: 'Stack', value: (rows) => sumNumbers(rows, 'copiesTotal'), tone: 'info' },
            { label: 'Available', icon: 'CheckCircle', value: (rows) => sumNumbers(rows, 'copiesAvailable'), tone: 'success' },
            { label: 'Digital', icon: 'Laptop', value: (rows) => countWhere(rows, (row) => Boolean(asText(row.digitalUrl))), tone: 'warning' },
        ],
        searchPlaceholder: 'Search by title, author or ISBN...',
        columns: [
            { key: 'title', label: 'Title', render: (row) => <span className="font-semibold text-gray-900">{asText(row.title)}</span> },
            { key: 'author', label: 'Author' },
            { key: 'subject', label: 'Subject' },
            { key: 'copiesAvailable', label: 'Available', format: 'number' },
            { key: 'digitalUrl', label: 'Digital', render: (row) => (asText(row.digitalUrl) ? <Icon name="CheckCircle" size={18} className="text-green-600" /> : '—') },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'isbn', label: 'ISBN', required: true },
            { key: 'title', label: 'Title', required: true },
            { key: 'author', label: 'Author' },
            { key: 'subject', label: 'Subject' },
            { key: 'department', label: 'Department' },
            { key: 'location', label: 'Shelf location' },
            { key: 'copiesTotal', label: 'Total copies', type: 'number', defaultValue: 1 },
            { key: 'copiesAvailable', label: 'Available copies', type: 'number', defaultValue: 1 },
            { key: 'digitalUrl', label: 'Digital copy URL' },
            { key: 'status', label: 'Status', type: 'select', options: ['ACTIVE', 'INACTIVE', 'ARCHIVED'], defaultValue: 'ACTIVE' },
        ],
    },

    'book-copies': {
        resource: 'book-copies',
        title: 'Book Copies',
        subtitle: 'Barcoded physical copies in the library',
        icon: 'Barcode',
        singular: 'Book Copy',
        columns: [
            { key: 'barcode', label: 'Barcode', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.barcode)}</span> },
            { key: 'bookId', label: 'Book', render: (row) => <span>{asText(row.bookTitle) || asText(row.bookId).slice(0, 8)}</span> },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'barcode', label: 'Barcode', required: true },
            { key: 'bookId', label: 'Book', type: 'reference', reference: { resource: 'books', label: labelOf('title', 'isbn') }, required: true },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status', type: 'select', options: ['AVAILABLE', 'BORROWED', 'LOST', 'DAMAGED'], defaultValue: 'AVAILABLE' },
        ],
    },

    'book-loans': {
        resource: 'book-loans',
        title: 'Book Loans',
        subtitle: 'Checkouts, returns, renewals and fines',
        icon: 'BookmarkSimple',
        singular: 'Book Loan',
        stats: [
            { label: 'Borrowed', icon: 'BookmarkSimple', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'BORROWED'), tone: 'primary' },
            { label: 'Overdue', icon: 'Warning', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'OVERDUE'), tone: 'danger' },
            { label: 'Fines', icon: 'Coins', value: (rows) => money(sumNumbers(rows, 'fineAmount')), tone: 'warning' },
        ],
        searchPlaceholder: 'Search by student, status or due date...',
        columns: [
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span> },
            { key: 'bookId', label: 'Book', render: (row) => <span>{asText(row.bookTitle) || asText(row.bookId).slice(0, 8)}</span> },
            { key: 'borrowedAt', label: 'Borrowed', format: 'date' },
            { key: 'dueAt', label: 'Due', format: 'date' },
            { key: 'fineAmount', label: 'Fine', format: 'currency' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'copyId', label: 'Copy', type: 'reference', reference: { resource: 'book-copies', label: labelOf('barcode') }, required: true },
            { key: 'bookId', label: 'Book', type: 'reference', reference: { resource: 'books', label: labelOf('title', 'isbn') }, required: true },
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'dueAt', label: 'Due at' },
            { key: 'status', label: 'Status', type: 'select', options: ['BORROWED', 'RETURNED', 'OVERDUE', 'LOST'], defaultValue: 'BORROWED' },
        ],
    },

    'book-reservations': {
        resource: 'book-reservations',
        title: 'Book Reservations',
        subtitle: 'Reserve a book before it is returned',
        icon: 'Bookmark',
        singular: 'Reservation',
        columns: [
            { key: 'bookId', label: 'Book', render: (row) => <span>{asText(row.bookTitle) || asText(row.bookId).slice(0, 8)}</span> },
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span> },
            { key: 'reservedAt', label: 'Reserved', format: 'date' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'bookId', label: 'Book', type: 'reference', reference: { resource: 'books', label: labelOf('title', 'isbn') }, required: true },
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'status', label: 'Status', type: 'select', options: ['ACTIVE', 'CANCELLED', 'FULFILLED'], defaultValue: 'ACTIVE' },
        ],
    },

    'lab-sessions': {
        resource: 'lab-sessions',
        title: 'Computer Lab',
        subtitle: 'Student study sessions with digital library access',
        icon: 'Code',
        singular: 'Lab Session',
        stats: [
            { label: 'Bookings', icon: 'Code', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Booked', icon: 'Hourglass', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'BOOKED'), tone: 'success' },
            { label: 'Labs', icon: 'Building', value: (rows) => uniqueCount(rows, 'lab'), tone: 'info' },
        ],
        searchPlaceholder: 'Search by reference, lab or seat...',
        columns: [
            { key: 'reference', label: 'Reference', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.reference)}</span> },
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span> },
            { key: 'lab', label: 'Lab', format: 'badge' },
            { key: 'seat', label: 'Seat' },
            { key: 'startAt', label: 'Start', format: 'date' },
            { key: 'purpose', label: 'Purpose' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'bookId', label: 'Digital book', type: 'reference', reference: { resource: 'books', label: labelOf('title', 'isbn') } },
            { key: 'lab', label: 'Lab', required: true, placeholder: 'e.g. LAB-A' },
            { key: 'seat', label: 'Seat', placeholder: 'e.g. 12' },
            { key: 'startAt', label: 'Start at' },
            { key: 'endAt', label: 'End at' },
            { key: 'purpose', label: 'Purpose', type: 'textarea', wide: true },
        ],
    },

    periods: {
        resource: 'periods',
        title: 'Periods',
        subtitle: 'Lesson time slots used by the timetable generator',
        icon: 'Clock',
        singular: 'Period',
        stats: [
            { label: 'Periods', icon: 'Clock', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Active', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => row.active === true), tone: 'success' },
        ],
        searchPlaceholder: 'Search by name, code or time...',
        columns: [
            { key: 'code', label: 'Code', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.code)}</span> },
            { key: 'name', label: 'Period', render: (row) => <span className="font-semibold text-gray-900">{asText(row.name)}</span> },
            { key: 'startTime', label: 'From' },
            { key: 'endTime', label: 'To' },
            { key: 'sortOrder', label: 'Order', format: 'number' },
            { key: 'active', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'code', label: 'Code', required: true, placeholder: 'e.g. P1' },
            { key: 'name', label: 'Name', required: true, placeholder: 'e.g. First period' },
            { key: 'startTime', label: 'Start time', type: 'time', required: true },
            { key: 'endTime', label: 'End time', type: 'time', required: true },
            { key: 'sortOrder', label: 'Order', type: 'number', defaultValue: 1 },
            { key: 'active', label: 'Active', type: 'boolean', defaultValue: true },
        ],
    },

    exams: {
        resource: 'exams',
        title: 'Exams',
        subtitle: 'Plan exams, schedules and the resulting report cards',
        icon: 'ClipboardText',
        singular: 'Exam',
        stats: [
            { label: 'Exams', icon: 'ClipboardText', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Scheduled', icon: 'CalendarCheck', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'SCHEDULED'), tone: 'success' },
            { label: 'Planned', icon: 'Hourglass', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'PLANNED'), tone: 'warning' },
        ],
        searchPlaceholder: 'Search by exam name, type or term...',
        columns: [
            { key: 'code', label: 'Code' },
            { key: 'name', label: 'Exam', render: (row) => <span className="font-semibold text-gray-900">{asText(row.name)}</span> },
            { key: 'examType', label: 'Type', format: 'badge' },
            { key: 'term', label: 'Term', format: 'badge' },
            { key: 'startDate', label: 'From', format: 'date' },
            { key: 'endDate', label: 'To', format: 'date' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'code', label: 'Code', required: true, placeholder: 'e.g. EX-2026-1' },
            { key: 'name', label: 'Exam name', required: true },
            { key: 'examType', label: 'Exam type', required: true, placeholder: 'e.g. FINAL' },
            { key: 'term', label: 'Term', type: 'select', options: ['TERM_1', 'TERM_2', 'TERM_3'], defaultValue: 'TERM_1' },
            { key: 'startDate', label: 'Start date', type: 'date' },
            { key: 'endDate', label: 'End date', type: 'date' },
            { key: 'status', label: 'Status', type: 'select', options: ['PLANNED', 'SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'], defaultValue: 'PLANNED' },
            { key: 'remarks', label: 'Remarks', type: 'textarea', wide: true },
        ],
    },

    'exam-sessions': {
        resource: 'exam-sessions',
        title: 'Exam Timetable',
        subtitle: 'Scheduled papers by class, subject, date and invigilator',
        icon: 'CalendarBlank',
        singular: 'Exam Session',
        stats: [
            { label: 'Papers', icon: 'CalendarBlank', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Scheduled', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'SCHEDULED'), tone: 'success' },
            { label: 'Rooms', icon: 'DoorOpen', value: (rows) => uniqueCount(rows, 'room'), tone: 'info' },
        ],
        searchPlaceholder: 'Search by date, room or status...',
        columns: [
            { key: 'examDate', label: 'Date', format: 'date' },
            { key: 'classId', label: 'Class', render: (row) => <span>{asText(row.className) || asText(row.classId).slice(0, 8)}</span> },
            { key: 'subjectId', label: 'Subject', render: (row) => <span>{asText(row.subjectName) || asText(row.subjectId).slice(0, 8)}</span> },
            { key: 'startTime', label: 'Time', render: (row) => `${asText(row.startTime)} – ${asText(row.endTime)}` },
            { key: 'room', label: 'Room' },
            { key: 'teacherId', label: 'Invigilator', render: (row) => <span>{asText(row.teacherName) || asText(row.teacherId).slice(0, 8)}</span> },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'examId', label: 'Exam', type: 'reference', reference: { resource: 'exams', label: labelOf('name', 'code') }, required: true },
            { key: 'classId', label: 'Class', type: 'reference', reference: { resource: 'classes', label: classLabel }, required: true },
            { key: 'subjectId', label: 'Subject', type: 'reference', reference: { resource: 'subjects', label: subjectLabel }, required: true },
            { key: 'teacherId', label: 'Invigilator', type: 'reference', reference: { resource: 'teachers', label: teacherLabel } },
            { key: 'examDate', label: 'Date', type: 'date', required: true },
            { key: 'startTime', label: 'Start time', type: 'time', required: true },
            { key: 'endTime', label: 'End time', type: 'time', required: true },
            { key: 'room', label: 'Room' },
            { key: 'status', label: 'Status', type: 'select', options: ['SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'], defaultValue: 'SCHEDULED' },
        ],
    },

    'duty-assignments': {
        resource: 'duty-assignments',
        title: 'Teacher Duties',
        subtitle: 'Automated and manual teacher duty rosters',
        icon: 'UserList',
        singular: 'Duty Assignment',
        stats: [
            { label: 'Scheduled', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'SCHEDULED'), tone: 'success' },
            { label: 'Cancelled', icon: 'Prohibit', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'CANCELLED'), tone: 'danger' },
            { label: 'Duty types', icon: 'Stack', value: (rows) => uniqueCount(rows, 'dutyType'), tone: 'info' },
        ],
        searchPlaceholder: 'Search by duty, date or location...',
        columns: [
            { key: 'dutyDate', label: 'Date', format: 'date' },
            { key: 'teacherId', label: 'Teacher', render: (row) => <span>{asText(row.teacherName) || asText(row.teacherId).slice(0, 8)}</span> },
            { key: 'dutyType', label: 'Duty', format: 'badge' },
            { key: 'startTime', label: 'Time', render: (row) => `${asText(row.startTime)} – ${asText(row.endTime)}` },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'teacherId', label: 'Teacher', type: 'reference', reference: { resource: 'teachers', label: teacherLabel }, required: true },
            { key: 'dutyType', label: 'Duty type', required: true, placeholder: 'e.g. ASSEMBLY' },
            { key: 'dutyDate', label: 'Date', type: 'date', required: true },
            { key: 'startTime', label: 'Start time', type: 'time' },
            { key: 'endTime', label: 'End time', type: 'time' },
            { key: 'location', label: 'Location' },
            { key: 'term', label: 'Term' },
            { key: 'status', label: 'Status', type: 'select', options: ['SCHEDULED', 'COMPLETED', 'CANCELLED'], defaultValue: 'SCHEDULED' },
            { key: 'notes', label: 'Notes', type: 'textarea', wide: true },
        ],
    },

    'report-cards': {
        resource: 'report-cards',
        title: 'Report Cards',
        subtitle: 'Ranked term results generated from uploaded marks',
        icon: 'Medal',
        singular: 'Report Card',
        stats: [
            { label: 'Cards', icon: 'Medal', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Published', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'PUBLISHED'), tone: 'success' },
            { label: 'Drafts', icon: 'Exam', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'DRAFT'), tone: 'warning' },
        ],
        searchPlaceholder: 'Search by reference, grade or status...',
        columns: [
            { key: 'reference', label: 'Reference', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.reference)}</span> },
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span> },
            { key: 'term', label: 'Term', format: 'badge' },
            { key: 'average', label: 'Average', format: 'number' },
            { key: 'grade', label: 'Grade', format: 'badge' },
            { key: 'position', label: 'Position', render: (row) => `${asText(row.position)} / ${asText(row.classSize)}` },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'examId', label: 'Exam', type: 'reference', reference: { resource: 'exams', label: labelOf('name', 'code') } },
            { key: 'classId', label: 'Class', type: 'reference', reference: { resource: 'classes', label: classLabel } },
            { key: 'term', label: 'Term' },
            { key: 'status', label: 'Status', type: 'select', options: ['DRAFT', 'PUBLISHED', 'ARCHIVED'], defaultValue: 'DRAFT' },
            { key: 'remarks', label: 'Remarks', type: 'textarea', wide: true },
        ],
    },

    'staff-leaves': {
        resource: 'staff-leaves',
        title: 'Staff Leave',
        subtitle: 'Leave requests that automatically update duty coverage',
        icon: 'Airplane',
        singular: 'Leave Request',
        stats: [
            { label: 'Pending', icon: 'Hourglass', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'PENDING'), tone: 'warning' },
            { label: 'Approved', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'APPROVED'), tone: 'success' },
        ],
        searchPlaceholder: 'Search by leave type or status...',
        columns: [
            { key: 'teacherId', label: 'Staff', render: (row) => <span>{asText(row.teacherName) || asText(row.teacherId).slice(0, 8)}</span> },
            { key: 'leaveType', label: 'Type', format: 'badge' },
            { key: 'startDate', label: 'From', format: 'date' },
            { key: 'endDate', label: 'To', format: 'date' },
            { key: 'reason', label: 'Reason' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'teacherId', label: 'Staff member', type: 'reference', reference: { resource: 'teachers', label: teacherLabel }, required: true },
            { key: 'leaveType', label: 'Leave type', required: true, placeholder: 'e.g. SICK' },
            { key: 'startDate', label: 'Start date', type: 'date', required: true },
            { key: 'endDate', label: 'End date', type: 'date', required: true },
            { key: 'reason', label: 'Reason', type: 'textarea', wide: true },
            { key: 'status', label: 'Status', type: 'select', options: ['PENDING', 'APPROVED', 'REJECTED'], defaultValue: 'PENDING' },
        ],
    },

    'parent-letters': {
        resource: 'parent-letters',
        title: 'Parent Letters',
        subtitle: 'Automatically generated end-of-term guardian letters',
        icon: 'EnvelopeOpen',
        singular: 'Parent Letter',
        stats: [
            { label: 'Letters', icon: 'EnvelopeOpen', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Generated', icon: 'CheckCircle', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'GENERATED'), tone: 'success' },
            { label: 'Need attention', icon: 'Warning', value: (rows) => countWhere(rows, (row) => asText(row.requirements).includes('Remedial')), tone: 'warning' },
        ],
        searchPlaceholder: 'Search by reference, student, term or title...',
        columns: [
            { key: 'reference', label: 'Reference', render: (row) => <span className="font-mono text-xs font-semibold text-gray-900">{asText(row.reference)}</span> },
            { key: 'studentId', label: 'Student', render: (row) => <span>{asText(row.studentName) || asText(row.studentId).slice(0, 8)}</span> },
            { key: 'term', label: 'Term', format: 'badge' },
            { key: 'title', label: 'Letter', render: (row) => <span className="font-semibold text-gray-900">{asText(row.title)}</span> },
            { key: 'generatedAt', label: 'Generated' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'studentId', label: 'Student', type: 'reference', reference: { resource: 'students', label: studentLabel }, required: true },
            { key: 'reportCardId', label: 'Report card', type: 'reference', reference: { resource: 'report-cards', label: labelOf('reference') } },
            { key: 'classId', label: 'Class', type: 'reference', reference: { resource: 'classes', label: classLabel } },
            { key: 'term', label: 'Term' },
            { key: 'title', label: 'Title', required: true },
            { key: 'content', label: 'Letter', type: 'textarea', wide: true },
            { key: 'requirements', label: 'Next-term requirements', type: 'textarea', wide: true },
            { key: 'status', label: 'Status', type: 'select', options: ['GENERATED', 'SENT', 'ARCHIVED'], defaultValue: 'GENERATED' },
            { key: 'generatedAt', label: 'Generated at' },
        ],
    },

    'school-events': {
        resource: 'school-events',
        title: 'School Calendar',
        subtitle: 'Events, meetings and activities across every department',
        icon: 'CalendarBlank',
        singular: 'Event',
        stats: [
            { label: 'Events', icon: 'CalendarBlank', value: (_rows, total) => total, tone: 'primary' },
            { label: 'Upcoming', icon: 'Hourglass', value: (rows) => countWhere(rows, (row) => asText(row.status) === 'SCHEDULED'), tone: 'info' },
        ],
        searchPlaceholder: 'Search by title, type or location...',
        columns: [
            { key: 'title', label: 'Event', render: (row) => <span className="font-semibold text-gray-900">{asText(row.title)}</span> },
            { key: 'eventType', label: 'Type', format: 'badge' },
            { key: 'eventDate', label: 'Date', format: 'date' },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status', format: 'badge' },
        ],
        fields: [
            { key: 'title', label: 'Title', required: true },
            { key: 'eventType', label: 'Event type', required: true, placeholder: 'e.g. MEETING' },
            { key: 'eventDate', label: 'Date', type: 'date', required: true },
            { key: 'startTime', label: 'Start time', type: 'time' },
            { key: 'endTime', label: 'End time', type: 'time' },
            { key: 'location', label: 'Location' },
            { key: 'description', label: 'Description', type: 'textarea', wide: true },
            { key: 'status', label: 'Status', type: 'select', options: ['SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'], defaultValue: 'SCHEDULED' },
        ],
    },
};
