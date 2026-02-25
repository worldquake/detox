package hu.detox.spring;

import hu.detox.Main;
import hu.detox.utils.reflection.ReflectionUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.shell.command.CommandCatalog;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.jline.InteractiveShellRunner;
import org.springframework.shell.jline.NonInteractiveShellRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static hu.detox.spring.DetoxConfig.ctx;

@Component("detoxShell")
@RequiredArgsConstructor
public class Shell {
    private static final Map<String, ConfigurableApplicationContext> PCKS = new HashMap<>();
    private final NonInteractiveShellRunner runner;
    private final InteractiveShellRunner intRunner;

    public static String getCmd(CommandContext cmd) {
        String[] cmdStr = cmd.getCommandRegistration().getCommand().split(" ");
        return cmdStr[cmdStr.length - 1];
    }

    private static boolean addPackage(String pkg) {
        if (PCKS.containsKey(pkg)) return false;
        AnnotationConfigApplicationContext childContext = new AnnotationConfigApplicationContext();
        ConfigurableApplicationContext cac = (ConfigurableApplicationContext) ctx();
        childContext.setParent(cac);
        childContext.scan(pkg);
        PCKS.put(pkg, childContext);
        childContext.refresh();
        // Copy all beans from the child context to the parent context
        String[] beanNames = childContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            if (cac.containsBean(beanName)) continue;
            Object bean = childContext.getBean(beanName);
            cac.getBeanFactory().registerSingleton(beanName, bean);
        }
        childContext.publishEvent(new ContextRefreshedEvent(childContext));
        // Register commands manually after they are loaded
        CommandCatalog c = cac.getBean(CommandCatalog.class);
        cac.getBeansOfType(CommandRegistration.class).values().forEach(c::register);
        return true;
    }

    public <T> T loadBean(Class<T> clz) {
        T ret;
        try {
            ret = ctx().getBean(clz);
        } catch (NoSuchBeanDefinitionException ex) {
            String pkg = clz.getPackage().getName();
            addPackage(pkg);
            ret = ctx().getBean(clz);
        }
        return ret;
    }

    public static void destroy(String pkg) {
        ConfigurableApplicationContext ac = PCKS.remove(pkg);
        if (ac != null) ac.close();
    }

    public void execute(List<String> args) throws Exception {
        if (CollectionUtils.isNotEmpty(args)) {
            String pkg = args.getFirst();
            addPackage(Main.class.getPackage().getName() + "." + pkg);
            runner.run(args.toArray(new String[0]));
        }
    }

    public void execute(String... args) throws Exception {
        Class<?> ref = ReflectionUtils.getCaller(this).getOn();
        loadBean(ref);
        String[] cmd = Main.toCommand(ref).split(" ");
        args = ArrayUtils.addAll(cmd, args);
        runner.run(args);
    }

}
