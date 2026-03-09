package hu.detox.spring;

import org.jspecify.annotations.NonNull;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class ConditionalOnNoApp implements TypeFilter {
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Annotation {
    }

    @Override
    public boolean match(@NonNull MetadataReader mdr, @NonNull MetadataReaderFactory mdrf) {
        boolean hasAnnotation = mdr.getAnnotationMetadata().hasAnnotation(Annotation.class.getName());
        return DetoxConfig.ctx() == null || !hasAnnotation;
    }
}