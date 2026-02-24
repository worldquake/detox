package hu.detox.szexpartnerek;

import hu.detox.config.Cfg2PropertySourceFactory;
import hu.detox.szexpartnerek.spring.admin.AdminCommand;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedString;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.stereotype.Component;

import java.text.Normalizer;

import static hu.detox.szexpartnerek.spring.SyncCommand.normalize;

@SpringBootApplication(scanBasePackages = "hu.detox.szexpartnerek.spring")
@Import({hu.detox.Main.class, AdminCommand.class})
@Component("szexpartnerek")
@PropertySource(value = "classpath:szexpartnerek.yaml", factory = Cfg2PropertySourceFactory.class)
@RequiredArgsConstructor
public class Main implements BeanPostProcessor {
    private static AttributedString PROMPT = new AttributedString("Szex> ");
    private static JdbcTemplate jdbc;

    public static CommandRegistration.Builder cr(String cmd) {
        return hu.detox.Main.cr(cmd).group("Szexpartnerek");
    }

    @Autowired
    private void init(JdbcTemplate szexpartnerekJDBC) {
        Main.jdbc = szexpartnerekJDBC;
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

    public static <T extends @Nullable Object> T query(String sql, ResultSetExtractor<T> rse) throws DataAccessException {
        return jdbc.query(sql, rse);
    }

    public static void exec(String sql) throws DataAccessException {
        jdbc.execute(sql);
    }

    public static JdbcTemplate jdbc() {
        return jdbc;
    }
}
