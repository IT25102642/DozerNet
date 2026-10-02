// DozerNet password fields — adds a show/hide ("eye") button to every
// <input type="password"> on the site, so users can check what they typed.
// Loaded from the shared <head>, so sign-in, registration, change-password and
// admin forms all get it, and so does any password field added in future.
(function () {
    const ICON_SHOW =
        '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" ' +
        'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>';
    const ICON_HIDE =
        '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" ' +
        'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M3 3l18 18"/><path d="M10.6 5.1A10.7 10.7 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.2"/>' +
        '<path d="M6.5 6.6C3.8 8.3 2 12 2 12s3.6 7 10 7c1.7 0 3.2-.4 4.5-1"/>' +
        '<path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';

    const enhance = (input) => {
        if (input.dataset.pwToggle) return;
        input.dataset.pwToggle = '1';

        const wrap = document.createElement('div');
        wrap.className = 'password-field';
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'password-toggle';
        button.setAttribute('aria-label', 'Show password');
        button.setAttribute('aria-pressed', 'false');
        button.innerHTML = ICON_SHOW;
        wrap.appendChild(button);

        const setVisible = (visible) => {
            input.type = visible ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(visible));
            button.setAttribute('aria-label', visible ? 'Hide password' : 'Show password');
            button.innerHTML = visible ? ICON_HIDE : ICON_SHOW;
        };

        button.addEventListener('click', () => {
            const visible = input.type === 'password';
            setVisible(visible);
            // Keep typing where they left off.
            input.focus();
            const end = input.value.length;
            try { input.setSelectionRange(end, end); } catch (ignored) { /* not supported for this type */ }
        });

        // Never leave a password visible after leaving or submitting the page.
        input.form && input.form.addEventListener('submit', () => setVisible(false));
        window.addEventListener('pagehide', () => setVisible(false));
    };

    const init = () => document.querySelectorAll('input[type="password"]').forEach(enhance);
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
