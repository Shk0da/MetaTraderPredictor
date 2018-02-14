package com.oanda.predictor.repository;

import com.google.common.collect.Maps;
import lombok.Synchronized;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class PredictionRepository {

    public enum Signal {UP, DOWN, NONE}

    private final Map<String, Signal> predicts = Maps.newConcurrentMap();

    public String getPredict(String symbol) {
        return predicts.getOrDefault(symbol, Signal.NONE).name();
    }

    @Synchronized
    public void addPredict(String symbol, Signal predict) {
        predicts.put(symbol, predict);
    }
}
