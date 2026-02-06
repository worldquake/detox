package hu.detox.utils;

import java.util.*;

public class TimeZoneList {

    private static final class TimeZoneMapping {
        private final String windowsStandardName;
        private final String olsonName;
        private final String windowsDisplayName;

        public TimeZoneMapping(final String windowsStandardName, final String olsonName, final String windowsDisplayName) {
            this.windowsStandardName = windowsStandardName;
            this.olsonName = olsonName;
            this.windowsDisplayName = windowsDisplayName;
        }

        public String getOlsonName() {
            return this.olsonName;
        }

        public String getWindowsDisplayName() {
            return this.windowsDisplayName;
        }

        public String getWindowsStandardName() {
            return this.windowsStandardName;
        }
    }

    public static final class TimeZoneWithDisplayNames {
        private final TimeZone timeZone;
        private final String displayName;
        private final String standardDisplayName;

        public TimeZoneWithDisplayNames(final TimeZone timeZone, final String displayName, final String standardDisplayName) {
            this.timeZone = timeZone;
            this.displayName = displayName;
            this.standardDisplayName = standardDisplayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public String getStandardDisplayName() {
            return this.standardDisplayName;
        }

        public TimeZone getTimeZone() {
            return this.timeZone;
        }
    }

    private static final List<TimeZoneMapping> ZONEMAPPINGS = new ArrayList<TimeZoneMapping>();

