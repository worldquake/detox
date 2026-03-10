package hu.detox.szexpartnerek.sync;

import hu.detox.spring.ConditionalOnNoApp;
import hu.detox.spring.Shell;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedString;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@ComponentScan(basePackages = "hu.detox.szexpartnerek.sync", basePackageClasses = hu.detox.szexpartnerek.Main.class)
@Component("szexpartnerekSyncMain")
@RequiredArgsConstructor
@ConditionalOnNoApp.Annotation
public class Main {
    private static AttributedString PROMPT = new AttributedString("SyncSzex> ");

    public static void main(String[] args) throws Exception {
        Shell shell = hu.detox.Main.main(Main.class, args).getBean(Shell.class);
        shell.execute(args);
    }

}
