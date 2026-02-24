package hu.detox;

import hu.detox.io.IOUtils;
import hu.detox.parsers.AmountCalculator;
import hu.detox.utils.reflection.ReflectionUtils;
import hu.detox.utils.strings.StringUtils;
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
import org.springframework.shell.command.CommandRegistration;
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

    public static CommandRegistration.Builder cr(String cmd) {
        Class<?> ref = ReflectionUtils.getCaller(null, els -> {
            StackTraceElement ret = null;
            boolean isCr;
            for (StackTraceElement el : els) {
                isCr = el.getMethodName().equals("cr");
                if (isCr) ret = el;
                else if (ret != null) {
                    if (cmd == null || cmd.startsWith(StringUtils.NULL_UNI)) ret = el;
                    break;
                }
            }
            return ret;
        }).getOn();
        String fcmd = hu.detox.Main.toCommand(ref) + (StringUtils.isBlank(cmd) ? "" : " " + cmd.replace(StringUtils.NULL_UNI, ""));
        return CommandRegistration.builder().group("DeToX").command(fcmd.trim());
    }

    public static String toCommand(Class<?> caller) {
        String pkg = caller.getPackage().getName()
                .replace(".spring", "");
        return toCommand(pkg);
    }

    public static String toCommand(String pkg) {
        pkg = pkg.replace(Main.class.getPackageName() + ".", "");
        String rc = System.getProperty("root").replace(Main.class.getPackageName() + ".", "");
        return pkg.replace(rc, "")
                .replaceFirst("^\\.", "")
                .replaceAll("\\.", " ");
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
