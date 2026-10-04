// DozerNet - never show a broken machine photo.
// Any <img data-fallback="..."> whose own source fails to load (dead link, typo, deleted file)
// is switched once to its fallback, the standard photo for that machine type.
(function () {
    const swap = (img) => {
        const fallback = img.getAttribute('data-fallback');
        if (!fallback || img.dataset.fallbackUsed === '1') return;
        img.dataset.fallbackUsed = '1';
        img.src = fallback;
    };

    // Errors do not bubble, but they can be caught on the capture phase.
    document.addEventListener('error', (e) => {
        if (e.target && e.target.tagName === 'IMG') swap(e.target);
    }, true);

    // Images that already failed before this script ran.
    const sweep = () => {
        document.querySelectorAll('img[data-fallback]').forEach((img) => {
            if (img.complete && img.naturalWidth === 0 && img.getAttribute('src')) swap(img);
        });
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', sweep);
    else sweep();
    window.addEventListener('load', sweep);
})();
