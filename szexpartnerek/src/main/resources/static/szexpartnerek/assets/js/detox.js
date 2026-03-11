const url = new URL(location.href);
const isLocal = window.location.protocol === "file:";
let rootUrl = "example/";
let bRootUrl = "/api";
if (isLocal) bRootUrl = rootUrl
else rootUrl = bRootUrl + "/" + rootUrl;

const browserLang = navigator.language || navigator.userLanguage;
let [langCode, countryCode] = browserLang.split('-');
if (countryCode) {
    countryCode = countryCode.toUpperCase();
    window.loc = [langCode, countryCode];
} else {
    window.loc = loadCache("loc");
    countryCode = window.loc ? window.loc[1] : null;
}

const mainCountryByLang = {
    eng: ['GB', 'IE', 'US', 'CA', 'AU', 'MT'], // Malta (MT) is also English-speaking
    deu: ['DE', 'AT', 'CH', 'LI', 'LU', 'BE'], // German in Belgium
    fra: ['FR', 'BE', 'CH', 'LU', 'MC'],
    spa: ['ES', 'MX', 'AR', 'CO', 'CL', 'PE', 'VE'],
    ita: ['IT', 'CH', 'SM', 'VA'],
    rus: ['RU', 'BY', 'KZ', 'KG'],
    por: ['PT', 'BR', 'AO', 'MZ'],
    nld: ['NL', 'BE', 'SR'],
    hun: ['HU'],
    tur: ['TR'],
    ell: ['GR', 'CY'],
    heb: ['IL'],
    ara: ['EG', 'MA', 'DZ'], // Not European, but kept for completeness
    jpn: ['JP'],
    zho: ['CN', 'SG'], // Not European, but kept for completeness
    swe: ['SE', 'FI'], // Swedish in Finland
    dan: ['DK'],
    nor: ['NO'],
    fin: ['FI'],
    pol: ['PL'],
    ces: ['CZ'],
    slk: ['SK'],
    slv: ['SI'],
    hrv: ['HR'],
    srp: ['RS', 'ME', 'BA'], // Serbian in Serbia, Montenegro, Bosnia
    bos: ['BA'], // Bosnian in Bosnia
    mkd: ['MK'],
    bul: ['BG'],
    ron: ['RO', 'MD'], // Romanian in Moldova
    lav: ['LV'],
    lit: ['LT'],
    est: ['EE'],
    alb: ['AL'],
    cat: ['ES', 'AD'], // Catalan in Andorra and Spain
    gla: ['GB'], // Scottish Gaelic in UK
    gle: ['IE'], // Irish in Ireland
    mlt: ['MT'], // Maltese in Malta
    isl: ['IS'],
    ukr: ['UA'],
    bel: ['BY'], // Belarusian in Belarus
    // Add more as needed
};

function reorderLocaleMap(locales) {
    const entries = Object.entries(locales);
    const usedKeys = new Set();
    const resultEntries = [];

    // 1. Add all mainCountryByLang entries in exact order
    Object.entries(mainCountryByLang).forEach(([iso3, countries]) => {
        countries.forEach(country => {
            // Find the first matching entry
            for (const [key, arr] of entries) {
                if (arr[2] === iso3 && arr[3] === country && !usedKeys.has(key)) {
                    resultEntries.push([key, arr]);
                    usedKeys.add(key);
                    break; // Only one per (lang, country) pair
                }
            }
        });
    });
    for (const [key, arr] of entries) {
        if (!usedKeys.has(key)) {
            resultEntries.push([key, arr]);
            usedKeys.add(key);
        }
    }
    return resultEntries;
}

String.prototype.cap = function () {
    if (this.length === 0) return this;
    if (!/[a-zA-Z]/.test(this.charAt(0))) return this;
    return this.charAt(0).toUpperCase() + this.slice(1);
}

function saveCache(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
}

function loadCache(key) {
    const data = localStorage.getItem(key);
    return data ? JSON.parse(data) : null;
}

function withCache(cacheKey, fetcher) {
    return async function (...args) {
        let cached = loadCache(cacheKey);
        if (cached) return cached;
        const data = await fetcher(cacheKey, ...args);
        saveCache(cacheKey, data);
        return data;
    };
}

if (!countryCode) {
    async function fetchCountryFromIP(ck) {
        let cUrl = `https://${ck}.io/json`;
        if (isLocal) {
            cUrl = bRootUrl + `/${ck}.json`;
        }
        const res = await fetch(cUrl);
        return await res.json();
    }

    const getCountryFromIP = withCache("ipinfo", fetchCountryFromIP);

    (async () => {
        window.geoip = await getCountryFromIP();
        countryCode = window.geoip.country
        if (!window.loc) window.loc = [langCode, countryCode];
    })();
}

// An infinite-scroll item changer
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
