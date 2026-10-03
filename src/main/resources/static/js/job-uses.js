// DozerNet job-use categories — hovering or focusing a category swaps the
// photo stage and its caption; when left alone the panel cycles through the
// categories on its own (paused on hover/focus, off-screen, or reduced motion).
(function () {
    const root = document.querySelector('.job-uses');
    if (!root) return;

    const panel = root.querySelector('.job-uses-panel');
    const items = Array.from(root.querySelectorAll('.job-uses-item'));
    const activePhoto = root.querySelector('.job-uses-photo[data-role="active"]');
    const nextPhoto = root.querySelector('.job-uses-photo[data-role="next"]');
    const caption = {
        name: root.querySelector('[data-role="name"]'),
        count: root.querySelector('[data-role="count"]')
    };
    if (!panel || !items.length || !activePhoto || !nextPhoto) return;

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const INTERVAL = 4500;
    panel.style.setProperty('--job-uses-interval', INTERVAL + 'ms');

    let current = Math.max(0, items.findIndex((el) => el.classList.contains('is-active')));
    let currentSrc = activePhoto.getAttribute('src') || '';
    let swapTimer = 0;

    const showPhoto = (src) => {
        if (!src || src === currentSrc) return;
        currentSrc = src;
        if (reduceMotion) {
            activePhoto.src = src;
            return;
        }
        window.clearTimeout(swapTimer);
        nextPhoto.classList.remove('is-incoming');
        nextPhoto.src = src;
        // Next frame, so the incoming photo starts from its zoomed-in state
        requestAnimationFrame(() => nextPhoto.classList.add('is-incoming'));
        swapTimer = window.setTimeout(() => {
            activePhoto.src = src;
            nextPhoto.classList.remove('is-incoming');
        }, 600);
    };

    const setActive = (index) => {
        current = index;
        const item = items[index];
        items.forEach((el) => el.classList.toggle('is-active', el === item));

        const name = item.getAttribute('data-name') || '';
        if (caption.name) caption.name.textContent = name;
        if (caption.count) caption.count.textContent = item.getAttribute('data-count') || '0';

        showPhoto(item.getAttribute('data-image'));
    };

    // --- Autoplay ------------------------------------------------------------
    let autoTimer = 0;
    let inView = false;
    let userHolding = false;

    const stopAuto = () => {
        window.clearTimeout(autoTimer);
        panel.classList.remove('is-playing');
    };
    const startAuto = () => {
        stopAuto();
        if (reduceMotion || !inView || userHolding) return;
        // Restart the progress line on the active row
        void panel.offsetWidth;
        panel.classList.add('is-playing');
        autoTimer = window.setTimeout(() => {
            setActive((current + 1) % items.length);
            startAuto();
        }, INTERVAL);
    };

    items.forEach((item, index) => {
        item.addEventListener('mouseenter', () => setActive(index));
        item.addEventListener('focusin', () => setActive(index));
    });

    panel.addEventListener('mouseenter', () => { userHolding = true; stopAuto(); });
    panel.addEventListener('mouseleave', () => {
        userHolding = panel.matches(':focus-within');
        startAuto();
    });
    panel.addEventListener('focusin', () => { userHolding = true; stopAuto(); });
    panel.addEventListener('focusout', () => {
        // Wait until focus has actually left the panel
        window.setTimeout(() => {
            if (!panel.matches(':focus-within') && !panel.matches(':hover')) {
                userHolding = false;
                startAuto();
            }
        }, 0);
    });

    if ('IntersectionObserver' in window) {
        new IntersectionObserver((entries) => {
            inView = entries[0].isIntersecting;
            if (inView) startAuto(); else stopAuto();
        }, { threshold: 0.35 }).observe(panel);
    }
})();
