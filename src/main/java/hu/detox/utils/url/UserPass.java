package hu.detox.utils.url;

import hu.detox.utils.StringUtils;
import lombok.Data;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;

import java.text.MessageFormat;

@Data
public class UserPass extends User {
    private String password;

    public UserPass() {
        this(null);
    }

    public UserPass(final String auth) {
        if (auth == null) {
            return;
        }
        final int j = auth.indexOf(":");
        if (j < 0) {
            this.setUserName(URL.decode(auth));
        } else {
            this.setUserName(URL.decode(auth.substring(0, j)));
            this.setPassword(URL.decode(auth.substring(j + 1)));
        }
    }

    public UserPass(final String u, final String p) {
        this.setUserName(u);
        this.setPassword(p);
    }

    public Credentials toCredentials() {
        return new UsernamePasswordCredentials(this.getUserName(), this.getPassword());
    }

    @Override
    public String toString() {
        return "UserPass " + this.getFullUserName() + // Login
                (this.getPassword() == null ? StringUtils.EMPTY : ", pass[" + this.getPassword().length() + "] ");
    }

    public String toString(String patt, final String pass) {
        if (StringUtils.isBlank(patt)) {
            patt = this.getFullUserName() + ":{0}";
            patt = MessageFormat.format(patt, pass == null ? this.getPassword() : pass);
        } else {
            patt = MessageFormat.format(patt, this.getUserName(), this.getDomain(), pass == null ? this.getPassword() : pass);
        }
        return patt;
    }

}
