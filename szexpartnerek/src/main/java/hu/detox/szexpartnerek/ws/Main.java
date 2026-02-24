package hu.detox.szexpartnerek.ws;

import hu.detox.spring.Shell;
import org.jline.utils.AttributedString;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.function.Function;

@SpringBootApplication
@Import({hu.detox.szexpartnerek.Main.class})
public class Main implements Function<String, Boolean>, ApplicationListener<ContextRefreshedEvent> {
    private static AttributedString PROMPT = new AttributedString("SexWS> ");
    private WebEndpointToggler toggler;

    public static void main(String[] args) throws Exception {
        Shell shell = hu.detox.Main.main(Main.class, args).getBean(Shell.class);
        shell.execute(args);
    }

    public void stop() {
        toggler.remove(this.getClass().getPackageName());
    }

    public void start() {
        toggler.register();
    }

    @Override
    public Boolean apply(String cmdStr) {
        boolean start = "start".equals(cmdStr);
        if (start) start();
        else stop();
        return start;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        toggler = hu.detox.Main.ctx().getBean(WebEndpointToggler.class);
    }
}

