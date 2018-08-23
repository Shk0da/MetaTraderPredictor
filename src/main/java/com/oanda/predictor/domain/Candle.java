package com.oanda.predictor.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.sql.Timestamp;
import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Candle {
    public Timestamp time;
    public String symbol;
    public int step;
    public double bid;
    public double ask;
    public double open;
    public double high;
    public double low;
    public double close;
    public int volume;

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
