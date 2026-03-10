package hu.detox.spring;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.parsers.JSonUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.IOException;

public class JacksonConverter extends AbstractHttpMessageConverter<JsonNode> {
    public JacksonConverter() {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
    }

    @Override
    protected boolean supports(@NonNull Class<?> clazz) {
        return JsonNode.class.isAssignableFrom(clazz);
    }

    @Override
    protected JsonNode readInternal(@NonNull Class<? extends JsonNode> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return JSonUtils.OM.readTree(inputMessage.getBody());
    }

    @Override
    protected void writeInternal(JsonNode jsonNode, HttpOutputMessage outputMessage) throws IOException {
        outputMessage.getBody().write(JSonUtils.OM.writeValueAsBytes(jsonNode));
    }
}
