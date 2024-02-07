package ru.tinkoff.predictor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.tinkoff.predictor.service.IndicatorTrader;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class PredictorApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(PredictorApplication.class, args);
        context.getBean(IndicatorTrader.class).run();
    }
}
