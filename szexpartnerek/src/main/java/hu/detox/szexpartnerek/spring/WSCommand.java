package hu.detox.szexpartnerek.spring;

import hu.detox.spring.Shell;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'hu.detox.szexpartnerek'.startsWith('${root}')")
public class WSCommand {
    @SneakyThrows
    public Boolean ws(CommandContext ctx) {
        var ws = hu.detox.Main.ctx().getBean(Shell.class)
                .loadBean(hu.detox.szexpartnerek.ws.Main.class);
        List<String> args = ctx.getParserResults().positional();
        return ws.apply(args.isEmpty() ? "start" : args.getFirst());
    }

    @Bean
    public CommandRegistration szexpartnerekWs() {
        return hu.detox.szexpartnerek.Main.cr("ws")
                .description("Manages the webservice.").withTarget()
                .function(this::ws).and().build();
    }

}
