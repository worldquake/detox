package hu.detox.szexpartnerek.admin;

import hu.detox.szexpartnerek.Main;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

import java.util.LinkedList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AdminCommand {
    private final List<Admin> admins;

    @SneakyThrows
    public Object admin(CommandContext ctx) {
        List<String> doOnly;
        List<String> what = new LinkedList<>(ctx.getParserResults().positional()
                .stream().toList());
        doOnly = (CollectionUtils.isEmpty(what)) ? null : what;
        admins.stream().filter(entry -> doOnly == null || doOnly.remove(entry.getId()))
                .forEach(Admin::run);
        return null;
    }

    @Bean("szexpartnerekAdmin")
    public CommandRegistration admin() {
        return Main.cr("admin")
                .description("Does any administration you instruct.")
                .withTarget().function(this::admin).and().build();
    }
}
