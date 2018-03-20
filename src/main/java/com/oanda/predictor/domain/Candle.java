package com.oanda.predictor.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.sql.Timestamp;
import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Candle {
    private Timestamp time;
    private String symbol;
    private int step;
    private double bid;
    private double ask;
    private double open;
    private double high;
    private double low;
    private double close;
    private int volume;

    public String getKey() {
        return symbol + step;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Candle candle = (Candle) o;
        return step == candle.step &&
                Objects.equals(time, candle.time) &&
                Objects.equals(symbol, candle.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), time, symbol, step);
    }
}
