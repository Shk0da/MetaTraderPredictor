package com.oanda.predictor.actor;

import akka.actor.UntypedAbstractActor;
import com.oanda.predictor.domain.Candle;
import com.oanda.predictor.repository.CandleRepository;
import com.oanda.predictor.repository.PredictionRepository;
import com.oanda.predictor.util.LSTMNetwork;
import com.oanda.predictor.util.StockDataSetIterator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Precision;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.joda.time.DateTime;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.oanda.predictor.repository.PredictionRepository.Signal;
import static com.oanda.predictor.util.StockDataSetIterator.*;

@Slf4j
@Scope("prototype")
@Component("LearnActor")
public class LearnActor extends UntypedAbstractActor {

    public enum Status {NOTHING, TRAINED, READY}

    private final String instrument;
    private final Integer step;

    private volatile MultiLayerNetwork neuralNetwork;
    private volatile DateTime lastLearn;
    private volatile Double lastPredict = 0D;
    private volatile Double lastCandleClose = 0D;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private PredictionRepository predictionRepository;

    @Getter
    @Setter
    public volatile Status status = Status.NOTHING;

    private double closeMin = Double.MAX_VALUE;
    private double closeMax = Double.MIN_VALUE;

    @Value("${predictor.sensitivity.trend}")
    private Double sensitivityTrend;

    @Value("${predictor.learn.interval}")
    private Integer learnInterval;

    @Value("${neuralnetwork.store.disk}")
    private Boolean storeDisk;

    private String locationToSave;

    public LearnActor(String instrument, Integer step) {
        this.instrument = instrument;
        this.step = step;
        this.locationToSave = "NeuralNetwork" + instrument + step;
    }

    @Override
    public void onReceive(Object message) {
        if (Messages.WORK.equals(message) && storeDisk && neuralNetwork == null) {
            log.info("Load model...");
            try {
                Path path = Paths.get(new URI(locationToSave));
                if (path != null && Files.exists(path)) {
                    neuralNetwork = ModelSerializer.restoreMultiLayerNetwork(locationToSave);
                }
            } catch (URISyntaxException | IOException ex) {
                log.error(ex.getMessage());
            }
        }

        if (Messages.WORK.equals(message) && neuralNetwork != null) {
            Signal signal = Signal.NONE;
            List<Candle> last = candleRepository.getLastCandles(instrument, step, VECTOR_SIZE);

            // check vector
            if (last.size() < VECTOR_SIZE) return;

            // check new data
            double vectorClose = last.get(4).getClose();
            if (lastCandleClose > 0 && lastCandleClose == vectorClose) return;
            lastCandleClose = vectorClose;

            INDArray input = Nd4j.create(new int[]{1, VECTOR_SIZE}, 'f');
            input.putScalar(new int[]{0, 0}, normalize(last.get(0).getClose(), closeMin, closeMax));
            input.putScalar(new int[]{0, 1}, normalize(last.get(1).getClose(), closeMin, closeMax));
            input.putScalar(new int[]{0, 2}, normalize(last.get(2).getClose(), closeMin, closeMax));
            input.putScalar(new int[]{0, 3}, normalize(last.get(3).getClose(), closeMin, closeMax));
            input.putScalar(new int[]{0, 4}, normalize(last.get(4).getClose(), closeMin, closeMax));

            INDArray output = neuralNetwork.rnnTimeStep(input);
            double closePrice = Precision.round(deNormalize(output.getDouble(0), closeMin, closeMax), 5);
            if (closePrice != Double.NaN && closePrice > 0 && closePrice != lastPredict) {
                if (lastPredict > 0) {
                    if (closePrice > lastPredict && (closePrice / (lastPredict / 100) - 100) > sensitivityTrend) {
                        signal = Signal.UP;
                    }

                    if (closePrice < lastPredict && (lastPredict / (closePrice / 100) - 100) > sensitivityTrend) {
                        signal = Signal.DOWN;
                    }
                }

                lastPredict = closePrice;
            }

            predictionRepository.addPredict(instrument, signal);
        }

        if (Messages.LEARN.equals(message)) {
            if (!status.equals(Status.TRAINED)) {
                trainNetwork();
            }
        }
    }

    @Async
    private void trainNetwork() {
        if (lastLearn != null && (DateTime.now().getMillis() - lastLearn.getMillis()) < TimeUnit.MINUTES.toMillis(learnInterval)) {
            return;
        }

        List<Candle> candles = candleRepository.getLastCandles(instrument, step, candleRepository.getLimit());
        setStatus(Status.TRAINED);
        StockDataSetIterator iterator = new StockDataSetIterator(candles, 256, 1);
        closeMin = iterator.getCloseMin();
        closeMax = iterator.getCloseMax();
        if (neuralNetwork == null) {
            neuralNetwork = LSTMNetwork.buildLstmNetworks(iterator);
        } else {
            neuralNetwork.evaluate(iterator);
        }

        if (storeDisk) {
            try {
                log.info("Saving model...");
                ModelSerializer.writeModel(neuralNetwork, locationToSave, true);
            } catch (IOException ex) {
                log.error(ex.getMessage());
            }
        }

        lastLearn = DateTime.now();
        setStatus(Status.READY);
    }
}
