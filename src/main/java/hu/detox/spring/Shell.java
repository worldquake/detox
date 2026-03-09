package hu.detox.spring;

import hu.detox.Agent;
import hu.detox.Main;
import hu.detox.utils.reflection.ReflectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.convert.converter.Converter;
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
public class Shell implements ApplicationListener<SpringApplicationEvent>, AutoCloseable {
    private static final Map<String, ConfigurableApplicationContext> PCKS = new HashMap<>();
    private final NonInteractiveShellRunner runner;
    private final InteractiveShellRunner intRunner;
    private transient Thread intThread;

    public static String getCmd(CommandContext cmd) {
        String[] cmdStr = cmd.getCommandRegistration().getCommand().split(" ");
        return cmdStr[cmdStr.length - 1];
    }

    private static boolean addPackage(String pkg) {
        if (PCKS.containsKey(pkg)) return false;
        AnnotationConfigApplicationContext childContext = new AnnotationConfigApplicationContext();
        ConfigurableApplicationContext cac = (ConfigurableApplicationContext) ctx();
        childContext.setParent(cac);
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(childContext);
        scanner.addExcludeFilter(new ConditionalOnNoApp());
        scanner.scan(pkg);
        childContext.refresh();
        PCKS.put(pkg, childContext);
        CommandCatalog cc = cac.getBean(CommandCatalog.class);
        DefaultListableBeanFactory parBf = (DefaultListableBeanFactory) cac.getBeanFactory();
        // Copy all beans from the child context to the parent context
        String[] beanNames = childContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            if (cac.containsBean(beanName)) continue;
            Object bean = childContext.getBean(beanName);
            if (bean instanceof Converter<?, ?> c) DetoxConfig.converter().addConverter(c);
            if (bean instanceof CommandRegistration c) cc.register(c);
            BeanDefinition def = childContext.getBeanDefinition(beanName);
            if (def.isSingleton()) parBf.registerSingleton(beanName, bean);
            else parBf.registerBeanDefinition(beanName, def);
        }
        childContext.publishEvent(new ContextRefreshedEvent(childContext));
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

    @SneakyThrows
    @Override
    public void onApplicationEvent(SpringApplicationEvent event) {
        if (event instanceof ApplicationStartedEvent) Agent.closeSplash();
        if (event instanceof ApplicationReadyEvent && (Agent.IDE || System.console() != null)) {
            intThread = Thread.currentThread();
            intRunner.run((String[]) null);
        }
    }

    @Override
    public void close() throws Exception {
        if (intThread != null) {
            intThread.interrupt();
            intThread = null;
        }
    }
}
