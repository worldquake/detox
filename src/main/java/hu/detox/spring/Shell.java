package hu.detox.spring;

import hu.detox.Agent;
import hu.detox.Main;
import hu.detox.utils.reflection.ReflectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.shell.command.CommandCatalog;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.jline.InteractiveShellRunner;
import org.springframework.shell.jline.NonInteractiveShellRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("detoxShell")
@RequiredArgsConstructor
public class Shell implements BeanPostProcessor, ApplicationListener<ApplicationReadyEvent> {
    private static final Map<String, ConfigurableApplicationContext> PCKS = new HashMap<>();
    private final NonInteractiveShellRunner runner;
    private final InteractiveShellRunner intRunner;

    public static CommandRegistration.Builder cr() {
        return CommandRegistration.builder().group("DeToX");
    }

    private static boolean addPackage(String pkg) {
        if (PCKS.containsKey(pkg)) return false;
        AnnotationConfigApplicationContext childContext = new AnnotationConfigApplicationContext();
        ConfigurableApplicationContext cac = (ConfigurableApplicationContext) Main.ctx();
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
            ret = Main.ctx().getBean(clz);
        } catch (NoSuchBeanDefinitionException ex) {
            String pkg = clz.getPackage().getName();
            addPackage(pkg);
            ret = Main.ctx().getBean(clz);
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
        String pkg = ref.getPackage().getName().substring(Main.class.getPackage().getName().length() + 1);
        String[] cmd = pkg.split("\\.");
        if (!Main.isDirectCaller(Main.class)) cmd = ArrayUtils.remove(cmd, 0);
        args = ArrayUtils.addAll(cmd, args);
        runner.run(args);
    }

    @SneakyThrows
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (System.console() != null || Agent.IDE) {
            intRunner.run((String[]) null);
        }
    }
}
