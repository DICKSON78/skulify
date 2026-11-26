/** Registers the production service worker so Skulify is installable. */
export function registerPwa(): void {
  if (!import.meta.env.PROD || typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
    return;
  }

  const register = () => {
    void navigator.serviceWorker
      .register('/sw.js', { scope: '/' })
      .then((registration) => {
        // Pick up metadata/icon changes even when an older worker is installed.
        void registration.update();
      })
      .catch(() => {
        // PWA support is progressive: the app still works if registration fails.
      });
  };

  if (document.readyState === 'complete') {
    register();
  } else {
    window.addEventListener('load', register, { once: true });
  }
}
