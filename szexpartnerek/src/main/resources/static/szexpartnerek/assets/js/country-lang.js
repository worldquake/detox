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
    try {
        const res = await fetch("https://ipinfo.io/json");
        const data = await res.json();
        saveCache(cacheKey, data);
        return data;
    } catch (err) {
        console.error("IP lookup failed:", err);
    }
    return null;
}

// Step 3: Get countries details from REST API
async function getCountries() {
    const cacheKey = `countryDetails`;
    let cached = loadCache(cacheKey);
    if (cached) {
        return cached;
    }
    try {
        const res = await fetch("https://restcountries.com/v3.1/all?fields=name,region,flag,cca2,cca3,translations,languages");
        const data = await res.json();
        saveCache(cacheKey, data);
    } catch (err) {
        console.error(err);
        return null;
    }
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

function findCountryByLang(lng) {
    if (!window.allCountries) return;
    const code3 = attempt2To3(lng);

    return window.allCountries.find(country => {
        if (!country.languages) return false;
        if (country.languages[code3]) return true;
        if (country.languages[lng]) return true;
        return Object.values(country.languages).some(
            name => name.toLowerCase() === lng.toLowerCase()
        );
    });
}

function attempt2To3(any) {
    if (!window.cca2ToLang3) return any;
    const codeSmall = any.toLowerCase();
    return window.cca2ToLang3[codeSmall] || any;
}

(async () => {
    if (!countryCode) {
        countryCode = await getCountryFromIP();
        countryCode = countryCode.country
    }
    const allCountries = await getCountries();

    window.allCountries = allCountries;
    window.country = allCountries.find(country => country.cca2 === countryCode)
    saveCache("country", window.country);

    window.loc = [langCode, countryCode];
    saveCache("loc", window.loc);

    initMap(langCode || 'en', countryCode || 'US');
})();
