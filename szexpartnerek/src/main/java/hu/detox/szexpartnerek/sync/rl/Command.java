package hu.detox.szexpartnerek.sync.rl;

import hu.detox.spring.Shell;
import hu.detox.szexpartnerek.sync.Args;
import hu.detox.szexpartnerek.sync.Sync;
import hu.detox.utils.strings.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Configuration
@RequiredArgsConstructor
@Component("szexpartnerekSyncCmd")
@ConditionalOnExpression("'hu.detox.szexpartnerek.sync.rl'.startsWith('${root}')")
public class Command implements Function<CommandContext, Sync.Entry> {
    private final hu.detox.szexpartnerek.sync.Shell shell;

    @Bean
    public CommandRegistration szexpartnerekSyncRL() {
        return hu.detox.szexpartnerek.Main.cr(StringUtils.NULL_UNI + "rl")
                .description("Starts synchronization only for Rosszlanyok.")
                .withTarget().function(this::apply).and().build();
    }

    public Sync.Entry apply(CommandContext cmd) {
        String cmdStr = Shell.getCmd(cmd);
        var sArgs = new ArrayList<>(cmd.getParserResults().positional());
        sArgs.addFirst(cmdStr);
        var args = new Args(false, 0, sArgs);
        List<Sync.Entry> lst = shell.apply(args);
        return lst.isEmpty() ? null : lst.getFirst();
    }
}
