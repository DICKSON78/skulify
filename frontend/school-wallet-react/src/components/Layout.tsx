import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import Icon from './Icon';

type NavItem = { path: string; label: string; icon: string };
type NavGroup = { label: string; items: NavItem[] };

const navGroups: NavGroup[] = [
    {
        label: 'Overview',
        items: [
            { path: '/dashboard', label: 'Dashboard', icon: 'SquaresFour' },
            { path: '/parent-portal', label: 'Parent Portal', icon: 'UsersThree' },
        ],
    },
    {
        label: 'People',
        items: [
            { path: '/students', label: 'Students', icon: 'GraduationCap' },
            { path: '/teachers', label: 'Teachers & Staff', icon: 'ChalkboardTeacher' },
        ],
    },
    {
        label: 'Academics',
        items: [
            { path: '/classes', label: 'Classes', icon: 'Buildings' },
            { path: '/teacher-assignments', label: 'Class Management', icon: 'ChalkboardTeacher' },
            { path: '/subjects', label: 'Subjects', icon: 'Books' },
            { path: '/attendance', label: 'Attendance', icon: 'CalendarCheck' },
            { path: '/grades', label: 'Grades & Exams', icon: 'Exam' },
            { path: '/timetable', label: 'Timetable', icon: 'Table' },
            { path: '/timetable-generator', label: 'Generate Timetable', icon: 'MagicWand' },
            { path: '/marks-upload', label: 'Upload Marks', icon: 'UploadSimple' },
            { path: '/periods', label: 'Periods', icon: 'Clock' },
            { path: '/exams', label: 'Exams', icon: 'ClipboardText' },
            { path: '/exam-sessions', label: 'Exam Timetable', icon: 'CalendarBlank' },
            { path: '/duty-roster', label: 'Duty Roster', icon: 'UserList' },
            { path: '/duty-assignments', label: 'Teacher Duties', icon: 'CalendarCheck' },
            { path: '/results-planning', label: 'Result Planning', icon: 'Medal' },
            { path: '/report-cards', label: 'Report Cards', icon: 'Certificate' },
            { path: '/parent-letters', label: 'Parent Letters', icon: 'EnvelopeOpen' },
            { path: '/online-tests', label: 'Online Tests', icon: 'Laptop' },
        ],
    },
    {
        label: 'Finance',
        items: [
            { path: '/fees', label: 'Fee Records', icon: 'Wallet' },
            { path: '/fee-categories', label: 'Fee Categories', icon: 'Stack' },
            { path: '/fee-plans', label: 'Fee Plans', icon: 'ListChecks' },
            { path: '/invoices', label: 'Invoices', icon: 'Invoice' },
            { path: '/pocket-money', label: 'Pocket Money', icon: 'Wallet' },
            { path: '/pocket-cards', label: 'Student Cards', icon: 'CreditCard' },
            { path: '/spending-limits', label: 'Spending Limits', icon: 'Shield' },
            { path: '/pocket-transactions', label: 'Card Transactions', icon: 'ArrowsLeftRight' },
        ],
    },
    {
        label: 'Library & Lab',
        items: [
            { path: '/library-desk', label: 'Library Desk', icon: 'BookOpenText' },
            { path: '/books', label: 'Books', icon: 'Books' },
            { path: '/book-copies', label: 'Book Copies', icon: 'Barcode' },
            { path: '/book-loans', label: 'Loans', icon: 'BookmarkSimple' },
            { path: '/book-reservations', label: 'Reservations', icon: 'Bookmark' },
            { path: '/lab-sessions', label: 'Computer Lab', icon: 'Code' },
        ],
    },
    {
        label: 'Store & Inventory',
        items: [
            { path: '/store-pos', label: 'POS Checkout', icon: 'Scan' },
            { path: '/store-items', label: 'Store Items', icon: 'Basket' },
            { path: '/store-terminals', label: 'Terminals', icon: 'Desktop' },
            { path: '/inventory-items', label: 'Inventory', icon: 'Package' },
            { path: '/stock-movements', label: 'Stock Movements', icon: 'Truck' },
            { path: '/purchase-requests', label: 'Purchase Requests', icon: 'SealCheck' },
        ],
    },
    {
        label: 'Operations',
        items: [
            { path: '/operations-hub', label: 'Operations Hub', icon: 'FlowArrow' },
            { path: '/departments', label: 'Departments', icon: 'Buildings' },
            { path: '/staff-attendance', label: 'Staff Attendance', icon: 'ClockCounterClockwise' },
            { path: '/school-documents', label: 'School Documents', icon: 'FileArrowUp' },
            { path: '/staff-leaves', label: 'Staff Leave', icon: 'Airplane' },
            { path: '/school-events', label: 'School Calendar', icon: 'CalendarBlank' },
            { path: '/school-tasks', label: 'Work Queue', icon: 'ListChecks' },
        ],
    },
    {
        label: 'Insights',
        items: [{ path: '/reports', label: 'Reports', icon: 'ChartBar' }],
    },
    {
        label: 'System',
        items: [{ path: '/settings', label: 'Settings', icon: 'GearSix' }],
    },
];

