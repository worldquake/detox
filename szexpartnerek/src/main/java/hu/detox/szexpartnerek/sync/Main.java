package hu.detox.szexpartnerek.sync;

import hu.detox.Agent;
import hu.detox.spring.Shell;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jline.utils.AttributedString;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@SpringBootApplication
@Import({hu.detox.szexpartnerek.Main.class})
@Component("szexpartnerekSyncMain")
@RequiredArgsConstructor
public class Main implements Function<Args, List<Sync.Entry>>, AutoCloseable, ApplicationListener<ContextRefreshedEvent> {
    private static AttributedString PROMPT = new AttributedString("SyncSzex> ");
    public static final ThreadLocal<Args> ARGS = new ThreadLocal<>();

    private final List<Sync.Entry> syncs;

    public static void main(String[] args) throws Exception {
        Shell shell = hu.detox.Main.main(Main.class, args).getBean(Shell.class);
        shell.execute(args);
    }

    @SneakyThrows
    public List<Sync.Entry> apply(Args args) {
        ARGS.set(args);
        List<String> doOnly = args.getIds();
        List<Sync.Entry> entries = syncs.stream().filter(entry -> doOnly == null || doOnly.remove(entry.getId())).toList();
        try (FKOff fkOff = new FKOff()) {
            for (Sync.Entry s : entries) {
                int skp = s.syncGetSkipped(doOnly, true);
                System.err.println("Skipped " + skp + " on " + s.getId());
            }
            execute("1_stats.sql", "2_mod.sql", "3_materialize.sql");
        } finally {
            close();
        }
        return entries;
    }

    private void execute(String... sqls) throws DataAccessException {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        for (String sql : sqls) {
            populator.addScript(Agent.resource("sql/szexpartnerek/" + sql));
        }
        populator.execute(hu.detox.szexpartnerek.Main.jdbc().getDataSource());
    }

    @Override
    public void close() throws Exception {
        if (syncs != null) for (Sync.Entry s : syncs) s.close();
    }

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        AbstractTrafoEngine.initEnums();
    }

}
