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

function saveCache(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
}

function loadCache(key) {
    const data = localStorage.getItem(key);
    return data ? JSON.parse(data) : null;
}

// Step 1: Detect browser language
const browserLang = navigator.language || navigator.userLanguage;
let [langCode, countryCode] = browserLang.split('-');
if (countryCode) countryCode = countryCode.toUpperCase();
else {
    window.loc = loadCache("loc");
    countryCode = window.loc ? window.loc[1] : null;
}

// Step 2: Fallback to IP-based geolocation
async function getCountryFromIP() {
    const cacheKey = "ipCountryCode";
    let cached = loadCache(cacheKey);
    if (cached) {
        return cached;
    }
    let cUrl = "https://ipinfo.io/json";
    if (isLocal) {
        cUrl = bRootUrl + "/ipinfo.json";
    }
    const res = await fetch(cUrl);
    const data = await res.json();
    saveCache(cacheKey, data);
    return data;
}

// Step 3: Get countries/locales details from REST API
async function getCountries() {
    const cacheKey = `countryDetails`;
    let cached = loadCache(cacheKey);
    if (cached) {
        return cached;
    }
    let cUrl = "https://restcountries.com/v3.1/all?fields=name,region,flag,cca2,cca3,translations,languages";
    if (isLocal) {
        cUrl = bRootUrl + "/all_countries.json";
    }
    const res = await fetch(cUrl);
    const data = await res.json();
    saveCache(cacheKey, data);
    return data;
}

async function getLocales() {
    const cacheKey = `localeDetails`;
    let cached = loadCache(cacheKey);
    if (cached) {
        return cached;
    }
    let locUrl = bRootUrl + "/loc";
    if (isLocal) {
        locUrl += ".json";
    }
    const res = await fetch(locUrl);
    const data = await res.json();
    data.locales = reorderLocaleMap(data.locales);
    saveCache(cacheKey, data);
    return data;
}

// Step 4: Initialize Google Map with localization
function initMap(lang, region) {
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=YOUR_API_KEY&language=${lang}&region=${region}&callback=loadMap`;
    script.async = true;
    document.head.appendChild(script);
}

function loadMap() {
    const map = new google.maps.Map(document.getElementById("map"), {
        center: {lat: 47.4979, lng: 19.0402}, // Example: Budapest
        zoom: 6
    });
}

function findCountriesByLang(lng, limit) {
    if (!window.allCountries) return [];
    const code3 = attempt2To3(true, lng).toLowerCase();
    if (!limit) {
        let ln3 = attempt2To3(false, lng).toLowerCase();
        limit = mainCountryByLang[ln3];
        if (limit) limit = limit.length
        if (!limit) limit = 2; else if (limit > 3) limit = 3;
    }
    let matches = window.allCountries.filter(country => {
        if (!country.languages) return false;
        if (country.languages[code3]) return true;
        if (country.languages[lng]) return true;
        return Object.values(country.languages).some(
            name => name.toLowerCase() === lng.toLowerCase()
        );
    });
    const priority = mainCountryByLang[code3];
    if (priority) {
        matches.sort((a, b) => {
            const aIdx = priority.indexOf(a.cca2);
            const bIdx = priority.indexOf(b.cca2);
            if (aIdx === -1 && bIdx === -1) return 0;
            if (aIdx === -1) return 1;
            if (bIdx === -1) return -1;
            return aIdx - bIdx;
        });
    }
    return matches.slice(0, limit);
}

function locale(search, what) {
    if (!window.allLocales) return null;

    function extractFields(arr, what) {
        switch (what) {
            case 2:
                return [arr[1], arr[3]];      // isolang2, country2
            case 3:
                return [arr[2], arr[4]];      // isolang3, country3
            case 0:
                return [arr[6], arr[7]];      // by locale of request
            case 1:
                return [arr[8], arr[9]];      // by locale of the country
            case null:
            case undefined:
                return [arr[10], arr[11]]; // by CORRECTIVE
            default:
                return arr[0];
        }
    }

    const locales = window.allLocales.locales;
    let bestMatch = null;
    let bestCount = 0;

    // Normalize search
    let searchArr = search;
    if (!Array.isArray(search)) searchArr = [search];

    for (const [key, arr] of locales) {
        let matchCount = 0;

        for (let i = 0; i < searchArr.length; i++) {
            const s = searchArr[i];
            let found = false;
            if (typeof s === "string" && key && key === s) {
                matchCount++;
            } else if (s instanceof RegExp && key && s.test(key)) {
                matchCount++;
            }

            if (!found) {
                for (const val of arr) {
                    if (typeof s === "string" && val === s) {
                        matchCount++;
                        break;
                    } else if (typeof s === "object" && s.test(val)) {
                        matchCount++;
                        break;
                    }
                }
            }
        }
        if (matchCount > bestCount) {
            bestCount = matchCount;
            bestMatch = arr;
        }
        if (matchCount >= searchArr.length) break;
    }

    if (bestMatch) return extractFields(bestMatch, what);
    return null;
}

function attempt2To3(country, any) {
    if (!window.allLocales) return any;
    const map = country ? window.allLocales.countryMap : window.allLocales.languageMap;
    const codeSmall = any.toLowerCase();
    let entry = Object.entries(map).find(
        ([k, v]) => k.toLowerCase() === codeSmall || v.toLowerCase() === codeSmall
    );
    return entry?.[0] || any;
}

(async () => {
    window.allLocales = await getLocales();

    const allCountries = await getCountries();
    window.allCountries = allCountries;

    if (!countryCode) {
        countryCode = await getCountryFromIP();
        countryCode = countryCode.country
    }
    window.country = allCountries.find(country => country.cca2 === countryCode)
    saveCache("country", window.country);

    window.loc = [langCode, countryCode];
    saveCache("loc", window.loc);

    initMap(langCode || 'en', countryCode || 'US');
})();
