package hu.detox.spring;

import org.apache.commons.collections.bidimap.DualTreeBidiMap;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.regex.PatternSyntaxException;

@RestController
@RequestMapping("/api/loc")
public class Loc {
    private static final Locale CORRECTIVE_LOCALE = Locale.ROOT;

    public static final String LIST_SPLIT = "[\\s,]+";
    private static final String SIMPLE_LOCALE_SPLIT = "[\\-_]+";
    public static final String LOCALE_SPLIT = Loc.SIMPLE_LOCALE_SPLIT + "|\\s*\\(|\t";
    private static final String LOCALE_MINUS = "\\-|minus";
    public static final String DETECTED = "DET";
    private static final List<Locale> ALL = new LinkedList<>(Arrays.asList(Locale.getAvailableLocales()));
    public static DualTreeBidiMap mapCountryISO3toISO2;
    public static DualTreeBidiMap mapLanguageISO3toISO2;

    static {
        final String[] countries = Locale.getISOCountries();
        Loc.mapCountryISO3toISO2 = new DualTreeBidiMap();
        for (final String country : countries) {
            final Locale locale = new Locale(StringUtils.EMPTY, country);
            Loc.mapCountryISO3toISO2.put(locale.getISO3Country().toUpperCase(CORRECTIVE_LOCALE), country);
        }
        Loc.mapLanguageISO3toISO2 = new DualTreeBidiMap();
        final String[] languages = Locale.getISOLanguages();
        for (final String lang : languages) {
            final Locale locale = new Locale(StringUtils.EMPTY, lang);
            Loc.mapLanguageISO3toISO2.put(locale.getISO3Language().toLowerCase(CORRECTIVE_LOCALE), lang);
        }
    }

    public static String attemptGet(boolean country, final Collection<Locale> nlocs, final String orig) {
        return Loc.attemptGet(country, nlocs, orig, false);
    }

    public static String attemptGet(boolean country, Collection<Locale> nlocs, String orig, final boolean closest) {
        if (orig.length() == 3) {
            final String cty = (String) Loc.mapCountryISO3toISO2.get(orig.toUpperCase(CORRECTIVE_LOCALE));
            if (cty != null) {
                orig = cty;
            }
        }
        if (nlocs == null) {
            nlocs = Loc.ALL;
        }
        String oocty = orig;
        orig = Loc.correctCommonMistakes(orig.toUpperCase(Locale.ENGLISH));
        orig = Loc.correctLongNames(nlocs, orig, Boolean.FALSE).toUpperCase(Locale.ENGLISH);
        final Map<String, String> ctys = closest ? new HashMap<>() : null;
        for (final Locale l : nlocs) {
            if (country && Loc.countryEquals(orig, l)) {
                return l.getCountry();
            } else if (country && Loc.langEquals(orig, l)) {
                return l.getLanguage();
            } else if (ctys != null) {
                String val = country ? l.getCountry() : l.getLanguage();
                ctys.put(l.getDisplayCountry(l), val);
                ctys.put(l.getDisplayCountry(Loc.CORRECTIVE_LOCALE), val);
                ctys.put(l.getDisplayLanguage(l), val);
                ctys.put(l.getDisplayLanguage(Loc.CORRECTIVE_LOCALE), val);
            }
        }
        if (ctys != null) {
            oocty = hu.detox.utils.strings.StringUtils.findMostSimilar(oocty, ctys.keySet());
            orig = ctys.get(oocty);
        }
        return orig;
    }

    private static String correctCommonMistakes(final String any) {
        return any.toUpperCase(Loc.CORRECTIVE_LOCALE) //
                .replaceFirst("^SW$", "SE") // Commonly made mistake
                .replaceFirst("^FL$", "FI") // Commonly made mistake
                .replaceFirst("^YU$", "MK") // MKD Macedonia is the former Yugoslav Republic
                .replaceFirst("^D$", "DE") // Germany
                .replaceFirst("^FIN$", "FI") // Finland
                .replaceFirst("^USA$", "US") // USA
                .replaceFirst("^I$", "IT") // Italy
                .replaceFirst("^F$", "FR") // France
                ;
    }

