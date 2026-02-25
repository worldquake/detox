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
    private final RequestMappingHandlerMapping controllerEndpointHandlerMapping;
    private Map<RequestMappingInfo, HandlerMethod> lastRemoved;
    private boolean alreadyInit;

    @SneakyThrows
    void initByPackage(String pck) {
        if (alreadyInit) return;
        initRemoved(pck);
        if (lastRemoved.isEmpty()) {
            requestMappingHandlerMapping.afterPropertiesSet();
            initRemoved(pck);
        }
    }

    @SneakyThrows
    private void initRemoved(String pck) {
        lastRemoved = new HashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod handlerMethod = e.getValue();
            Class<?> beanType = (Class<?>) handlerMethod.getClass().getMethod("getBeanType").invoke(handlerMethod);
            if (beanType.getName().startsWith(pck + ".")) {
                lastRemoved.put(info, handlerMethod);
            }
        }
        if (lastRemoved.isEmpty())
            requestMappingHandlerMapping.afterPropertiesSet();
        else {
            alreadyInit = true; // If ever this happens it means the classes are loaded
            lastRemoved.clear();
        }
    }

    @SneakyThrows
    public boolean remove(String pck) {
        if (!lastRemoved.isEmpty()) return false;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod handlerMethod = e.getValue();
            Class<?> beanType = (Class<?>) handlerMethod.getClass().getMethod("getBeanType").invoke(handlerMethod);
            if (beanType.getName().startsWith(pck + ".")) {
                requestMappingHandlerMapping.unregisterMapping(info);
                lastRemoved.put(info, handlerMethod);
            }
        }
        return true;
    }

    @SneakyThrows
    public boolean register() {
        if (lastRemoved.isEmpty()) return false;
        for (var info : lastRemoved.entrySet()) {
            HandlerMethod m = info.getValue();
            requestMappingHandlerMapping.registerMapping(info.getKey(), m.getBean(), m.getMethod());
        }
        lastRemoved.clear();
        return true;
    }
}