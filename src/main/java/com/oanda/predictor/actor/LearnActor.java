package com.oanda.predictor.actor;

import akka.actor.UntypedAbstractActor;
import com.google.common.collect.Lists;
import com.oanda.predictor.domain.Candle;
import com.oanda.predictor.provider.ApplicationContextProvider;
import com.oanda.predictor.repository.CandleRepository;
import com.oanda.predictor.repository.PredictionRepository;
import com.oanda.predictor.util.CSVUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.oanda.predictor.repository.PredictionRepository.Signal;
import static com.oanda.predictor.util.StockDataSetIterator.deNormalize;
import static com.oanda.predictor.util.StockDataSetIterator.getVectorSize;
import static java.lang.Double.parseDouble;

@Slf4j
@Scope("prototype")
@Component("LearnActor")
public class LearnActor extends UntypedAbstractActor {

    public enum Status {NOTHING, TRAINED, READY}

    private final String instrument;
    private final Integer step;

    private volatile MultiLayerNetwork neuralNetwork;
    private volatile DateTime lastLearn = null;
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
    private final double[] maBlacks = new double[5];
    private final double[] maWhites = new double[5];

    @Value("${predictor.learn.interval}")
    private Integer learnInterval;

    @Value("${neuralnetwork.store.disk}")
    private Boolean storeDisk;

    private final String locationToSave;

    private final TaskScheduler taskScheduler = ApplicationContextProvider.getApplicationContext().getBean(TaskScheduler.class);

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

    @Synchronized
    private void predict() {
        Signal signal = Signal.NONE;
        List<Candle> last = candleRepository.getLastCandles(instrument, step, getVectorSize());

        // check vector
        if (last.size() < getVectorSize()) return;

        // check new data
        double vectorClose = last.get(last.size() - 1).getClose();
        if (lastCandleClose > 0 && lastCandleClose == vectorClose) return;
        lastCandleClose = vectorClose;

        INDArray output;
        StockDataSetIterator iterator = new StockDataSetIterator(last, 0.0);
        try {
            output = neuralNetwork.rnnTimeStep(iterator.getTest().get(iterator.getTest().size() - 1).getKey());
        } catch (Exception ex) {
            log.error("Predict {}{} failed: {}", instrument, step, ex.getMessage());
            predictionRepository.addPredict(instrument, signal);
            return;
        }

        double maBlack = Precision.round(deNormalize(output.getDouble(3), iterator.getMins()[3], iterator.getMaxs()[3]), 5);
        double maWhite = Precision.round(deNormalize(output.getDouble(4), iterator.getMins()[4], iterator.getMaxs()[4]), 5);
        double ema = Precision.round(deNormalize(output.getDouble(5), iterator.getMins()[5], iterator.getMaxs()[5]), 5);
        double closePrice = Precision.round(deNormalize(output.getDouble(6), closeMin, closeMax), 5);
        if (!Double.isNaN(closePrice) && closePrice > 0 && closePrice != lastPredict) {
            double[] mas = Objects.requireNonNull(iterator.getIndicators())[4];
            double[] emas = Objects.requireNonNull(iterator.getIndicators())[5];
            if (emas.length >= 5 && mas.length >= 3) {
                boolean maDown = mas[emas.length - 1] < mas[emas.length - 2] && mas[emas.length - 2] < mas[emas.length - 3];
                boolean emaDown = emas[emas.length - 1] < emas[emas.length - 2] && emas[emas.length - 3] < emas[emas.length - 5];
                if (emaDown && maDown && closePrice < maBlack && closePrice <= maWhite && closePrice < ema) {
                    signal = Signal.DOWN;
                }

                boolean maUp = mas[emas.length - 1] > mas[emas.length - 2] && mas[emas.length - 2] > mas[emas.length - 3];
                boolean emaUp = emas[emas.length - 1] > emas[emas.length - 2] && emas[emas.length - 3] > emas[emas.length - 5];
                if (emaUp && maUp && closePrice > maBlack && closePrice >= maWhite && closePrice > ema) {
                    signal = Signal.UP;
                }
            }

            signal = getMaSignal(signal, maWhite, maWhites);
            signal = getMaSignal(signal, maBlack, maBlacks);

            lastPredict = closePrice;
        }

        predictionRepository.addPredict(instrument, signal);
    }

