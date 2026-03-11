package hu.detox.spring;

import com.fasterxml.jackson.databind.JsonNode;
import hu.Main;
import hu.detox.parsers.AmountCalculator;
import hu.detox.parsers.JSonUtils;
import lombok.SneakyThrows;
import org.jscience.physics.amount.Amount;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@Configuration
@ComponentScan
@Import(Main.class)
public class DetoxConfig implements BeanPostProcessor {
    private static AsyncTaskExecutor executor;
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
            ApplicationContext par = Main.ctx().getParent();
            if (par != null && par.containsBean(beanName)) bean = null;
            else System.out.println("Bean " + beanName + " := " + bean);
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    public static Amount<?> toAmount(String expr) {
        return AmountCalculator.INSTANCE.calc(expr);
    }

    public static <T> Future<T> async(Callable<T> callable) {
        return executor.submit(callable);
    }

    public static <T> Future<T> async(Runnable callable) {
        return (Future<T>) executor.submit(callable);
    }

    public static GenericConversionService converter() {
        return converter;
    }

}
