import com.google.common.collect.Lists;
import com.oanda.predictor.domain.Candle;
import com.oanda.predictor.util.LSTMNetwork;
import com.oanda.predictor.util.StockDataSetIterator;
import com.opencsv.CSVReader;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class Dl4jTest {

    public static void main(String[] args) {
        StockDataSetIterator iterator = new StockDataSetIterator(getData(), 0.7);
        MultiLayerNetwork net = LSTMNetwork.buildLstmNetworks(iterator);

        List<Pair<INDArray, Double>> testData = iterator.getTest();
        List<Double> predicts = Lists.newArrayList();
        List<Double> actuals = Lists.newArrayList();

        testData.forEach(indArrayDoublePair -> {
            INDArray output = net.rnnTimeStep(indArrayDoublePair.getKey());
            predicts.add(StockDataSetIterator.deNormalize(
                    output.getDouble(0), iterator.getCloseMin(), iterator.getCloseMax()
            ));
            actuals.add(indArrayDoublePair.getValue());
        });

        plote(actuals, predicts);
    }

    private static void plote(List<Double> actuals, List<Double> predicts) {
        final XYSeries series = new XYSeries("USD/SEK");
        final XYSeries series2 = new XYSeries("USD/SEK PREDICT");

        double max = 0;
        double min = 999;
        for (int i = 0; i < actuals.size(); i++) {
            if (actuals.get(i) > max) max = actuals.get(i);
            if (actuals.get(i) < min) min = actuals.get(i);
            if (predicts.get(i) > max) max = predicts.get(i);
            if (predicts.get(i) < min) min = predicts.get(i);

            series.add(i, actuals.get(i));
            series2.add(i, predicts.get(i));
        }

        final XYSeriesCollection data = new XYSeriesCollection();
        data.addSeries(series);
        data.addSeries(series2);

        final JFreeChart chart = ChartFactory.createXYLineChart(
                "USD/SEK",
                "Tick M5",
                "ClosePrice",
                data,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        NumberAxis range = (NumberAxis) ((XYPlot) chart.getPlot()).getRangeAxis();
        range.setRange(min, max);

        final ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(1024, 600));

        ApplicationFrame appFrane = new ApplicationFrame("USD/SEK");
        appFrane.setContentPane(chartPanel);
        appFrane.pack();
        RefineryUtilities.centerFrameOnScreen(appFrane);
        appFrane.setVisible(true);
    }

    private static List<Candle> getData() {
        List<Candle> data = Lists.newArrayList();
        String csvFile = Dl4jTest.class.getResource("data.csv").getFile();
        try {
            CSVReader reader = new CSVReader(new FileReader(csvFile));
            String[] line;
            while ((line = reader.readNext()) != null) {
                Candle candle = new Candle();
                candle.setOpen(Double.valueOf(line[1]));
                candle.setHigh(Double.valueOf(line[2]));
                candle.setLow(Double.valueOf(line[3]));
                candle.setClose(Double.valueOf(line[4]));
                candle.setVolume(Double.valueOf(line[5]).intValue());
                data.add(candle);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        return data.subList(0, 1000);
    }
}
