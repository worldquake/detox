package hu.detox.szexpartnerek.sync.rl;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@ConfigurationProperties("rl.client")
public class Http extends hu.detox.utils.Http {
    private String login;
    private static final String LIKE = "action=likes&action_parameter=cancel&like_rate=cancel";
    private static final String DISLIKE = "action=likes&action_parameter=-1&like_rate=-1";

    Http() {
        super("https://rosszlanyok.hu/");
    }

    public void doLikeAction(boolean yn, Object id) {
        post("4layer/right_functions.php?id=" + id, yn ? LIKE : DISLIKE);
    }

    public void setLogin(String l) {
        this.login = l;
        if (l == null) {
            get("?logout=1");
        } else {
            post("belepes", l);
        }
    }
}
