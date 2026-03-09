package hu.detox.szexpartnerek.sync;

import hu.detox.spring.ConditionalOnNoApp;
import hu.detox.spring.Shell;
import hu.detox.szexpartnerek.spring.SzexConfig;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedString;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication(
        scanBasePackages = "hu.detox.szexpartnerek.sync",
        scanBasePackageClasses = SzexConfig.class
)
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
