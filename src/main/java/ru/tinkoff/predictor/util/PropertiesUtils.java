package ru.tinkoff.predictor.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;

public final class PropertiesUtils {

    public static Properties loadProperties() throws IOException {
        final Properties properties = new Properties();

        InputStream internalProperties = PropertiesUtils.class.getClassLoader().getResourceAsStream("application.properties");
        properties.load(Objects.requireNonNull(internalProperties));

        String externalPathProperties = System.getProperty("application.properties");
        if (null != externalPathProperties) {
            File propFile = new File(externalPathProperties);
            if (propFile.exists()) {
                InputStream externalProperties = Files.newInputStream(propFile.toPath());
                properties.load(Objects.requireNonNull(externalProperties));
            }
        }
        return properties;
    }
}
