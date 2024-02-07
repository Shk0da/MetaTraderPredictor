package ru.tinkoff.predictor.service;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.tinkoff.predictor.actor.LearnActor;
import ru.tinkoff.predictor.actor.Messages;
import ru.tinkoff.predictor.actor.SpringDIActor;
import ru.tinkoff.predictor.domain.Candle;
import ru.tinkoff.predictor.repository.CandleRepository;
import ru.tinkoff.predictor.repository.PredictionRepository;

import java.util.Map;

@Service
public class LearnService {

    public static Logger log = LoggerFactory.getLogger(LearnActor.class);

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private PredictionRepository predictionRepository;

    private final ActorSystem actorSystem = ActorSystem.create("LearnSystem");
    private final Map<String, ActorRef> actors = Maps.newConcurrentMap();

    @Async
    public void addCandle(Candle candle) {
        candleRepository.addCandle(candle);

        ActorRef actor = actors.getOrDefault(candle.getKey(), null);
        if (actor == null) {
            actor = actorSystem.actorOf(Props.create(SpringDIActor.class, LearnActor.class, candle.getSymbol(), candle.getStep()), "LearnActor_" + candle.getSymbol() + "_" + candle.getStep());
            actors.put(candle.getKey(), actor);
        }

        if (candle.getAsk() > 0 && candle.getBid() > 0) {
            actor.tell(Messages.LEARN, actorSystem.guardian());
        }

        log.debug("Candle list size {} {}: added {}, total: {}", candle.getSymbol(), candle.getStep(), 1, candleRepository.getSize(candle.getSymbol(), candle.getStep()));
    }

    public String getPredict(String symbol, int step) {
        ActorRef actor = actors.getOrDefault(symbol + step, null);
        if (actor != null) {
            actor.tell(Messages.PREDICT, actorSystem.guardian());
        }
        return predictionRepository.getPredict(symbol);
    }
}