    static { // NOCH This is imported class
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Afghanistan Standard Time", "Asia/Kabul", "(GMT +04:30) Kabul"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Alaskan Standard Time", "America/Anchorage", "(GMT -09:00) Alaska"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Arab Standard Time", "Asia/Riyadh", "(GMT +03:00) Kuwait, Riyadh"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Arabian Standard Time", "Asia/Dubai", "(GMT +04:00) Abu Dhabi, Muscat"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Arabic Standard Time", "Asia/Baghdad", "(GMT +03:00) Baghdad"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Argentina Standard Time", "America/Buenos_Aires", "(GMT -03:00) Buenos Aires"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Atlantic Standard Time", "America/Halifax", "(GMT -04:00) Atlantic Time (Canada)"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("AUS Central Standard Time", "Australia/Darwin", "(GMT +09:30) Darwin"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("AUS Eastern Standard Time", "Australia/Sydney", "(GMT +10:00) Canberra, Melbourne, Sydney"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Azerbaijan Standard Time", "Asia/Baku", "(GMT +04:00) Baku"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Azores Standard Time", "Atlantic/Azores", "(GMT -01:00) Azores"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Bangladesh Standard Time", "Asia/Dhaka", "(GMT +06:00) Dhaka"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Canada Central Standard Time", "America/Regina", "(GMT -06:00) Saskatchewan"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Cape Verde Standard Time", "Atlantic/Cape_Verde", "(GMT -01:00) Cape Verde Is."));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Caucasus Standard Time", "Asia/Yerevan", "(GMT +04:00) Yerevan"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Cen. Australia Standard Time", "Australia/Adelaide", "(GMT +09:30) Adelaide"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Central America Standard Time", "America/Guatemala", "(GMT -06:00) Central America"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Central Asia Standard Time", "Asia/Almaty", "(GMT +06:00) Astana"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Central Brazilian Standard Time", "America/Cuiaba", "(GMT -04:00) Cuiaba"));
        TimeZoneList.ZONEMAPPINGS
                .add(new TimeZoneMapping("Central Europe Standard Time", "Europe/Budapest", "(GMT +01:00) Belgrade, Bratislava, Budapest, Ljubljana, Prague"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Central European Standard Time", "Europe/Warsaw", "(GMT +01:00) Sarajevo, Skopje, Warsaw, Zagreb"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Central Pacific Standard Time", "Pacific/Guadalcanal", "(GMT +11:00) Solomon Is., New Caledonia"));
        TimeZoneList.ZONEMAPPINGS
                .add(new TimeZoneMapping("Central Standard Time (Mexico)", "America/Mexico_City", "(GMT -06:00) Guadalajara, Mexico City, Monterrey"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Central Standard Time", "America/Chicago", "(GMT -06:00) Central Time (US & Canada)"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("China Standard Time", "Asia/Shanghai", "(GMT +08:00) Beijing, Chongqing, Hong Kong, Urumqi"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Dateline Standard Time", "Etc/GMT+12", "(GMT -12:00) International Date Line West"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("E. Africa Standard Time", "Africa/Nairobi", "(GMT +03:00) Nairobi"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("E. Australia Standard Time", "Australia/Brisbane", "(GMT +10:00) Brisbane"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("E. Europe Standard Time", "Europe/Minsk", "(GMT +02:00) Minsk"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("E. South America Standard Time", "America/Sao_Paulo", "(GMT -03:00) Brasilia"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Eastern Standard Time", "America/New_York", "(GMT -05:00) Eastern Time (US & Canada)"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Egypt Standard Time", "Africa/Cairo", "(GMT +02:00) Cairo"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Ekaterinburg Standard Time", "Asia/Yekaterinburg", "(GMT +05:00) Ekaterinburg"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Fiji Standard Time", "Pacific/Fiji", "(GMT +12:00) Fiji, Marshall Is."));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("FLE Standard Time", "Europe/Kiev", "(GMT +02:00) Helsinki, Kyiv, Riga, Sofia, Tallinn, Vilnius"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Georgian Standard Time", "Asia/Tbilisi", "(GMT +04:00) Tbilisi"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("GMT Standard Time", "Europe/London", "(GMT) Dublin, Edinburgh, Lisbon, London"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Greenland Standard Time", "America/Godthab", "(GMT -03:00) Greenland"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Greenwich Standard Time", "Atlantic/Reykjavik", "(GMT) Monrovia, Reykjavik"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("GTB Standard Time", "Europe/Istanbul", "(GMT +02:00) Athens, Bucharest, Istanbul"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Hawaiian Standard Time", "Pacific/Honolulu", "(GMT -10:00) Hawaii"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("India Standard Time", "Asia/Calcutta", "(GMT +05:30) Chennai, Kolkata, Mumbai, New Delhi"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Iran Standard Time", "Asia/Tehran", "(GMT +03:30) Tehran"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Israel Standard Time", "Asia/Jerusalem", "(GMT +02:00) Jerusalem"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Jordan Standard Time", "Asia/Amman", "(GMT +02:00) Amman"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Kamchatka Standard Time", "Asia/Kamchatka", "(GMT +12:00) Petropavlovsk-Kamchatsky - Old"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Korea Standard Time", "Asia/Seoul", "(GMT +09:00) Seoul"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Magadan Standard Time", "Asia/Magadan", "(GMT +11:00) Magadan"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Mauritius Standard Time", "Indian/Mauritius", "(GMT +04:00) Port Louis"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Mid-Atlantic Standard Time", "Etc/GMT+2", "(GMT -02:00) Mid-Atlantic"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Middle East Standard Time", "Asia/Beirut", "(GMT +02:00) Beirut"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Montevideo Standard Time", "America/Montevideo", "(GMT -03:00) Montevideo"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Morocco Standard Time", "Africa/Casablanca", "(GMT) Casablanca"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Mountain Standard Time (Mexico)", "America/Chihuahua", "(GMT -07:00) Chihuahua, La Paz, Mazatlan"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Mountain Standard Time", "America/Denver", "(GMT -07:00) Mountain Time (US & Canada)"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Myanmar Standard Time", "Asia/Rangoon", "(GMT +06:30) Yangon (Rangoon)"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("N. Central Asia Standard Time", "Asia/Novosibirsk", "(GMT +06:00) Novosibirsk"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Namibia Standard Time", "Africa/Windhoek", "(GMT +02:00) Windhoek"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Nepal Standard Time", "Asia/Katmandu", "(GMT +05:45) Kathmandu"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("New Zealand Standard Time", "Pacific/Auckland", "(GMT +12:00) Auckland, Wellington"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Newfoundland Standard Time", "America/St_Johns", "(GMT -03:30) Newfoundland"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("North Asia East Standard Time", "Asia/Irkutsk", "(GMT +08:00) Irkutsk"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("North Asia Standard Time", "Asia/Krasnoyarsk", "(GMT +07:00) Krasnoyarsk"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Pacific SA Standard Time", "America/Santiago", "(GMT -04:00) Santiago"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Pacific Standard Time (Mexico)", "America/Tijuana", "(GMT -08:00) Baja California"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Pacific Standard Time", "America/Los_Angeles", "(GMT -08:00) Pacific Time (US & Canada)"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Pakistan Standard Time", "Asia/Karachi", "(GMT +05:00) Islamabad, Karachi"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Paraguay Standard Time", "America/Asuncion", "(GMT -04:00) Asuncion"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Romance Standard Time", "Europe/Paris", "(GMT +01:00) Brussels, Copenhagen, Madrid, Paris"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Russian Standard Time", "Europe/Moscow", "(GMT +03:00) Moscow, St. Petersburg, Volgograd"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("SA Eastern Standard Time", "America/Cayenne", "(GMT -03:00) Cayenne, Fortaleza"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("SA Pacific Standard Time", "America/Bogota", "(GMT -05:00) Bogota, Lima, Quito"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("SA Western Standard Time", "America/La_Paz", "(GMT -04:00) Georgetown, La Paz, Manaus, San Juan"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Samoa Standard Time", "Pacific/Apia", "(GMT -11:00) Samoa"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("SE Asia Standard Time", "Asia/Bangkok", "(GMT +07:00) Bangkok, Hanoi, Jakarta"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Singapore Standard Time", "Asia/Singapore", "(GMT +08:00) Kuala Lumpur, Singapore"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("South Africa Standard Time", "Africa/Johannesburg", "(GMT +02:00) Harare, Pretoria"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Sri Lanka Standard Time", "Asia/Colombo", "(GMT +05:30) Sri Jayawardenepura"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Syria Standard Time", "Asia/Damascus", "(GMT +02:00) Damascus"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Taipei Standard Time", "Asia/Taipei", "(GMT +08:00) Taipei"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Tasmania Standard Time", "Australia/Hobart", "(GMT +10:00) Hobart"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Tokyo Standard Time", "Asia/Tokyo", "(GMT +09:00) Osaka, Sapporo, Tokyo"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Tonga Standard Time", "Pacific/Tongatapu", "(GMT +13:00) Nuku'alofa"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Ulaanbaatar Standard Time", "Asia/Ulaanbaatar", "(GMT +08:00) Ulaanbaatar"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("US Eastern Standard Time", "America/Indianapolis", "(GMT -05:00) Indiana (East)"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("US Mountain Standard Time", "America/Phoenix", "(GMT -07:00) Arizona"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("GMT ", "Etc/GMT", "(GMT) Coordinated Universal Time"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("GMT +12", "Etc/GMT-12", "(GMT +12:00) Coordinated Universal Time+12"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("GMT -02", "Etc/GMT+2", "(GMT -02:00) Coordinated Universal Time-02"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("GMT -11", "Etc/GMT+11", "(GMT -11:00) Coordinated Universal Time-11"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Venezuela Standard Time", "America/Caracas", "(GMT -04:30) Caracas"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Vladivostok Standard Time", "Asia/Vladivostok", "(GMT +10:00) Vladivostok"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("W. Australia Standard Time", "Australia/Perth", "(GMT +08:00) Perth"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("W. Central Africa Standard Time", "Africa/Lagos", "(GMT +01:00) West Central Africa"));
        TimeZoneList.ZONEMAPPINGS
                .add(new TimeZoneMapping("W. Europe Standard Time", "Europe/Berlin", "(GMT +01:00) Amsterdam, Berlin, Bern, Rome, Stockholm, Vienna"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("West Asia Standard Time", "Asia/Tashkent", "(GMT +05:00) Tashkent"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("West Pacific Standard Time", "Pacific/Port_Moresby", "(GMT +10:00) Guam, Port Moresby"));
        TimeZoneList.ZONEMAPPINGS.add(new TimeZoneMapping("Yakutsk Standard Time", "Asia/Yakutsk", "(GMT +09:00) Yakutsk"));
    }

    private static final TimeZoneList INSTANCE = new TimeZoneList();

    public static final TimeZoneList getInstance() {
        return TimeZoneList.INSTANCE;
    }

    public static TimeZone toJavaTimezone(final String tz) {
        for (final TimeZoneMapping map : TimeZoneList.ZONEMAPPINGS) {
            if (map.getWindowsStandardName().equalsIgnoreCase(tz)) {
                return TimeZoneList.toJavaTimezone(map);
            }
        }
        return TimeZone.getTimeZone(tz);
    }

    public static TimeZone toJavaTimezone(final TimeZoneMapping win) {
        return TimeZone.getTimeZone(win.getOlsonName());
    }

    private final List<TimeZoneWithDisplayNames> timeZones = new ArrayList<TimeZoneWithDisplayNames>();

    private TimeZoneList() {
        final HashSet<String> availableIdsSet = new HashSet<String>();
        for (final String availableId : TimeZone.getAvailableIDs()) {
            availableIdsSet.add(availableId);
        }
        for (final TimeZoneMapping zoneMapping : TimeZoneList.ZONEMAPPINGS) {
            final String id = zoneMapping.getOlsonName();
            if (!availableIdsSet.contains(id)) {
                throw new IllegalStateException("Unknown ID [" + id + "]");
            }
            final TimeZone timeZone = TimeZone.getTimeZone(id);
            this.timeZones.add(new TimeZoneWithDisplayNames(timeZone, zoneMapping.getWindowsDisplayName(), zoneMapping.getWindowsStandardName()));
        }
        Collections.sort(this.timeZones, new Comparator<TimeZoneWithDisplayNames>() {
            @Override
            public int compare(final TimeZoneWithDisplayNames a, final TimeZoneWithDisplayNames b) {
                final int diff = a.getTimeZone().getRawOffset() - b.getTimeZone().getRawOffset();
                if (diff < 0) {
                    return -1;
                } else if (diff > 0) {
                    return 1;
                } else {
                    return a.getDisplayName().compareTo(b.getDisplayName());
                }
            }
        });
    }

    public List<TimeZoneWithDisplayNames> getTimeZones() {
        return this.timeZones;
    }

}
