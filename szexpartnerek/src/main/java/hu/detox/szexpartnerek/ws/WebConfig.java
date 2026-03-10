package hu.detox.szexpartnerek.ws;

import hu.detox.spring.JacksonConverter;
import hu.detox.szexpartnerek.ws.rest.GenericRequestParamArgumentResolver;
import hu.detox.utils.strings.StringUtils;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final static String PREFIX = "/szexpartnerek";

    public static String valueOf(FromItem fi) {
        if (fi == null) return null;
        String name = StringUtils.trimToNull(fi.toString());
        if (fi.getAlias() != null) name = fi.getAlias().getName();
        return name;
    }

    public static String valueOf(SelectItem<?> si) {
        if (si == null) return null;
        String name = StringUtils.trimToNull(si.toString());
        if (si.getAlias() != null) name = si.getAlias().getName();
        return name;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static" + PREFIX + "/assets/");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GenericRequestParamArgumentResolver());
    }

    @Bean
    public HttpMessageConverter<com.fasterxml.jackson.databind.JsonNode> jacksonConverter() {
        return new JacksonConverter();
    }
}