    public static String correctCountry(Collection<Locale> nlocs, String octy) {
        octy = StringUtils.trimToNull(octy);
        if (octy == null) {
            return null;
        }
        if (org.apache.commons.collections.CollectionUtils.isEmpty(nlocs)) {
            nlocs = Loc.ALL;
        }
        final Set<String> countries = new LinkedHashSet<String>();
        for (final Locale loc : nlocs) {
            countries.add(loc.getCountry());
        }
        if (octy.length() == 2) {
            octy = octy.toUpperCase(Locale.ENGLISH);
            for (final Locale l : nlocs) {
                final String lup = l.getLanguage().toUpperCase(Locale.ENGLISH);
                if (!countries.contains(lup)) {
                    octy = octy.replace(lup, l.getCountry());
                }
            }
        }
        final String[] ctyArr = octy.split(Loc.LOCALE_SPLIT);
        for (int i = ctyArr.length - 1; i >= 0; i--) {
            octy = Loc.attemptGet(true, nlocs, ctyArr[i]);
            if (countries.contains(octy)) {
                return StringUtils.isEmpty(octy) ? null : octy;
            }
        }
        return null;
    }

    @SuppressWarnings("null")
    public static String correctLang(Collection<Locale> nlocs, final String olng) {
        if (org.apache.commons.collections.CollectionUtils.isEmpty(nlocs)) {
            nlocs = Loc.ALL;
        }
        String lng = StringUtils.trimToEmpty(olng).split(Loc.LOCALE_SPLIT)[0];
        lng = Loc.correctLongNames(nlocs, lng, Boolean.TRUE).toLowerCase(Loc.CORRECTIVE_LOCALE);
        String nlng = lng;
        if (lng.length() == 2) {
            lng = Loc.correctCommonMistakes(lng).toLowerCase(Loc.CORRECTIVE_LOCALE);
            for (final Locale l : nlocs) {
                if (l.getLanguage().equals(lng)) {
                    break;
                }
                nlng = lng.replace(l.getCountry().toLowerCase(Loc.CORRECTIVE_LOCALE), l.getLanguage());
                if (nlng.length() != 2) {
                    break;
                }
                lng = nlng;
            }
        }
        nlng = null;
        for (final Locale l : nlocs) {
            if (Loc.langEquals(lng, l)) {
                nlng = l.getLanguage();
                break;
            }
        }
        if (StringUtils.isEmpty(nlng) || nlng.length() != 2) {
            nlng = Loc.attemptGet(true, nlocs, olng);
            nlng = Loc.country2langOrNull(nlocs, nlng);
        }
        return StringUtils.isEmpty(nlng) || nlng.length() != 2 ? null : nlng;
    }

    public static Locale[] correctLocale(final Collection<Locale> nlocs, final String... locs) {
        final Locale[] ret = new Locale[locs.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = Loc.correctLocale(nlocs, locs[i], false);
        }
        return ret;
    }

