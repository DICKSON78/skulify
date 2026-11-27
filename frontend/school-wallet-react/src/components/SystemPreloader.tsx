import Icon from './Icon';

/** Full-screen Skulify splash shown while the app authenticates a sign-in. */
export default function SystemPreloader({
    message = 'Management System',
}: {
    message?: string;
}) {
    return (
        <div className="system-preloader" role="status" aria-live="polite">
            <div className="system-preloader-card">
                <div className="system-preloader-mark">
                    <Icon name="GraduationCap" size={36} />
                </div>
                <p className="system-preloader-title">Skulify</p>
                <p className="system-preloader-subtitle">{message}</p>
                <div className="system-preloader-track">
                    <span className="system-preloader-bar" />
                </div>
            </div>
        </div>
    );
}
