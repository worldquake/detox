package hu.detox.szexpartnerek.sync;

import hu.detox.szexpartnerek.Main;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

import java.util.ArrayList;
import java.util.function.Function;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${root}' == 'hu.detox.szexpartnerek.sync'")
public class Command implements Function<CommandContext, Sync.Entry> {
    private final hu.detox.szexpartnerek.sync.Main main;

    @Bean
    public CommandRegistration szexpartnerekSyncRL() {
        return Main.cr("rl")
                .description("Starts synchronization only for Rosszlanyok.")
                .withTarget().function(this::apply).and().build();
    }

    public Sync.Entry apply(CommandContext cmd) {
        String cmdStr = cmd.getCommandRegistration().getCommand();
        var sArgs = new ArrayList<>(cmd.getParserResults().positional());
        sArgs.addFirst(cmdStr);
        var args = new Args(false, 0, sArgs);
        return main.apply(args).getFirst();
    }
}