    public static Locale correctLocale(Collection<Locale> nlocs, String loc, final boolean closest) {
        if (org.apache.commons.collections.CollectionUtils.isEmpty(nlocs)) {
            nlocs = Loc.ALL;
        }
        loc = StringUtils.trimToEmpty(loc).replace(")", "");
        final String tloc = loc.replaceAll(Loc.LOCALE_SPLIT, "");
        if (tloc.isEmpty()) {
            return null;
        }
        String loc2f = null;
        if (tloc.length() == 4) {
            loc2f = tloc.substring(0, 2).toLowerCase() + "_" + tloc.substring(2).toUpperCase();
            final String loc2r = tloc.substring(2).toLowerCase() + "_" + tloc.substring(0, 2).toUpperCase();
            String loc2;
            if (tloc.matches("[A-Z]{2}[a-z]{2}")) {
                loc2 = loc2r;
            } else {
                loc2 = loc2f;
            }
            try {
                final Locale x = LocaleUtils.toLocale(loc2);
                if (nlocs.contains(x)) {
                    return x;
                } else if (closest) {
                    final Locale cx = Loc.findClosest(x, nlocs);
                    if (cx != null) {
                        return cx;
                    }
                } else {
                    loc = loc2r;
                }
            } catch (final Exception ia) {
                loc = loc2.equals(loc2r) ? loc2f : loc2r;
            }
        }
        String[] sp = loc.split(Loc.LOCALE_SPLIT);
        if (sp.length == 1) {
            sp = new String[]{sp[0], null};
        }
        try {
            final String o1 = sp[0];
            // Assume the first is language
            sp[0] = Loc.correctLang(nlocs, o1);
            if (sp[1] == null) {
                if (o1.equals(sp[0])) {
                    sp[1] = Loc.lang2country(nlocs, sp[0]);
                } else {
                    sp[1] = Loc.correctCountry(nlocs, o1);
                }
            } else {
                sp[1] = Loc.correctCountry(nlocs, sp[1]);
            }
        } catch (final IllegalArgumentException ia) {
            // Was not a language, try reverse direction
            String lng = sp[1];
            try {
                sp[1] = Loc.correctCountry(nlocs, sp[0]);
            } catch (final IllegalArgumentException ia2) {
                sp[0] = Loc.correctLang(nlocs, lng);
                sp[1] = Loc.lang2country(nlocs, sp[0]);
                lng = null;
            }
            if (lng != null) {
                try {
                    sp[0] = Loc.correctLang(nlocs, lng);
                } catch (final IllegalArgumentException ia2) {
                    sp[0] = Loc.country2lang(nlocs, sp[1]);
                }
            }
        }
        if (sp[0] == null || sp[1] == null) {
            String[] spn = loc.toLowerCase().split(Loc.LOCALE_SPLIT);
            if (spn.length == 1 && spn[0].length() == 2) {
                spn = new String[]{spn[0], spn[0].toUpperCase()};
            } else {
                spn = sp;
            }
            sp = spn;
        }
        sp[1] = StringUtils.trimToNull(sp[1]);
        sp[0] = StringUtils.trimToNull(sp[0]);
        if (sp[0] == null && sp[1] == null) {
            return null;
        }
        Locale r;
        if (sp[0] == null && sp[1] != null) {
            r = new Locale(Loc.correctLang(nlocs, sp[1]));
        } else {
            r = LocaleUtils.toLocale(sp[0] + (sp[1] == null ? "" : "_" + sp[1].toUpperCase()));
        }
        if (!nlocs.contains(r) && loc2f != null) {
            r = LocaleUtils.toLocale(loc2f);
        }
        if (closest) {
            r = Loc.findClosest(r, nlocs);
        }
        return !closest || nlocs.contains(r) ? r : null;
    }

    public static String correctLongNames(Collection<Locale> nlocs, String any, final Boolean useOnlyLang) {
        if (Loc.CORRECTIVE_LOCALE == null) {
            return any;
        }
        if (org.apache.commons.collections.CollectionUtils.isEmpty(nlocs)) {
            nlocs = Loc.ALL;
        }
        String ganyUpper = Loc.correctCommonMistakes(any.toLowerCase(Loc.CORRECTIVE_LOCALE));
        if (Boolean.TRUE.equals(useOnlyLang) || useOnlyLang == null) {
            for (final Locale l : nlocs) {
                ganyUpper = ganyUpper.replace(l.getDisplayLanguage(Loc.CORRECTIVE_LOCALE).toUpperCase(Loc.CORRECTIVE_LOCALE), l.getLanguage());
            }
        }
        if (Boolean.FALSE.equals(useOnlyLang) || useOnlyLang == null) {
            for (final Locale l : nlocs) {
                ganyUpper = ganyUpper.replace(l.getDisplayCountry(Loc.CORRECTIVE_LOCALE).toUpperCase(Loc.CORRECTIVE_LOCALE), l.getCountry());
            }
        }
        if (!ganyUpper.equalsIgnoreCase(any)) {
            if (Boolean.TRUE.equals(useOnlyLang)) {
                ganyUpper = ganyUpper.toLowerCase(Loc.CORRECTIVE_LOCALE);
            }
            any = ganyUpper;
        }
        return any;
    }

