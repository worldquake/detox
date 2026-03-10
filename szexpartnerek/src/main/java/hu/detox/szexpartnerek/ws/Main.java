package hu.detox.szexpartnerek.ws;

import hu.detox.spring.ConditionalOnNoApp;
import hu.detox.spring.Shell;
import org.jline.utils.AttributedString;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@ComponentScan(basePackages = "hu.detox.szexpartnerek.ws", basePackageClasses = hu.detox.szexpartnerek.Main.class)
@Component("szexpartnerekWSMain")
@ConditionalOnNoApp.Annotation
public class Main implements Function<String, Boolean>, ApplicationListener<ContextRefreshedEvent> {
    private static AttributedString PROMPT = new AttributedString("SexWS> ");
    private WebEndpointToggler toggler;

    public static void main(String[] args) throws Exception {
        Shell shell = hu.detox.Main.main(Main.class, args).getBean(Shell.class);
        shell.execute(args);
    }

    public boolean stop() {
        return toggler.remove(this.getClass().getPackageName());
    }

    public boolean start() {
        return toggler.register();
    }

    @Override
    public Boolean apply(String cmdStr) {
        Boolean start = "start".equals(cmdStr);
        if (start) start = start() ? true : null;
        else start = stop() ? false : null;
        return start;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (toggler == null) {
            toggler = event.getApplicationContext().getBean(WebEndpointToggler.class);
            toggler.initByPackage(Main.class.getPackageName());
        }
    }
}

