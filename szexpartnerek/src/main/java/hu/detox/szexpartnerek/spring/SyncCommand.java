package hu.detox.szexpartnerek.spring;

import hu.detox.spring.Shell;
import hu.detox.szexpartnerek.sync.Args;
import hu.detox.szexpartnerek.sync.Sync;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

import java.util.List;

import static hu.detox.spring.DetoxConfig.ctx;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'hu.detox.szexpartnerek'.startsWith('${root}')")
public class SyncCommand {
    public static String text(Element el, String... attrs) {
        if (el == null) return null;
        String data = null;
        for (String att : attrs) {
            data = normalize(el.attr(att));
            if (data != null) break;
        }
        if (data == null) data = normalize(el.text());
        return data;
    }

    public static String normalize(String data) {
        if (data == null) return data;
        data = data.trim()
                .replaceAll("[.,! ]{2,}", "!").replaceAll("[.,? ]{2,}", "?")
                .replaceAll("\\s+([.,?!])", "$1")
                .replaceAll("([.,?!])(\\S)", "$1 $2")
                .replaceAll("\\s+", " ");
        if (StringUtil.isBlank(data) || data.equals("-") || data.equalsIgnoreCase("null")) data = null;
        return data;
    }

    @SneakyThrows
    public List<Sync.Entry> sync(CommandContext ctx) {
        int maxBatch = ctx.getOptionValue("batch");
        boolean full = ctx.getOptionValue("full");
        List<String> what = ctx.getParserResults().positional();
        var args = new Args(full, maxBatch, what);
        var sync = ctx().getBean(Shell.class)
                .loadBean(hu.detox.szexpartnerek.sync.Main.class);
        return sync.apply(args);
    }

    @Bean
    public CommandRegistration szexpartnerekSync() {
        return hu.detox.szexpartnerek.Main.cr("sync")
                .description("Synchronizes data from the specified sources.").withOption()
                .longNames("batch").shortNames('b').arity(1, 1).type(int.class).defaultValue("0").description("Maximum batch size after flush must happen.")
                .and().withOption()
                .longNames("full").shortNames('f').arity(0, 1).type(boolean.class).defaultValue("false").description("Full sync of all data, not excluding recently updated records.")
                .and().withTarget().function(this::sync).and().build();
    }

}
