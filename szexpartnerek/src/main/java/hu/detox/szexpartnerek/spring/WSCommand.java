package hu.detox.szexpartnerek.spring;

import hu.detox.spring.Shell;
import hu.detox.szexpartnerek.Main;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${root}' == 'hu.detox.szexpartnerek'")
public class WSCommand {
    @SneakyThrows
    public Void ws(CommandContext ctx) {
        var ws = hu.detox.Main.ctx().getBean(Shell.class)
                .loadBean(hu.detox.szexpartnerek.ws.Main.class);
        return ws.apply(ctx.getParserResults().positional());
    }

    @Bean
    public CommandRegistration szexpartnerekWs() {
        return Main.cr("ws")
                .description("Manages the webservice.").withTarget().function(this::ws).and().build();
    }

}
