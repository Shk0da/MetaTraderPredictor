package ru.tinkoff.predictor.service;

import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.predictor.config.MainConfig;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TCSService {

    private final InvestApi investApi;

    public TCSService(MainConfig mainConfig) {
        this.investApi = mainConfig.isSandbox()
                ? InvestApi.createSandbox(mainConfig.getTcsApiKey())
                : InvestApi.create(mainConfig.getTcsApiKey());
    }

    public List<Share> getMoexShares() {
        return investApi.getInstrumentsService().getTradableSharesSync()
                .stream()
                .filter(it -> it.getCurrency().equals("rub"))
                .collect(Collectors.toList());
    }

    public List<HistoricCandle> getCandles(String figi, OffsetDateTime start, OffsetDateTime end, CandleInterval interval) {
        return investApi.getMarketDataService().getCandlesSync(figi, start.toInstant(), end.toInstant(), interval);
    }
}
