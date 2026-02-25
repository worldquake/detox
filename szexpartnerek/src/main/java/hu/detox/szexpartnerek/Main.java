package hu.detox.szexpartnerek;

import hu.detox.szexpartnerek.spring.SzexConfig;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedString;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.stereotype.Component;

import java.text.Normalizer;

import static hu.detox.szexpartnerek.spring.SyncCommand.normalize;

@SpringBootApplication(scanBasePackageClasses = SzexConfig.class)
@Component("szexpartnerek")
@RequiredArgsConstructor
public class Main implements BeanPostProcessor {
    private static AttributedString PROMPT = new AttributedString("Szex> ");

    public static CommandRegistration.Builder cr(String cmd) {
        return hu.detox.Main.cr(cmd).group("Szexpartnerek");
    }

    public static String toEnumLike(String input) {
        if (input == null) return null;
        String normalized = normalize(Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", ""));
        if (normalized == null) return null;
        String underscored = normalized.replaceAll("[^A-Za-z_ ]", "");
        underscored = underscored.replaceAll("\\s+", "_");
        return underscored.toUpperCase();
    }

    public static void main(String[] args) throws Exception {
        hu.detox.Main.main(Main.class, args);
    }
}
