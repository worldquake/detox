String.prototype.cap = function () {
    if (this.length === 0) return this;
    if (!/[a-zA-Z]/.test(this.charAt(0))) return this;
    return this.charAt(0).toUpperCase() + this.slice(1);
}

const url = new URL(location.href);
const isLocal = window.location.protocol === "file:";
const target = isLocal ? url.searchParams.get('t') || "" : url.pathname.replace(/^\//, "").replaceAll('/', '_');
let rootUrl = "example/";
let bRootUrl = "/api";
if (isLocal) bRootUrl = rootUrl
else rootUrl = bRootUrl + "/" + rootUrl;
rootUrl += target;

function startLanScrollCycling($container, delay = 5000) {
    if ($container.data('infinite-scroll-initialized')) return;
    $container.data('infinite-scroll-initialized', true);

    var $icons = $container.children('span'); // Only direct children
    if ($icons.length === 0) return;
    $icons.tooltip({show: null, track: true});
    var current = 0;

    function showNextIcon() {
        $icons.hide();
        $icons.eq(current).show();
        current = (current + 1) % $icons.length;
        setTimeout(showNextIcon, delay);
    }

    showNextIcon();
}

// Initial run for existing divs
$(function () {
    $('.infinite-scroll').each(function () {
        startLanScrollCycling($(this));
    });
    const observer = new MutationObserver(function (mutations) {
        mutations.forEach(function (mutation) {
            $(mutation.addedNodes).each(function () {
                $(this).find('.infinite-scroll').each(function () {
                    startLanScrollCycling($(this));
                });
            });
        });
    });

    observer.observe(document.body, {childList: true, subtree: true});
});