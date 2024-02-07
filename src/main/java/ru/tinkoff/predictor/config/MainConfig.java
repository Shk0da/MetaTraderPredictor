package ru.tinkoff.predictor.config;

import org.springframework.stereotype.Component;
import ru.tinkoff.predictor.util.PropertiesUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Properties;

@Component
public class MainConfig {

    public static final DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    public static final DateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    public static final DateFormat dateFormatUs = new SimpleDateFormat("yyyy-MM-dd");

    private final boolean isTestMode;
    private final boolean isSandbox;

    private String tcsAccountId;
    private final String tcsApiKey;

    public MainConfig() throws Exception {
        final Properties properties = PropertiesUtils.loadProperties();
        this.isTestMode = Boolean.parseBoolean(properties.getProperty("tcs.testMode", "false"));
        this.isSandbox = Boolean.parseBoolean(properties.getProperty("tcs.isSandbox", "false"));
        this.tcsAccountId = properties.getProperty("tcs.accountId");
        this.tcsApiKey = properties.getProperty("tcs.apiKey");
    }

    public boolean isTestMode() {
        return isTestMode;
    }

    public boolean isSandbox() {
        return isSandbox;
    }

    public String getTcsAccountId() {
        return tcsAccountId;
    }

    public MainConfig withAccountId(String accountId) {
        this.tcsAccountId = accountId;
        return this;
    }

    public String getTcsApiKey() {
        return tcsApiKey;
    }
}
