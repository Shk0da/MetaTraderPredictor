package ru.tinkoff.predictor.config;

import org.springframework.stereotype.Component;
import ru.tinkoff.predictor.util.PropertiesUtils;

import java.util.Properties;

@Component
public class MainConfig {

    private final boolean isSandbox;
    private final String tcsApiKey;

    public MainConfig() throws Exception {
        final Properties properties = PropertiesUtils.loadProperties();
        this.isSandbox = Boolean.parseBoolean(properties.getProperty("tcs.isSandbox", "false"));
        this.tcsApiKey = properties.getProperty("tcs.apiKey");
    }

    public boolean isSandbox() {
        return isSandbox;
    }

    public String getTcsApiKey() {
        return tcsApiKey;
    }
}
