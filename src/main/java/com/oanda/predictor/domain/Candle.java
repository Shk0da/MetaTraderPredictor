package com.oanda.predictor.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.sql.Timestamp;

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
}
