package hu.detox.szexpartnerek.ws;

import hu.detox.spring.Shell;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedString;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.function.Function;

@SpringBootApplication
@Import({hu.detox.szexpartnerek.Main.class})
@RequiredArgsConstructor
public class Main implements Function<List<String>, Void> {
    private static AttributedString PROMPT = new AttributedString("SexWS> ");
    private WebEndpointToggler toggler;

    public static void main(String[] args) throws Exception {
        Shell shell = hu.detox.Main.main(Main.class, args).getBean(Shell.class);
        shell.execute(args);
    }

    @Override
    public Void apply(List<String> strings) {
        if (strings.isEmpty()) return null;
        switch (strings.getFirst()) {
            case "stop":
                toggler.remove(this.getClass().getPackageName());
                break;
            case "start":
                toggler.register();
                break;
        }
        return null;
    }
}

