package hu.detox.utils.url;

import hu.detox.utils.strings.StringUtils;

import java.text.MessageFormat;

public class User {
    public static final char DOMAIN_WIN = '\\';
    public static final char DOMAIN_EMAIL = '@';

    private char domainSep = User.DOMAIN_WIN;
    private String name;
    protected String email;
    private String userName;
    private String domain;

    public String calcDisplayName() {
        return this.name == null ? this.userName : this.name;
    }

    public String getDomain() {
        return this.domain;
    }

    public String getEmail() {
        if (this.email == null) {
            if (this.domain == null) {
                return null;
            }
            this.email = this.getUserName() + User.DOMAIN_EMAIL + this.domain;
        }
        return this.email;
    }

    public String getFullUserName() {
        String patt;
        if (StringUtils.isBlank(this.getDomain())) {
            patt = "{0}";
        } else if (this.domainSep == User.DOMAIN_WIN) {
            patt = "{1}" + this.domainSep + "{0}";
        } else {
            patt = "{0}" + this.domainSep + "{1}";
        }
        patt = MessageFormat.format(patt, this.getUserName(), this.getDomain());
        return patt;
    }

    public String getLogin() {
        if (this.domain == null) {
            return this.getUserName();
        }
        return this.getDomain() + User.DOMAIN_WIN + this.getUserName();
    }

    public String getName() {
        return this.name;
    }

    public String getUserName() {
        return this.userName;
    }

    public boolean isEmail() {
        return this.domainSep == User.DOMAIN_EMAIL;
    }

    public void setDomain(final String domain) {
        this.domain = domain;
    }

    public void setEmail(final String email) {
        this.email = email;
        if (this.userName == null && this.domain == null) {
            this.setUserName(email);
        }
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
        if (userName != null) {
            String[] ud = userName.split("[\\" + User.DOMAIN_WIN + "/]");
            if (ud.length == 2) {
                this.domainSep = User.DOMAIN_WIN;
                this.setDomain(ud[0]);
                this.userName = ud[1];
            } else {
                ud = StringUtils.split(userName, User.DOMAIN_EMAIL);
                if (ud.length == 2) {
                    this.domainSep = User.DOMAIN_EMAIL;
                    if (StringUtils.isEmpty(this.domain)) {
                        this.setDomain(ud[1]);
                        this.userName = ud[0];
                    }
                }
            }
        }
    }

    public void setWindows(final boolean win) {
        this.domainSep = win ? User.DOMAIN_WIN : User.DOMAIN_EMAIL;
    }
}
