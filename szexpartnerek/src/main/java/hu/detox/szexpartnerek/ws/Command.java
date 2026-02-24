package hu.detox.szexpartnerek.ws;

import hu.detox.spring.Shell;
import hu.detox.utils.strings.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Configuration
@Component("szexpartnerekWSCmd")
@RequiredArgsConstructor
@ConditionalOnExpression("'hu.detox.szexpartnerek.ws'.startsWith('${root}')")
public class Command implements Function<CommandContext, Boolean> {
    private final hu.detox.szexpartnerek.ws.Main main;

    @Bean("szexpartnerekWSStart")
    public CommandRegistration start() {
        return hu.detox.szexpartnerek.Main.cr(StringUtils.NULL_UNI)
                .description("Starts the webservice if not started yet.")
                .withTarget().function(this::apply).and().build();
    }

    @Bean("szexpartnerekWSStop")
    public CommandRegistration stop() {
        return hu.detox.szexpartnerek.Main.cr(StringUtils.NULL_UNI)
                .description("Stops the webservice if started.")
                .withTarget().function(this::apply).and().build();
    }

    public Boolean apply(CommandContext cmd) {
        String cmdStr = Shell.getCmd(cmd);
        return main.apply(cmdStr);
    }
}