    private Signal getMaSignal(final Signal signal, final double ma, double[] mas) {
        mas[0] = mas[1];
        mas[1] = mas[2];
        mas[2] = mas[3];
        mas[3] = mas[4];
        mas[4] = ma;
        if (mas[0] > 0) {
            Signal maWhiteSignal = Signal.valueOf(signal.name());
            if (mas[0] < mas[1] && mas[1] < mas[2] && mas[2] > mas[3] && mas[3] > mas[4]) {
                maWhiteSignal = Signal.DOWN;
            }
            if (mas[0] > mas[1] && mas[1] > mas[2] && mas[2] < mas[3] && mas[3] < mas[4]) {
                maWhiteSignal = Signal.UP;
            }
            return (signal == Signal.UP && maWhiteSignal == Signal.UP)
                    ? Signal.UP
                    : (signal == Signal.DOWN && maWhiteSignal == Signal.DOWN)
                    ? Signal.DOWN
                    : Signal.NONE;
        }
        return signal;
    }

    private void trainNetwork() {
        if (lastLearn != null && (DateTime.now().getMillis() - lastLearn.getMillis()) < TimeUnit.MINUTES.toMillis(learnInterval)) {
            return;
        }

        List<Candle> candles = candleRepository.getLastCandles(instrument, step, candleRepository.getLimit());
        if (candles.size() < candleRepository.getLimit()) return;

        try {
            setStatus(Status.TRAINED);
            log.info("Start train: {}_{}", instrument, step);
            StockDataSetIterator iterator = new StockDataSetIterator(candles, 1.0);
            neuralNetwork = LSTMNetwork.buildLstmNetworks(iterator);
            closeMin = iterator.getCloses()[0];
            closeMax = iterator.getCloses()[1];

            if (storeDisk && neuralNetwork != null) {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("."), locationToSave + "_*")) {
                    dirStream.iterator().forEachRemaining(path -> path.toFile().delete());
                    String filePath = locationToSave + "_" + closeMin + "_" + closeMax;
                    ModelSerializer.writeModel(neuralNetwork, filePath, true);
                    log.info("The model is saved to disk: {}", filePath);
                    CSVUtil.saveCandles(candles, filePath + "Data");
                    log.info("The data is saved to disk also");
                    lastLearn = DateTime.now();
                    setStatus(Status.READY);
                } catch (IOException ex) {
                    log.error("Failed save to disk {}{}: {}", instrument, step, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed create network {}{}: {}", instrument, step, ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Synchronized
    private MultiLayerNetwork getNeuralNetwork() {
        if (storeDisk && neuralNetwork == null) {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("."), locationToSave + "_*")) {
                List<Path> paths = Lists.newArrayList(dirStream.iterator())
                        .stream()
                        .filter(path -> !path.toString().contains(".csv"))
                        .sorted(Comparator.comparing(o -> o.toFile().lastModified()))
                        .collect(Collectors.toList());

                if (!paths.isEmpty()) {
                    String fileName = paths.get(paths.size() - 1).toFile().getName();
                    neuralNetwork = ModelSerializer.restoreMultiLayerNetwork(fileName);
                    int firstDelimiter = fileName.indexOf('_');
                    int secondDelimiter = fileName.lastIndexOf('_');
                    closeMin = parseDouble(fileName.substring(firstDelimiter + 1, secondDelimiter));
                    closeMax = parseDouble(fileName.substring(secondDelimiter + 1));
                    log.info("The model is loaded from the disk: {}", fileName);
                    lastLearn = DateTime.now();
                }
            } catch (IOException ex) {
                log.error(ex.getMessage());
            }
        }

        return neuralNetwork;
    }
}
