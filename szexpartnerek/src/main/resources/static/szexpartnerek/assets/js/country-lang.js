async function fetchCountries() {
    let cUrl = "https://restcountries.com/v3.1/all?fields=name,region,flag,cca2,cca3,translations,languages";
    if (isLocal) {
        cUrl = bRootUrl + "/all_countries.json";
    }
    const res = await fetch(cUrl);
    return await res.json();
}

const getCountries = withCache("countryDetails", fetchCountries);

async function fetchLocales() {
    let locUrl = bRootUrl + "/loc";
    if (isLocal) {
        locUrl += ".json";
    }
    const res = await fetch(locUrl);
    const data = await res.json();
    data.locales = reorderLocaleMap(data.locales);
    return data;
}

const getLocales = withCache("localeDetails", fetchLocales);

async function i18nData(l) {
    const cacheKey = `/loc/${l}`;
    let cached = loadCache(cacheKey);
    if (cached) {
        return cached;
    }
    let locUrl = bRootUrl + cacheKey;
    if (isLocal) {
        locUrl += ".json";
    }
    const response = await fetch(locUrl);
    const data = await response.json();
    saveCache(cacheKey, data);
    await i18next.init({
        lng: window.loc[0],
        resources: data
    });
    return data;
}

async function fetchI18N(path, l = `${window.loc[0]}_${window.loc[1]}`) {
    const data = await i18nData(`${path}/${l}`);
    return await i18next.init({
        lng: window.loc[0],
        resources: data
    });
}

const getI18N = withCache("localeDetails", fetchI18N);

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
    window.allCountries = await getCountries();
    window.country = window.allCountries.find(country => country.cca2 === countryCode)
})();
