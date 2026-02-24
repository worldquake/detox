package hu.detox.spring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import hu.detox.Agent;
import hu.detox.Main;
import hu.detox.utils.strings.StringUtils;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;

import java.util.List;

@Configuration
@ConditionalOnExpression("'hu.detox'.startsWith('${root}')")
public class RunCommand {

    @Bean
    public CommandRegistration run() {
        return Main.cr("run")
                .command("run").description("Runs the basic setup, and positional arguments").withOption()
                .longNames("test").shortNames('t').arity(0, 0).type(boolean.class).defaultValue("false").description("Sets the system to test mode (ideally no execution).")
                .and().withOption()
                .longNames("log").shortNames('l').position(1).arity(1, 1).type(String.class).defaultValue("").description("Sets logger configuration.")
                .and().withTarget().function(this::run).and().build();
    }

    @SneakyThrows
    private String run(CommandContext ctx) {
        Agent.test = ctx.getOptionValue("test");
        String log = ctx.getOptionValue("log");
        List<String> positional = ctx.getParserResults().positional();
        if (StringUtils.isNotBlank(log)) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(Level.toLevel(log, Level.INFO));
        }
        Shell sh = Main.ctx().getBean(Shell.class);
        sh.execute(positional);
        return CollectionUtils.isEmpty(positional) ? null : positional.get(0);
    }

}
