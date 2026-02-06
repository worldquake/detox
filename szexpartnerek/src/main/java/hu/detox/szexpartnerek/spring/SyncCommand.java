package hu.detox.szexpartnerek.spring;

import hu.detox.spring.Shell;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.sync.AbstractTrafoEngine;
import hu.detox.szexpartnerek.sync.Args;
import hu.detox.szexpartnerek.sync.Sync;
import hu.detox.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

import java.util.LinkedList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${root}' == 'hu.detox.szexpartnerek'")
public class SyncCommand implements ApplicationListener<ContextRefreshedEvent> {
    private static final int MAX_BATCH_DEFAULT = 100;

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
        data = data.trim();
        if (StringUtil.isBlank(data) || data.equals("-") || data.equalsIgnoreCase("null")) data = null;
        return data;
    }

    @SneakyThrows
    public List<Sync.Entry> sync(CommandContext ctx) {
        int maxBatch = ctx.getOptionValue("batch");
        boolean full = ctx.getOptionValue("full");
        List<String> what = new LinkedList<>(ctx.getParserResults().positional()
                .stream().map(StringUtils::toRootLowerCase).toList());
        var args = new Args(full, maxBatch, hu.detox.utils.CollectionUtils.isEmpty(what) ? null : what);
        var sync = hu.detox.Main.ctx().getBean(Shell.class)
                .loadBean(hu.detox.szexpartnerek.sync.Main.class);
        return sync.apply(args);
    }

    @Bean
    public CommandRegistration szexpartnerekSync() {
        return Main.cr("sync")
                .description("Synchronizes data from the specified sources.").withOption()
                .longNames("batch").shortNames('b').arity(1, 1).type(int.class).defaultValue("" + MAX_BATCH_DEFAULT).description("Maximum batch size after flush must happen.")
                .and().withOption()
                .longNames("full").shortNames('f').arity(0, 1).type(boolean.class).defaultValue("false").description("Full sync of all data, not excluding recently updated records.")
                .and().withTarget().function(this::sync).and().build();
    }

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        AbstractTrafoEngine.initEnums();
    }
}
