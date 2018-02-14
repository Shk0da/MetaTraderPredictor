package com.oanda.predictor.controller;

import com.oanda.predictor.domain.Candle;
import com.oanda.predictor.service.LearnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private LearnService learnService;

    @PostMapping("/add-candle")
    public void addCandle(@RequestBody Candle candle) {
        learnService.addCandle(candle);
    }

    @GetMapping(value = "/prediction", params = {"symbol", "step"})
    public ResponseEntity<String> prediction(String symbol, Integer step) {
        return new ResponseEntity<>(learnService.getPredict(symbol, step), HttpStatus.OK);
    }
}
