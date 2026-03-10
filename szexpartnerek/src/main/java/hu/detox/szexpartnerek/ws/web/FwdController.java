package hu.detox.szexpartnerek.ws.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class FwdController {
    static final String FWD = "forward:/szexpartnerek/index.html";

    @RequestMapping(value = {"/{path}"})
    public String forward() {
        return FWD;
    }
}
