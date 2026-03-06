package hu.detox.szexpartnerek.ws.converters;

import hu.detox.szexpartnerek.ws.rest.DataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParingConverter implements Converter<String, DataRepository.PagingParam> {
    @Override
    public DataRepository.PagingParam convert(String s) {
        return DataRepository.PagingParam.valueOf(s);
    }
}
