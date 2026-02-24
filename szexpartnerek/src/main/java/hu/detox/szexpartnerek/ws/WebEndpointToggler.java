package hu.detox.szexpartnerek.ws;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashMap;
import java.util.Map;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class WebEndpointToggler {
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private Map<RequestMappingInfo, HandlerMethod> lastRemoved;

    void init() {
        requestMappingHandlerMapping.afterPropertiesSet();
    }

    @SneakyThrows
    public void remove(String pck) {
        lastRemoved = new HashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod handlerMethod = e.getValue();
            Class<?> beanType = (Class<?>) handlerMethod.getClass().getMethod("getBeanType").invoke(handlerMethod);
            if (beanType.getName().startsWith(pck + ".")) {
                requestMappingHandlerMapping.unregisterMapping(info);
                lastRemoved.put(info, handlerMethod);
            }
        }
    }

    @SneakyThrows
    public void register() {
        if (lastRemoved == null) return;
        for (var info : lastRemoved.entrySet()) {
            HandlerMethod m = info.getValue();
            requestMappingHandlerMapping.registerMapping(info.getKey(), m.getBean(), m.getMethod());
        }
        lastRemoved.clear();
    }
}