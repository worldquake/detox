package hu.detox.szexpartnerek.spring.admin;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static hu.detox.spring.DetoxConfig.ctx;

@Configuration
@RequiredArgsConstructor
public class AdminCommand {

    @SneakyThrows
    public Object admin(CommandContext ctx) {
        Collection<Admin> admins = ctx().getBeansOfType(Admin.class).values();
        List<String> what = ctx.getParserResults().positional();
        if (CollectionUtils.isEmpty(what)) {
            for (Admin a : admins) {
                System.err.println(a.toString());
            }
        } else {
            List<String> doFinal = new LinkedList<>(ctx.getParserResults().positional());
            admins.stream().filter(entry -> doFinal.remove(entry.getId()))
                    .forEach(Admin::run);
        }
        return null;
    }

    @Bean("szexpartnerekAdmin")
    public CommandRegistration admin() {
        return hu.detox.szexpartnerek.Main.cr(null)
                .description("Does any administration you instruct.")
                .withTarget().function(this::admin).and().build();
    }
}