    public static String country2lang(final Collection<Locale> nlocs, final String cty) {
        final String ret = Loc.country2langOrNull(nlocs, cty);
        if (ret == null) {
            throw new IllegalStateException(cty + " country must be in " + nlocs);
        }
        return ret;
    }

    public static String country2langOrNull(final Collection<Locale> nlocs, final String cty) {
        boolean langIsCty = false;
        String ret = null;
        for (final Locale l : nlocs) {
            if (Loc.countryEquals(cty, l)) {
                if (!langIsCty && l.getCountry().equalsIgnoreCase(l.getLanguage())) {
                    ret = l.getLanguage();
                    langIsCty = true;
                } else if (ret == null) {
                    ret = l.getLanguage();
                }
            }
        }
        return ret;
    }

    public static boolean countryEquals(final String cty, final Locale l) {
        return cty.equalsIgnoreCase(l.getCountry()) || cty.equalsIgnoreCase(l.getDisplayCountry(l))
                || cty.equalsIgnoreCase(l.getDisplayCountry(Loc.CORRECTIVE_LOCALE));
    }

    public static Locale findClosest(final Locale x, Collection<Locale> nlocs) {
        if (nlocs == null) {
            nlocs = Loc.ALL;
        }
        if (x == null || nlocs.contains(x)) {
            return x;
        }
        for (final Locale l : nlocs) {
            if (x.getCountry().equals(l.getCountry()) || x.getLanguage().equals(l.getCountry().toLowerCase()) || x.getCountry().equals(l.getLanguage())) {
                return l;
            }
        }
        return null;
    }

    public static Locale getUnalteredSimpleLocale(final String lcl) {
        Locale any;
        if (lcl.length() > 4) {
            final String[] spl = lcl.split(Loc.SIMPLE_LOCALE_SPLIT);
            any = new Locale(spl[0], spl[1]);
        } else if (lcl.length() == 4) {
            any = new Locale(lcl.substring(0, 2), lcl.substring(2, 5));
        } else {
            any = new Locale(lcl);
        }
        return any;
    }

    public static String lang2country(final Collection<Locale> nlocs, final String lng) {
        boolean langIsCty = false;
        String ret = null;

        for (final Locale l : nlocs) {
            if (Loc.langEquals(lng, l)) {
                if (!langIsCty && l.getCountry().equalsIgnoreCase(l.getLanguage())) {
                    ret = l.getCountry();
                    langIsCty = true;
                } else if (ret == null && !langIsCty) {
                    ret = l.getCountry();
                }
            }
        }

        if (ret == null) {
            throw new IllegalArgumentException(lng + " language must be in " + nlocs);
        }

        return ret;
    }

    public static boolean langEquals(final String lng, final Locale l) {
        return l.toString().equalsIgnoreCase(lng) || lng.equalsIgnoreCase(l.getLanguage()) || lng.equalsIgnoreCase(l.getDisplayLanguage(l))
                || lng.equalsIgnoreCase(l.getDisplayLanguage(Loc.CORRECTIVE_LOCALE));
    }

