package hu.detox.spring;

import hu.detox.Agent;
import hu.detox.config.Cfg2PropertySourceFactory;
import hu.detox.io.IOUtils;
import hu.detox.parsers.AmountCalculator;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.jscience.physics.amount.Amount;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@Configuration
@ComponentScan
//@Import({Shell.class, Commands.class})
public class DetoxConfig implements ApplicationContextAware, BeanPostProcessor {
    private static ApplicationContext context;
    private static AsyncTaskExecutor executor;
    private static PropertyResolver resolver;
    private static ConversionService converter;

    @Bean
    public ConversionService conversionService() {
        return new DefaultFormattingConversionService();
    }

    @Override
    public @Nullable Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AsyncTaskExecutor) {
            executor = (AsyncTaskExecutor) bean;
        } else if (bean instanceof ConversionService) {
            converter = (ConversionService) bean;
        } else {
            System.out.println(beanName);
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        context = ctx;
        resolver = context.getBean(PropertyResolver.class);
        File my = Agent.getFile("application.yaml", FileFilterUtils.fileFileFilter());
        if (my != null) {
            FileSystemResource propertySource = new FileSystemResource(my);
            ctx.getBean(ConfigurableEnvironment.class).getPropertySources()
                    .addFirst(Cfg2PropertySourceFactory.make(propertySource, null));
        }
        IOUtils.initStatic();
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
}
