package ru.tinkoff.predictor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.predictor.domain.Candle;
import ru.tinkoff.predictor.provider.ApplicationContextProvider;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.System.out;
import static java.time.OffsetDateTime.now;

@Service
public class IndicatorTrader {

    @Autowired
    private TCSService tcsService;

    @Autowired
    private LearnService learnService;

    private final TaskScheduler taskScheduler = ApplicationContextProvider.getApplicationContext().getBean(TaskScheduler.class);

    @Value("#{'${predictor.tickers}'.split(',')}")
    private Set<String> tickerNames;

    @PostConstruct
    public void run() {
        List<Share> stocks = tcsService.getMoexShares()
                .stream()
                .filter(it -> tickerNames.contains(it.getTicker()))
                .collect(Collectors.toList());
        init(stocks);

        taskScheduler.scheduleAtFixedRate(() -> {
            stocks.forEach(stock -> {
                sleep(300, 450);
                List<HistoricCandle> m60candles = tcsService.getCandles(
                        stock.getFigi(),
                        now().minusHours(1),
                        now(),
                        CandleInterval.CANDLE_INTERVAL_HOUR
                );
                m60candles.forEach(it -> addCandle(stock, it));
            });
            stocks.forEach(stock -> {
                sleep(300, 450);
                String predict = learnService.getPredict(stock.getTicker(), 60);
                if (!predict.equals("NONE")) {
                    TelegramNotifyService.telegramNotifyService.sendMessage(stock.getTicker() + ": " + predict);
                }
            });
        }, 60 * 60 * 1000);
    }

    private void init(List<Share> stocks) {
        stocks.forEach(stock -> {
            sleep(300, 450);
            try {
                out.println("Start: " + stock.getName());
                List<HistoricCandle> m60candles = new ArrayList<>();
                for (int i = 7 * 25; i > 7; i = i - 7) {
                    m60candles.addAll(
                            tcsService.getCandles(
                                    stock.getFigi(),
                                    now().minusDays(i),
                                    now().minusDays(i - 7),
                                    CandleInterval.CANDLE_INTERVAL_HOUR
                            )
                    );
                    sleep(300, 450);
                }

                m60candles.addAll(
                        tcsService.getCandles(
                                stock.getFigi(),
                                now().minusDays(7),
                                now(),
                                CandleInterval.CANDLE_INTERVAL_HOUR
                        )
                );

                m60candles.forEach(it -> addCandle(stock, it));
                sleep(5 * 60 * 1000, 6 * 60 * 1000);
            } catch (Exception ex) {
                out.println(stock.getName() + " Error: " + ex.getMessage());
            }
        });
    }

    private void addCandle(Share stock, HistoricCandle it) {
        Candle candle = new Candle();
        candle.time = new Timestamp(System.currentTimeMillis());
        candle.symbol = stock.getTicker();
        candle.step = 60;
        candle.bid = toDouble(it.getClose());
        candle.ask = toDouble(it.getClose());
        candle.open = toDouble(it.getOpen());
        candle.high = toDouble(it.getHigh());
        candle.low = toDouble(it.getLow());
        candle.close = toDouble(it.getClose());
        candle.volume = (int) it.getVolume();
        learnService.addCandle(candle);
    }

    private static void sleep(int start, int end) {
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(start, end));
        } catch (InterruptedException ex) {
            out.println("Error: " + ex.getMessage());
        }
    }

    public static double toDouble(Quotation quotation) {
        return toDouble(quotation.getUnits(), quotation.getNano());
    }

    private static double toDouble(long units, int nano) {
        return units + Double.parseDouble("0." + nano);
    }
}
