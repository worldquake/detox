package hu.detox.szexpartnerek.ws.rest;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static hu.detox.spring.DetoxConfig.converter;

public class GenericRequestParamArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return converter().canConvert(String.class, parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String paramName = parameter.getParameterName();
        String value = webRequest.getParameter(paramName);
        if (value == null) {
            return null;
        }
        return converter().convert(value, parameter.getParameterType());
    }
}