package com.oanda.predictor.repository;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.oanda.predictor.domain.Candle;
import com.oanda.predictor.util.StockDataSetIterator;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class CandleRepository {

    @Getter
    @Value("${candle.repository.limit}")
    private Integer limit;

    private final Map<String, TreeMap<Timestamp, Candle>> candles = Maps.newHashMap();

    public List<Candle> getCandles(String key) {
        return Lists.newArrayList(this.candles.getOrDefault(key, Maps.newTreeMap()).values());
    }

    public List<Candle> getCandles(String symbol, int step) {
        return Lists.newArrayList(this.candles.getOrDefault(getKey(symbol, step), Maps.newTreeMap()).values());
    }

    @Synchronized
    public void clearAllCandles() {
        this.candles.clear();
    }

    @Synchronized
    public void clearCandles(String symbol, int step) {
        this.candles.put(getKey(symbol, step), Maps.newTreeMap());
    }

    @Synchronized
    public void addCandles(String symbol, int step, List<Candle> candles) {
        String key = getKey(symbol, step);
        List<Candle> current = getCandles(key);
        current.addAll(candles);
        this.candles.put(key, getMapFromList(current));
    }

    @Synchronized
    public void addCandle(Candle candle) {
        String key = getKey(candle.getSymbol(), candle.getStep());
        List<Candle> current = getCandles(key);
        current.add(candle);
        this.candles.put(key, getMapFromList(current));
    }

    @Synchronized
    public Candle getLastCandle(String symbol, int step) {
        List<Candle> current = getCandles(getKey(symbol, step));
        return current.isEmpty() ? null : current.get(current.size() - 1);
    }

    @Synchronized
    public List<Candle> getLastCandles(String symbol, int step, int size) {
        List<Candle> current = getCandles(getKey(symbol, step));

        if (current.isEmpty() || current.size() < StockDataSetIterator.VECTOR_SIZE) {
            return current;
        }

        if (current.size() > limit) {
            List<Candle> trimmedList = current.subList(current.size() - limit / 2, current.size());
            this.candles.put(getKey(symbol, step), getMapFromList(trimmedList));
        }

        if (size > current.size()) {
            size = current.size() - 1;
        }

        int fromIndex = current.size() - size - 1;
        if (fromIndex < 0) {
            return current;
        }

        return current.subList(fromIndex, current.size());
    }

    public Integer getSize(String symbol, int step) {
        return getCandles(getKey(symbol, step)).size();
    }

    private String getKey(String symbol, int step) {
        return symbol + step;
    }

    private TreeMap<Timestamp, Candle> getMapFromList(List<Candle> current) {
        return current.stream().collect(Collectors.toMap(Candle::getTime, item -> item, (a, b) -> b, TreeMap::new));
    }
}
