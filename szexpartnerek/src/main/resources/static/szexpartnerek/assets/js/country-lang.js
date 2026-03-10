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

const mainCountryByLang = {
    eng: ['GB', 'US', 'CA', 'AU', 'IE', 'NZ'],
    deu: ['DE', 'AT', 'CH', 'LI', 'LU'],
    fra: ['FR', 'BE', 'CH', 'CA', 'LU', 'MC'],
    spa: ['ES', 'MX', 'AR', 'CO', 'CL', 'PE', 'VE'],
    ita: ['IT', 'CH', 'SM', 'VA'],
    rus: ['RU', 'BY', 'KZ', 'KG'],
    por: ['PT', 'BR', 'AO', 'MZ'],
    nld: ['NL', 'BE', 'SR'],
    pl: ['PL'],
    hun: ['HU'],
    tur: ['TR'],
    ell: ['GR', 'CY'],
    heb: ['IL'],
    ara: ['EG', 'SA', 'MA', 'DZ', 'IQ', 'SY', 'JO', 'AE'],
    jpn: ['JP'],
    zho: ['CN', 'TW', 'SG', 'HK']
    // Add more as needed
};

function findCountriesByLang(lng, limit = 2) {
    if (!window.allCountries) return [];
    const code3 = attempt2To3(true, lng).toLowerCase();
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
