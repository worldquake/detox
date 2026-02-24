package hu.detox;

import hu.detox.io.IOUtils;
import hu.detox.parsers.AmountCalculator;
import hu.detox.utils.reflection.ReflectionUtils;
import org.jline.utils.AttributedString;
import org.jscience.physics.amount.Amount;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Quit;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@SpringBootApplication(scanBasePackages = "hu.detox.spring")
@Command(group = "DeToX base commands")
@ShellComponent
@Primary
public class Main implements ApplicationContextAware, BeanPostProcessor, Quit.Command, PromptProvider {
    public static AttributedString PROMPT = new AttributedString("DeToX> ");
    private static ApplicationContext context;
    private static AsyncTaskExecutor executor;
    private static PropertyResolver resolver;
    private static ConversionService converter;

    public static boolean isDirectCaller(Class<?> caller) {
        return isDirectCaller(caller.getPackage().getName());
    }

    public static boolean isDirectCaller(String pkg) {
        String rc = System.getProperty("root");
        return pkg.equals(rc);
    }

    public static Amount<?> toAmount(String expr) {
        return AmountCalculator.INSTANCE.calc(expr);
    }

    public static String prop(String prop) {
        return prop(prop, null);
    }

    public static String prop(String prop, String def) {
        return resolver.getProperty(prop, def);
    }

    public static <T> Future<T> async(Callable<T> callable) {
        return executor.submit(callable);
    }

    public static <T> Future<T> async(Runnable callable) {
        return (Future<T>) executor.submit(callable);
    }

    public static Resource resource(String res) {
        return context.getResource(res);
    }

    public static ConversionService converter() {
        return converter;
    }

    public static ApplicationContext ctx() {
        return context;
    }

    @Bean
    public ConversionService conversionService() {
        return new DefaultFormattingConversionService();
    }

    public static void main(String[] args) throws Exception {
        main(Main.class, args);
    }

    public static ApplicationContext main(Class<?> any, String[] args) throws Exception {
        System.setProperty("root", any.getPackageName());
        PROMPT = ReflectionUtils.getProperty(any, "PROMPT");
        Agent.init();
        SpringApplication application = new SpringApplication(any);
        context = application.run(args);
        return context;
    }

    @ShellMethod(value = "Exit the JVM.", key = {"quit", "exit"})
    public void quit() {
        System.exit(0);
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Main.context = ctx;
        Main.resolver = context.getBean(PropertyResolver.class);
        IOUtils.initStatic();
    }

    @Override
    public @Nullable Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AsyncTaskExecutor) {
            Main.executor = (AsyncTaskExecutor) bean;
        } else if (bean instanceof ConversionService) {
            Main.converter = (ConversionService) bean;
        } else {
            System.out.println(beanName);
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public AttributedString getPrompt() {
        return PROMPT;
    }
}
