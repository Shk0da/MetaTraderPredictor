package ru.tinkoff.predictor.service;


import org.springframework.web.client.RestTemplate;
import ru.tinkoff.predictor.config.TelegramNotifyConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.out;

public class TelegramNotifyService {

    public static final TelegramNotifyService telegramNotifyService = new TelegramNotifyService();

    private static final String TELEGRAM_SEND_MESSAGE_URL = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";

    private final Boolean enable;
    private final String botToken;
    private final String chatId;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ExecutorService queue = Executors.newSingleThreadExecutor();

    public TelegramNotifyService() {
        try {
            TelegramNotifyConfig telegramNotifyConfig = new TelegramNotifyConfig();
            this.enable = telegramNotifyConfig.getEnable();
            this.botToken = telegramNotifyConfig.getBotToken();
            this.chatId = telegramNotifyConfig.getChatId();
        } catch (Exception ex) {
            throw new RuntimeException("Failed instance TelegramNotifyService: " + ex.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (!enable) return;

        queue.execute(() -> {
            try {
                String text = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
                String uri = String.format(TELEGRAM_SEND_MESSAGE_URL, botToken, chatId, text);
                restTemplate.postForObject(uri, null, String.class);
            } catch (Exception ex) {
                out.println("Error: " + ex.getMessage());
            }
        });
    }
}
