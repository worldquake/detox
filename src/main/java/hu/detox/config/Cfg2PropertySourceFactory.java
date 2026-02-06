package hu.detox.config;

import hu.detox.io.CharIOHelper;
import org.apache.commons.configuration2.ConfigurationConverter;
import org.jspecify.annotations.NonNull;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.util.Properties;

public class Cfg2PropertySourceFactory implements PropertySourceFactory {

    @Override
    public @NonNull PropertySource<?> createPropertySource(String name, @NonNull EncodedResource encodedResource) {
        try (CharIOHelper cio = CharIOHelper.attempt(encodedResource, name)) {
            Properties properties = ConfigurationConverter.getProperties(ConfigReader.INSTANCE.toCfg(null, cio));
            return new PropertiesPropertySource(cio.getName(), properties);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load YAML properties from " + encodedResource.getResource().getFilename(), e);
        }
    }
}