export default function Layout({ children }: { children: ReactNode }) {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [collapsedGroups, setCollapsedGroups] = useState<Record<number, boolean>>({});
    const [userMenuOpen, setUserMenuOpen] = useState(false);
    const [notificationsOpen, setNotificationsOpen] = useState(false);
    const [now, setNow] = useState(new Date());
    const desktopSidebarRef = useRef<HTMLElement | null>(null);
    const mobileSidebarRef = useRef<HTMLDivElement | null>(null);
    const location = useLocation();
    const navigate = useNavigate();
    const { user, logout, can } = useAuth();

    const initials = (user?.fullName ?? 'SK')
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part.charAt(0).toUpperCase())
        .join('');
    const roleLabel = (user?.roles ?? [])
        .map((role) => role.replace(/_/g, ' ').toLowerCase())
        .map((role) => role.charAt(0).toUpperCase() + role.slice(1))
        .join(' · ') || 'Skulify';

    const signOut = async () => {
        setUserMenuOpen(false);
        await logout();
        navigate('/login', { replace: true });
    };

    useEffect(() => {
        const timer = setInterval(() => setNow(new Date()), 1000);
        return () => clearInterval(timer);
    }, []);

    const isActive = (path: string) =>
        location.pathname === path || location.pathname.startsWith(path + '/');

    // Keep the group of the current page open (same behaviour as the MTAI admin portal).
    useEffect(() => {
        navGroups.forEach((group, index) => {
            if (group.items.some((item) => isActive(item.path))) {
                setCollapsedGroups((prev) => ({ ...prev, [index]: false }));
            }
        });
    }, [location.pathname]);

    // Close floating panels on navigation so they never cover the new page.
    useEffect(() => {
        setUserMenuOpen(false);
        setNotificationsOpen(false);
    }, [location.pathname]);

    // Keep the active link in view after navigation, group expansion, or opening the mobile menu.
    // If it is already visible we do nothing, so the sidebar never jumps unnecessarily.
    useEffect(() => {
        const frame = requestAnimationFrame(() => {
            [desktopSidebarRef.current, mobileSidebarRef.current].forEach((sidebar) => {
                if (!sidebar) return;
                const nav = sidebar.querySelector<HTMLElement>('nav');
                const activeLink = sidebar.querySelector<HTMLElement>('[data-active="true"]');
                if (!nav || !activeLink || nav.clientHeight === 0) return;

                const navRect = nav.getBoundingClientRect();
                const linkRect = activeLink.getBoundingClientRect();
                const edge = 12;
                if (linkRect.top >= navRect.top + edge && linkRect.bottom <= navRect.bottom - edge) {
                    return;
                }

                const target =
                    nav.scrollTop + linkRect.top - navRect.top - (navRect.height - linkRect.height) / 2;
                nav.scrollTo({ top: Math.max(0, target), behavior: 'smooth' });
            });
        });
        return () => cancelAnimationFrame(frame);
    }, [location.pathname, collapsedGroups, sidebarOpen]);

    const toggleGroup = (index: number) =>
        setCollapsedGroups((prev) => ({ ...prev, [index]: !prev[index] }));

    const currentLabel =
        navGroups.flatMap((group) => group.items).find((item) => isActive(item.path))?.label ||
        'Skulify';

    const renderSidebar = (mobile = false) => (
        <>
            <div className="sidebar-brand">
                <Link
                    to="/dashboard"
                    onClick={() => mobile && setSidebarOpen(false)}
                    className="sidebar-brand-link"
                >
                    <span className="sidebar-brand-mark">
                        <Icon name="GraduationCap" size={22} />
                    </span>
                    <span>
                        <span className="sidebar-brand-title">Skulify</span>
                        <span className="sidebar-brand-subtitle">Management System</span>
                    </span>
                </Link>
                <button
                    onClick={() => setSidebarOpen(false)}
                    className={`${mobile ? '' : 'hidden'} md:hidden text-white/60 hover:text-white`}
                    aria-label="Close menu"
                >
                    <Icon name="X" size={20} />
                </button>
            </div>

            <nav className="sidebar-nav sidebar-scroll">
                <div className="space-y-0.5">
                    {navGroups.map((group, groupIndex) => {
                        const visibleItems = group.items.filter((item) => can(item.path));
                        if (visibleItems.length === 0) {
                            return null;
                        }
                        const collapsed = collapsedGroups[groupIndex];
                        return (
                            <div key={group.label}>
                                <button
                                    onClick={() => toggleGroup(groupIndex)}
                                    className="sidebar-group-label"
                                >
                                    <span>{group.label}</span>
                                    <Icon
                                        name="CaretRight"
                                        size={10}
                                        className={`transition-transform duration-200 ${collapsed ? '' : 'rotate-90'}`}
                                    />
                                </button>
                                <div
                                    className={`space-y-0.5 overflow-hidden transition-all duration-200 ${
                                        collapsed ? 'max-h-0 opacity-0' : 'max-h-[500px] opacity-100'
                                    }`}
                                >
                                    {visibleItems.map((item) => {
                                        const active = isActive(item.path);
                                        return (
                                            <Link
                                                key={item.path}
                                                to={item.path}
                                                data-active={active ? 'true' : undefined}
                                                onClick={() => mobile && setSidebarOpen(false)}
                                                className="sidebar-item"
                                            >
                                                <Icon name={item.icon} size={20} className="shrink-0" />
                                                <span>{item.label}</span>
                                            </Link>
                                        );
                                    })}
                                </div>
                            </div>
                        );
                    })}
                </div>
            </nav>

            <div className="sidebar-footer">
                {can('/settings') && (
                    <Link
                        to="/settings"
                        data-active={isActive('/settings') ? 'true' : undefined}
                        onClick={() => mobile && setSidebarOpen(false)}
                        className="sidebar-item"
                    >
                        <Icon name="GearSix" size={20} className="shrink-0" />
                        <span>Settings</span>
                    </Link>
                )}
                <div className="my-2 border-t border-white/10"></div>
                <div className="sidebar-user">
                    <span className="sidebar-avatar">{initials}</span>
                    <div className="min-w-0 flex-1">
                        <p className="sidebar-user-name">{user?.fullName ?? 'Skulify'}</p>
                        <p className="sidebar-user-role">{roleLabel}</p>
                    </div>
                </div>
            </div>
        </>
    );

    return (
        <div className="app-shell">
            <div
                ref={mobileSidebarRef}
                className={`md:hidden fixed inset-y-0 left-0 z-50 flex w-[272px] sidebar-panel transition-transform duration-200 ${
                    sidebarOpen ? 'translate-x-0' : '-translate-x-full'
                }`}
            >
                {renderSidebar(true)}
            </div>
            {sidebarOpen && (
                <div
                    className="fixed inset-0 bg-slate-950/50 z-40 md:hidden"
                    onClick={() => setSidebarOpen(false)}
                />
            )}

            <aside
                ref={desktopSidebarRef}
                className="relative hidden md:flex sidebar-panel shrink-0 h-full"
            >
                {renderSidebar()}
            </aside>

            <div className="main-column flex flex-1 flex-col overflow-hidden">
                <header className="topbar">
                    <div className="topbar-left">
                        <button
                            onClick={() => setSidebarOpen(true)}
                            className="md:hidden text-white/80 hover:text-white"
                            aria-label="Open menu"
                        >
                            <Icon name="List" size={22} />
                        </button>
                        <span className="announcement-pill">
                            <span className="announcement-badge">New Update</span>
                            <span className="hidden lg:inline">Skulify automation is live</span>
                            <Link to="/operations-hub">Learn more</Link>
                        </span>
                        <span className="topbar-divider hidden xl:block"></span>
                        <span className="topbar-meta hidden xl:inline-flex">
                            <Icon name="CalendarBlank" size={16} />
                            <strong>
                                {now.toLocaleDateString('en-US', {
                                    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
                                })}
                            </strong>
                        </span>
                        <span className="topbar-meta hidden xl:inline-flex">
                            <Icon name="Clock" size={16} />
                            <strong>
                                {now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}
                            </strong>
                        </span>
                        <span className="text-sm font-semibold text-white sm:hidden">{currentLabel}</span>
                    </div>

                    <div className="topbar-right">
                        <span className="topbar-quick hidden sm:inline-flex">
                            <Icon name="Lightning" size={16} />
                            Quick Action
                        </span>
                        <div className="dropdown-anchor">
                            <button
                                onClick={() => {
                                    setNotificationsOpen((open) => !open);
                                    setUserMenuOpen(false);
                                }}
                                className="relative icon-circle"
                                aria-label="Notifications"
                                aria-expanded={notificationsOpen}
                            >
                                <Icon name="Bell" size={20} />
                                <span className="notification-badge"></span>
                            </button>
                            {notificationsOpen && (
                                <div className="dropdown-panel notification-panel">
                                    <div className="notification-header">
                                        <h3 className="notification-title">Notifications</h3>
                                        <span className="notification-link">Mark all as read</span>
                                    </div>
                                    <div className="notification-list">
                                        <div className="notification-empty">You're all caught up.</div>
                                    </div>
                                    <div className="notification-footer">
                                        <span className="notification-link">View all notifications</span>
                                    </div>
                                </div>
                            )}
                        </div>
                        <div className="dropdown-anchor">
                            <button
                                onClick={() => {
                                    setUserMenuOpen((open) => !open);
                                    setNotificationsOpen(false);
                                }}
                                className="profile-chip"
                                aria-expanded={userMenuOpen}
                            >
                                <span className="profile-avatar">{initials}</span>
                                <span className="profile-name">{user?.fullName ?? 'Skulify'}</span>
                                <Icon name="CaretDown" size={12} className="text-white/70" />
                            </button>
                            {userMenuOpen && (
                                <div className="dropdown-panel">
                                    <div className="dropdown-header">
                                        <span className="dropdown-avatar">{initials}</span>
                                        <div className="min-w-0">
                                            <p className="dropdown-name truncate">{user?.fullName ?? 'Skulify'}</p>
                                            <p className="dropdown-email truncate">{user?.username ?? 'skulify'}</p>
                                        </div>
                                    </div>
                                    <div className="dropdown-section">Current workspace</div>
                                    {can('/settings') && (
                                        <Link
                                            to="/settings"
                                            onClick={() => setUserMenuOpen(false)}
                                            className={`dropdown-item ${isActive('/settings') ? 'selected' : ''}`}
                                        >
                                            <Icon name="GearSix" size={18} />
                                            Settings
                                            {isActive('/settings') && (
                                                <Icon name="Check" size={14} weight="fill" className="check" />
                                            )}
                                        </Link>
                                    )}
                                    <div className="dropdown-divider"></div>
                                    <button
                                        type="button"
                                        onClick={() => void signOut()}
                                        className="dropdown-item"
                                    >
                                        <Icon name="SignOut" size={18} />
                                        Sign out
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                </header>

                <main className="content-area content-sheet">
                    {children}
                </main>
            </div>
        </div>
    );
}
