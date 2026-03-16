package hu.detox.szexpartnerek.sync;

import hu.detox.spring.ConditionalOnNoApp;
import hu.detox.spring.Shell;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedString;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@ComponentScan(basePackages = "hu.detox.szexpartnerek.sync", basePackageClasses = hu.detox.szexpartnerek.spring.SzexConfig.class)
@Component("szexpartnerekSyncMain")
@EnableAutoConfiguration(exclude = WebMvcAutoConfiguration.class)
@RequiredArgsConstructor
@ConditionalOnNoApp.Annotation
public class Main {
    private static AttributedString PROMPT = new AttributedString("SyncSzex> ");

    public static void main(String[] args) throws Exception {
        Shell shell = hu.detox.Main.main(false, Main.class, args).getBean(Shell.class);
        shell.execute(args);
    }

}
