package ru.tinkoff.predictor.util;

import com.google.common.collect.Lists;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;
import ru.tinkoff.predictor.actor.LearnActor;
import ru.tinkoff.predictor.domain.Candle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static java.lang.Double.parseDouble;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CSVUtil {

    public static Logger log = LoggerFactory.getLogger(LearnActor.class);

    public static void saveCandles(List<Candle> candles, String fileName) throws IOException {
        if (candles.isEmpty()) return;

        String separator = ",";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSSZ");
        StringBuilder stringBuilder = new StringBuilder();
        candles.forEach(candle -> {
            stringBuilder.append(simpleDateFormat.format(Date.from(candle.getTime().toInstant())));
            stringBuilder.append(separator);
            stringBuilder.append(candle.getOpen());
            stringBuilder.append(separator);
            stringBuilder.append(candle.getHigh());
            stringBuilder.append(separator);
            stringBuilder.append(candle.getLow());
            stringBuilder.append(separator);
            stringBuilder.append(candle.getClose());
            stringBuilder.append(separator);
            stringBuilder.append(candle.getVolume());
            stringBuilder.append("\n");
        });

        File csvDataFile = new File(fileName + ".csv");
        if (csvDataFile.exists() || !csvDataFile.exists() && csvDataFile.createNewFile()) {
            FileCopyUtils.copy(
                    stringBuilder.toString(),
                    new OutputStreamWriter(new FileOutputStream(csvDataFile), UTF_8)
            );
        } else {
            log.error("Something wrong. File {} not save.", csvDataFile.getName());
        }
    }

    public static List<Candle> getCandles(String fileName, int size) throws IOException {
        File csvDataFile = new File(fileName);
        if (!csvDataFile.exists()) return Lists.newArrayList();

        List<Candle> data = Lists.newArrayList();
        CSVReader reader = new CSVReader(new FileReader(csvDataFile));
        String[] line;
        while ((line = reader.readNext()) != null) {
            Candle candle = new Candle();
            candle.setOpen(parseDouble(line[1]));
            candle.setHigh(parseDouble(line[2]));
            candle.setLow(parseDouble(line[3]));
            candle.setClose(parseDouble(line[4]));
            candle.setVolume(Double.valueOf(line[5]).intValue());
            data.add(candle);
        }

        return data.subList(0, min(data.size(), size));
    }
}
