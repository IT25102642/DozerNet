// DozerNet highlights — count up numeric stats (e.g. available machines)
// as they scroll into view. Non-numeric stats ("Same day", "Verified") are untouched.
(function () {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) return;

    const stats = document.querySelectorAll('#highlights .stat');
    if (!stats.length || !('IntersectionObserver' in window)) return;

    const animate = (el, target) => {
        const duration = 900;
        let startTime = null;
        const step = (t) => {
            if (startTime === null) startTime = t;
            const progress = Math.min(1, (t - startTime) / duration);
            const eased = 1 - Math.pow(1 - progress, 3);
            el.textContent = Math.round(eased * target).toString();
            if (progress < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    };

    const io = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (!entry.isIntersecting) return;
            const el = entry.target;
            const target = parseInt(el.textContent.trim(), 10);
            if (!isNaN(target) && String(target) === el.textContent.trim()) {
                animate(el, target);
            }
            io.unobserve(el);
        });
    }, { threshold: 0.4 });

    stats.forEach((el) => io.observe(el));
})();
