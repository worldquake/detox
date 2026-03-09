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
    private Map<RequestMappingInfo, HandlerMethod> lastRemoved = new HashMap<>();
    private Map<RequestMappingInfo, HandlerMethod> lastRemovedController = new HashMap<>();
    private boolean alreadyInit;

    @SneakyThrows
    void initByPackage(String pck) {
        if (alreadyInit) return;
        initRemoved(pck);
        if (lastRemoved.isEmpty() && lastRemovedController.isEmpty()) {
            requestMappingHandlerMapping.afterPropertiesSet();
            controllerEndpointHandlerMapping.afterPropertiesSet();
            initRemoved(pck);
        }
    }

    @SneakyThrows
    private void initRemoved(String pck) {
        lastRemoved.clear();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod handlerMethod = e.getValue();
            Class<?> beanType = (Class<?>) handlerMethod.getClass().getMethod("getBeanType").invoke(handlerMethod);
            if (beanType.getName().startsWith(pck + ".")) {
                lastRemoved.put(info, handlerMethod);
            }
        }
        lastRemovedController.clear();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : controllerEndpointHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod handlerMethod = e.getValue();
            Class<?> beanType = (Class<?>) handlerMethod.getClass().getMethod("getBeanType").invoke(handlerMethod);
            if (beanType.getName().startsWith(pck + ".")) {
                lastRemovedController.put(info, handlerMethod);
            }
        }
        if (lastRemoved.isEmpty() && lastRemovedController.isEmpty()) {
            requestMappingHandlerMapping.afterPropertiesSet();
            controllerEndpointHandlerMapping.afterPropertiesSet();
        } else {
            alreadyInit = true;
            lastRemoved.clear();
            lastRemovedController.clear();
        }
    }

    @SneakyThrows
    public boolean remove(String pck) {
        if (!lastRemoved.isEmpty() || !lastRemovedController.isEmpty()) return false;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod handlerMethod = e.getValue();
            Class<?> beanType = (Class<?>) handlerMethod.getClass().getMethod("getBeanType").invoke(handlerMethod);
            if (beanType.getName().startsWith(pck + ".")) {
                requestMappingHandlerMapping.unregisterMapping(info);
                lastRemoved.put(info, handlerMethod);
            }
        }
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : controllerEndpointHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod handlerMethod = e.getValue();
            Class<?> beanType = (Class<?>) handlerMethod.getClass().getMethod("getBeanType").invoke(handlerMethod);
            if (beanType.getName().startsWith(pck + ".")) {
                controllerEndpointHandlerMapping.unregisterMapping(info);
                lastRemovedController.put(info, handlerMethod);
            }
        }
        return true;
    }

    @SneakyThrows
    public boolean register() {
        boolean result = false;
        if (!lastRemoved.isEmpty()) {
            for (var info : lastRemoved.entrySet()) {
                HandlerMethod m = info.getValue();
                requestMappingHandlerMapping.registerMapping(info.getKey(), m.getBean(), m.getMethod());
            }
            lastRemoved.clear();
            result = true;
        }
        if (!lastRemovedController.isEmpty()) {
            for (var info : lastRemovedController.entrySet()) {
                HandlerMethod m = info.getValue();
                controllerEndpointHandlerMapping.registerMapping(info.getKey(), m.getBean(), m.getMethod());
            }
            lastRemovedController.clear();
            result = true;
        }
        return result;
    }
}