import Icon from './Icon';

/** Full-screen Skulify splash shown while the app authenticates a sign-in. */
export default function SystemPreloader() {
    return (
        <div className="system-preloader" role="status" aria-live="polite" aria-label="Skulify loading">
            <div className="system-preloader-card">
                <div className="system-preloader-mark">
                    <Icon name="GraduationCap" size={36} />
                </div>
                <p className="system-preloader-title">Skulify</p>
            </div>
        </div>
    );
}
