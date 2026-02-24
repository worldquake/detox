package hu.detox.szexpartnerek.ws;

import hu.detox.spring.Shell;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedString;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.util.function.Function;

@SpringBootApplication
@Import({hu.detox.szexpartnerek.Main.class})
@RequiredArgsConstructor
public class Main implements Function<String, Boolean> {
    private static AttributedString PROMPT = new AttributedString("SexWS> ");
    private final WebEndpointToggler toggler;

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
}

