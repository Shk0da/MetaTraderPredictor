package com.oanda.predictor.actor;

import akka.actor.UntypedAbstractActor;
import com.oanda.predictor.domain.Candle;
import com.oanda.predictor.provider.ApplicationContextProvider;
import com.oanda.predictor.repository.CandleRepository;
import com.oanda.predictor.repository.PredictionRepository;
import com.oanda.predictor.util.LSTMNetwork;
import com.oanda.predictor.util.StockDataSetIterator;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
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
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Iterator;
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

    private volatile double closeMin;
    private volatile double closeMax;

    @Value("${predictor.sensitivity.trend}")
    private Double sensitivityTrend;

    @Value("${predictor.learn.interval}")
    private Integer learnInterval;

    @Value("${neuralnetwork.store.disk}")
    private Boolean storeDisk;

    private String locationToSave;

    private TaskScheduler taskScheduler = ApplicationContextProvider.getApplicationContext().getBean(TaskScheduler.class);

    public LearnActor(String instrument, Integer step) {
        this.instrument = instrument;
        this.step = step;
        this.locationToSave = "NeuralNetwork" + instrument + step;
    }

    @Override
    public void onReceive(Object message) {
        if (Messages.LEARN.equals(message)) {
            if (!status.equals(Status.TRAINED)) {
                taskScheduler.schedule(this::trainNetwork, new Date());
            }
        }

        if (Messages.PREDICT.equals(message)) {
            if (getNeuralNetwork() != null) {
                taskScheduler.schedule(this::predict, new Date());
            }
        }
    }

    private void predict() {
        Signal signal = Signal.NONE;
        List<Candle> last = candleRepository.getLastCandles(instrument, step, VECTOR_SIZE);

        // check vector
        if (last.size() < VECTOR_SIZE) return;

        // check new data
        double vectorClose = last.get(last.size() - 1).getClose();
        if (lastCandleClose > 0 && lastCandleClose == vectorClose) return;
        lastCandleClose = vectorClose;

        INDArray output;
        try {
            INDArray input = Nd4j.create(new int[]{1, VECTOR_SIZE}, 'f');
            for (int j = VECTOR_SIZE, k = 0; j > 0; j--, k++) {
                input.putScalar(new int[]{0, k}, normalize(last.get(k).getClose(), closeMin, closeMax));
            }
            output = neuralNetwork.rnnTimeStep(input);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            predictionRepository.addPredict(instrument, signal);
            return;
        }

        double closePrice = Precision.round(deNormalize(output.getDouble(0), closeMin, closeMax), 5);
        if (closePrice != Double.NaN && closePrice > 0 && closePrice != lastPredict) {
            if (lastPredict > 0) {
                double spread = last.get(0).getBid() - last.get(0).getAsk();
                boolean diffMoreSpread = Math.abs(closePrice - lastPredict) > spread;
                if (closePrice > lastPredict && diffMoreSpread && (closePrice / (lastPredict / 100) - 100) > sensitivityTrend) {
                    signal = Signal.UP;
                }

                if (closePrice < lastPredict && diffMoreSpread && (lastPredict / (closePrice / 100) - 100) > sensitivityTrend) {
                    signal = Signal.DOWN;
                }
            }

            lastPredict = closePrice;
        }

        predictionRepository.addPredict(instrument, signal);
    }

    private void trainNetwork() {
        if (lastLearn != null && (DateTime.now().getMillis() - lastLearn.getMillis()) < TimeUnit.MINUTES.toMillis(learnInterval)) {
            return;
        }

        List<Candle> candles = candleRepository.getLastCandles(instrument, step, candleRepository.getLimit());
        if (candles.size() < candleRepository.getLimit()) return;

        setStatus(Status.TRAINED);
        StockDataSetIterator iterator = new StockDataSetIterator(candles, 1);
        if (getNeuralNetwork() == null) {
            neuralNetwork = LSTMNetwork.buildLstmNetworks(iterator);
            closeMin = iterator.getCloseMin();
            closeMax = iterator.getCloseMax();
        } else {
            neuralNetwork.evaluateRegression(iterator);
        }

        if (storeDisk) {
            try {
                String filePath = locationToSave + "_" + closeMin + "_" + closeMax;
                ModelSerializer.writeModel(neuralNetwork, filePath, true);
                log.info("The model is saved to disk: {}", filePath);
            } catch (IOException ex) {
                log.error(ex.getMessage());
            }
        }

        lastLearn = DateTime.now();
        setStatus(Status.READY);
    }

    @Synchronized
    private MultiLayerNetwork getNeuralNetwork() {
        if (storeDisk && neuralNetwork == null && locationToSave != null) {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("."), locationToSave + "*")) {
                File fileNetwork = null;
                Iterator<Path> iterator = dirStream.iterator();
                if (iterator.hasNext()) {
                    fileNetwork = iterator.next().toFile();
                }

                if (fileNetwork != null) {
                    String fileName = fileNetwork.getName();
                    neuralNetwork = ModelSerializer.restoreMultiLayerNetwork(fileName);
                    int firstDelimiter = fileName.indexOf('_');
                    int secondDelimiter = fileName.lastIndexOf('_');
                    closeMin = Double.valueOf(fileName.substring(firstDelimiter + 1, secondDelimiter));
                    closeMax = Double.valueOf(fileName.substring(secondDelimiter + 1, fileName.length()));
                    log.info("The model is loaded from the disk: {}", fileName);
                }
            } catch (IOException ex) {
                log.error(ex.getMessage());
            }
        }

        return neuralNetwork;
    }
}
