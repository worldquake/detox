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
@EnableConfigurationProperties(DetoxConfig.OpenAiProperties.class)
public class DetoxConfig implements BeanPostProcessor {
    private static final Logbook LOGBOOK = Logbook.builder()
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

    private ClientHttpResponse wrapResponse(byte[] body, ClientHttpResponse original) {
        return new ClientHttpResponse() {
            public HttpStatusCode getStatusCode() throws IOException {
                return original.getStatusCode();
            }

            public String getStatusText() throws IOException {
                return original.getStatusText();
            }

            public HttpHeaders getHeaders() {
                return original.getHeaders();
            }

            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            public void close() {
                original.close();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi openAiApi(OpenAiProperties props, OpenAiConnectionProperties commonProperties, OpenAiChatProperties chatProperties,
                               ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                               ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                               ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
        OpenAIAutoConfigurationUtil.ResolvedConnectionProperties resolved = resolveConnectionProperties(
                commonProperties, chatProperties, "chat");
        var build = restClientBuilderProvider.getObject();
        if (props.headers != null) for (Map.Entry e : props.headers.entrySet()) {
            build.defaultHeader((String) e.getKey(), (String) e.getValue());
        }
        var restClient = build
                .requestInterceptor(new LogbookClientHttpRequestInterceptor(LOGBOOK))
                .requestInterceptor((request, body, execution) -> {
                    if (props.anthropic) body = toAnthropic(body);
                    if (props.header != null) {
                        HttpHeaders headers = request.getHeaders();
                        List<String> vals = headers.remove(HttpHeaders.AUTHORIZATION);
                        if (CollectionUtils.isNotEmpty(vals)) {
                            headers.add(props.header, vals.get(0).replace("Bearer ", ""));
                        }
                    }

                    ClientHttpResponse resp = execution.execute(request, body);

                    if (props.anthropic) {
                        byte[] respBody = resp.getBody().readAllBytes();
                        byte[] rewritten = fromAnthropic(respBody);
                        return wrapResponse(rewritten, resp);
                    }

                    return resp;
                });

        return OpenAiApi.builder()
                .baseUrl(resolved.baseUrl())
                .apiKey(new SimpleApiKey(resolved.apiKey()))
                .headers(resolved.headers())
                .completionsPath(chatProperties.getCompletionsPath())
                .embeddingsPath(OpenAiEmbeddingProperties.DEFAULT_EMBEDDINGS_PATH)
                .restClientBuilder(restClient)
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                .build();
    }

    private byte[] toAnthropic(byte[] body) throws IOException {
        ObjectNode root = (ObjectNode) OM.readTree(body);

        // Move system messages to top-level, rewrite tool results
        JsonNode messagesNode = root.path("messages");
        if (messagesNode.isArray()) {
            ArrayNode newMessages = OM.createArrayNode();
            StringJoiner system = new StringJoiner("\n");

            StreamSupport.stream(messagesNode.spliterator(), false)
                    .forEach(msg -> {
                        switch (msg.path("role").asText()) {
                            case "system" -> system.add(msg.path("content").asText());
                            case "tool" -> {
                                ObjectNode converted = OM.createObjectNode();
                                converted.put("role", "user");
                                ArrayNode content = OM.createArrayNode();
                                ObjectNode toolResult = OM.createObjectNode();
                                toolResult.put("type", "tool_result");
                                toolResult.put("tool_use_id", msg.path("tool_call_id").asText());
                                toolResult.put("content", msg.path("content").asText());
                                content.add(toolResult);
                                converted.set("content", content);
                                newMessages.add(converted);
                            }
                            case "assistant" -> {
                                ObjectNode converted = (ObjectNode) msg.deepCopy();
                                JsonNode toolCallsNode = msg.path("tool_calls");
                                if (toolCallsNode.isArray()) {
                                    ArrayNode content = OM.createArrayNode();

                                    String text = msg.path("content").asText();
                                    if (!text.isBlank()) {
                                        ObjectNode textBlock = OM.createObjectNode();
                                        textBlock.put("type", "text");
                                        textBlock.put("text", text);
                                        content.add(textBlock);
                                    }

                                    toolCallsNode.forEach(tc -> {
                                        ObjectNode toolUse = OM.createObjectNode();
                                        toolUse.put("type", "tool_use");
                                        toolUse.put("id", tc.path("id").asText());
                                        toolUse.put("name", tc.path("function").path("name").asText());
                                        try {
                                            toolUse.set("input", OM.readTree(
                                                    tc.path("function").path("arguments").asText()));
                                        } catch (IOException e) {
                                            toolUse.set("input", OM.createObjectNode());
                                        }
                                        content.add(toolUse);
                                    });

                                    converted.set("content", content);
                                    converted.remove("tool_calls");
                                }
                                newMessages.add(converted);
                            }
                            default -> newMessages.add(msg);
                        }
                    });

            if (system.length() > 0) root.put("system", system.toString());
            root.set("messages", newMessages);
        }

        // Rewrite OpenAI tools → Anthropic format
        JsonNode toolsNode = root.path("tools");
        if (toolsNode.isArray()) {
            ArrayNode newTools = OM.createArrayNode();
            StreamSupport.stream(toolsNode.spliterator(), false)
                    .map(tool -> {
                        if (!"function".equals(tool.path("type").asText())) return tool;
                        JsonNode fn = tool.get("function");
                        ObjectNode t = OM.createObjectNode();
                        t.put("type", "custom");
                        t.put("name", fn.path("name").asText());
                        t.put("description", fn.path("description").asText());
                        t.set("input_schema", fn.path("parameters"));
                        return (JsonNode) t;
                    })
                    .forEach(newTools::add);
            root.set("tools", newTools);
        }

        return OM.writeValueAsBytes(root);
    }

    private byte[] fromAnthropic(byte[] body) throws IOException {
        ObjectNode root = (ObjectNode) OM.readTree(body);

        // Only rewrite if it's an Anthropic-style response
        if (!root.has("content")) return body;

        ObjectNode openAiResp = OM.createObjectNode();
        openAiResp.put("id", root.path("id").asText());
        openAiResp.put("object", "chat.completion");
        openAiResp.put("model", root.path("model").asText());

        String textContent = "";
        ArrayNode toolCalls = OM.createArrayNode();

        for (JsonNode content : root.path("content")) {
            switch (content.path("type").asText()) {
                case "text" -> textContent = content.path("text").asText();
                case "tool_use" -> {
                    ObjectNode toolCall = OM.createObjectNode();
                    toolCall.put("id", content.path("id").asText());
                    toolCall.put("type", "function");
                    ObjectNode fn = OM.createObjectNode();
                    fn.put("name", content.path("name").asText());
                    fn.put("arguments", OM.writeValueAsString(content.path("input")));
                    toolCall.set("function", fn);
                    toolCalls.add(toolCall);
                }
            }
        }

        // Build message
        ObjectNode message = OM.createObjectNode();
        message.put("role", "assistant");
        message.put("content", textContent);
        if (!toolCalls.isEmpty()) message.set("tool_calls", toolCalls);

        // Build choice
        ObjectNode choice = OM.createObjectNode();
        choice.put("index", 0);
        choice.put("finish_reason",
                "tool_use".equals(root.path("stop_reason").asText()) ? "tool_calls" : "stop");
        choice.set("message", message);

        // Build usage
        JsonNode usage = root.path("usage");
        ObjectNode openAiUsage = OM.createObjectNode();
        openAiUsage.put("prompt_tokens", usage.path("input_tokens").asInt());
        openAiUsage.put("completion_tokens", usage.path("output_tokens").asInt());
        openAiUsage.put("total_tokens", usage.path("input_tokens").asInt() + usage.path("output_tokens").asInt());

        openAiResp.set("choices", OM.createArrayNode().add(choice));
        openAiResp.set("usage", openAiUsage);

        return OM.writeValueAsBytes(openAiResp);
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
            else System.out.println("Bean " + beanName + " := " + bean);
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @ConfigurationProperties(prefix = "openai")
    @Data
    static class OpenAiProperties {
        private Map<String, Object> headers;
        private String header;
        private boolean anthropic;
    }

}