    public static String stdLocale(String loc) {
        loc = StringUtils.trimToEmpty(loc).replace(")", "");
        String cty = null;
        String lan = null;
        final String tloc = loc.replaceAll(Loc.LOCALE_SPLIT, StringUtils.EMPTY);
        if (tloc.matches("[A-Z]{2}[a-z]{2}")) {
            lan = tloc.substring(2).toLowerCase(Locale.ENGLISH);
            cty = tloc.substring(0, 2).toUpperCase(Locale.ENGLISH);
        } else if (tloc.matches("[a-z]{2}[A-Z]{2}")) {
            lan = tloc.substring(0, 2).toLowerCase(Locale.ENGLISH);
            cty = tloc.substring(2).toUpperCase(Locale.ENGLISH);
        } else {
            final String[] aloc = loc.split(Loc.LOCALE_SPLIT);
            lan = aloc[0];
            if (aloc.length > 1) {
                cty = Loc.correctCommonMistakes(aloc[1]);
            }
        }
        if (cty != null && cty.length() != 2) {
            final String tmp = (String) Loc.mapCountryISO3toISO2.get(cty);
            if (tmp == null) {
                cty = Loc.correctLongNames(null, cty, Boolean.FALSE);
            } else {
                cty = tmp;
            }
        }
        if (lan.length() != 2) {
            lan = Loc.correctLongNames(null, lan, Boolean.TRUE);
        }
        return lan + (cty == null ? StringUtils.EMPTY : "_" + cty);
    }

    public static Set<Locale> shrinkLocs(final Locale det, Collection<Locale> locs, List<String> argl) {
        argl = new LinkedList<String>(argl);
        if (locs == null) {
            locs = Loc.ALL;
        }
        final Set<Locale> ret = new LinkedHashSet<>();
        if (org.apache.commons.collections.CollectionUtils.isEmpty(argl)) {
            ret.addAll(locs);
            return ret;
        }
        boolean add = true;
        if (!argl.isEmpty() && argl.get(0).matches(Loc.LOCALE_MINUS)) {
            ret.addAll(locs);
            add = false;
            argl.remove(0);
        }
        while (!argl.isEmpty()) {
            String arg = argl.remove(0);
            if (StringUtils.isBlank(arg)) {
                ret.addAll(locs);
            } else if (arg.matches(Loc.LOCALE_MINUS)) {
                add = false;
            } else if ("+".equals(arg)) {
                add = true;
            } else {
                if (det != null) {
                    arg = arg.replace(Loc.DETECTED, det.toString());
                }
                if (arg.equals(Loc.DETECTED)) {
                    ret.add(add ? Loc.CORRECTIVE_LOCALE : null);
                } else if (arg.contains(">")) { // Inclusive locale range definition
                    final String[] rng = arg.split(">", -1);
                    final Locale[] l01 = correctLocale(locs, rng[0], rng[1]);
                    boolean on = false;
                    for (final Locale iloc : locs) {
                        if (l01[0] == null || l01[0].equals(iloc)) {
                            on = true;
                        }
                        if (on) {
                            ret.add(iloc);
                        }
                        if (l01[1] != null && l01[1].equals(iloc)) {
                            on = false;
                        }
                    }
                } else {
                    arg = Loc.correctLongNames(locs, arg, null);
                    for (final Locale loc : locs) {
                        try {
                            if (Loc.langEquals(arg, loc) || Loc.countryEquals(arg, loc) || loc.toString().toUpperCase().matches(arg)) {
                                if (add) {
                                    ret.add(loc);
                                } else {
                                    ret.remove(loc);
                                }
                            }
                        } catch (final PatternSyntaxException pse) {
                            // No problem, does not match
                        }
                    }
                }
            }
        }

        return ret;
    }

    public static Set<Locale> shrinkLocs(final Locale det, final Collection<Locale> locs, final String arg) {
        final List<String> argl = Arrays.asList(arg.trim().split(Loc.LIST_SPLIT));
        return shrinkLocs(det, locs, argl);
    }

    @GetMapping
    public Object getMappings() {
        return new Object[]{
                mapCountryISO3toISO2, mapLanguageISO3toISO2,
                Loc.ALL
        };
    }

}
