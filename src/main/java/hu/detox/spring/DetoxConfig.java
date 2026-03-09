package hu.detox.spring;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.Agent;
import hu.detox.config.Cfg2PropertySourceFactory;
import hu.detox.io.IOUtils;
import hu.detox.parsers.AmountCalculator;
import hu.detox.parsers.JSonUtils;
import lombok.SneakyThrows;
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
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.AsyncTaskExecutor;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@Configuration
@ComponentScan
public class DetoxConfig implements ApplicationContextAware, BeanPostProcessor {
    private static ApplicationContext context;
    private static AsyncTaskExecutor executor;
    private static PropertyResolver resolver;
    private static GenericConversionService converter;

    @Bean
    Converter<String, JsonNode> nodeConverter() {
        return new Converter<>() {
            @SneakyThrows
            @Override
            public JsonNode convert(String s) {
                return JSonUtils.OM.readValue(s, JsonNode.class);
            }
        };
    }

    @Override
    public @Nullable Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AsyncTaskExecutor && executor != null) return executor;
        if (bean instanceof ConversionService && converter != null) return converter;
        if (bean instanceof AsyncTaskExecutor) executor = (AsyncTaskExecutor) bean;
        else if (bean instanceof ConversionService) converter = (GenericConversionService) bean;
        else {
            ApplicationContext par = context.getParent();
            if (par != null && par.containsBean(beanName)) bean = null;
            else System.out.println("Bean " + beanName + " := " + bean);
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        context = ctx;
        if (resolver != null) return;
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

    public static GenericConversionService converter() {
        return converter;
    }

    public static ApplicationContext ctx() {
        return context;
    }
}
