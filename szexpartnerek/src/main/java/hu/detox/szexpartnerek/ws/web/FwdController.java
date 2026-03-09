package hu.detox.szexpartnerek.ws.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@ConditionalOnExpression("!'${root}'.startsWith('hu.detox.szexpartnerek')")
public class FwdController {
    static final String FWD = "forward:/szexpartnerek/index.html";

    @RequestMapping(value = {"/szexpartnerek/{path:^(?!assets).*}"})
    public String forward() {
        return FWD;
    }
}
