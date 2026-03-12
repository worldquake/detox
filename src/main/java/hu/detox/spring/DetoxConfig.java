package hu.detox.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.Main;
import hu.detox.parsers.AmountCalculator;
import hu.detox.utils.CollectionUtils;
import lombok.Data;
import lombok.SneakyThrows;
import org.jscience.physics.amount.Amount;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.core.DefaultHttpLogFormatter;
import org.zalando.logbook.core.DefaultHttpLogWriter;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.StreamSupport;

import static hu.detox.parsers.JSonUtils.OM;
import static org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil.resolveConnectionProperties;

@Configuration
@ComponentScan
@DependsOn(Main.BEAN_NAME)
@Import(Main.class)
public class DetoxConfig implements BeanPostProcessor {
    public static final Logbook LOGBOOK = Logbook.builder()
            .sink(new DefaultSink(
                    new DefaultHttpLogFormatter(),
                    new DefaultHttpLogWriter()
            ))
            .build();

    private static AsyncTaskExecutor executor;
    private static GenericConversionService converter;

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

    @Bean
    Converter<String, JsonNode> nodeConverter() {
        return new Converter<>() {
            @SneakyThrows
            @Override
            public JsonNode convert(String s) {
                return OM.readValue(s, JsonNode.class);
            }
        };
    }

    @Override
    public @Nullable Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AsyncTaskExecutor && executor != null) return executor;
        if (bean instanceof ConversionService && converter != null) return converter;
        if (bean instanceof AsyncTaskExecutor) executor = (AsyncTaskExecutor) bean;
        else if (bean instanceof ConversionService) converter = (GenericConversionService) bean;
        else if (Main.ctx() != null) {
            ApplicationContext par = Main.ctx().getParent();
            if (par != null && par.containsBean(beanName)) bean = null;
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

}
