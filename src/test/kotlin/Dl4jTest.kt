import com.google.common.collect.Lists
import org.deeplearning4j.util.ModelSerializer
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartUtilities
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.XYPlot
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import org.slf4j.LoggerFactory
import ru.tinkoff.predictor.util.CSVUtil
import ru.tinkoff.predictor.util.LSTMNetwork
import ru.tinkoff.predictor.util.StockDataSetIterator
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class Dl4jExpApplicationTest

private val log = LoggerFactory.getLogger(Dl4jExpApplicationTest::class.java)

private const val DATA_SIZE = 1_000

const val dataFileName = "Data.csv"
const val networkFileName = "NeuralNetwork"

fun main(args: Array<String>) {
    predictTest()
}

@Throws(IOException::class)
fun predictTest() {
    val dataFile = Dl4jExpApplicationTest::class.java.getResource("/$dataFileName").file
    val data = CSVUtil.getCandles(dataFile, DATA_SIZE)
    val iterator = StockDataSetIterator(data, 0.0)
    val neuralNetworkFile = Dl4jExpApplicationTest::class.java.getResource(networkFileName)
    val net = if (neuralNetworkFile != null && File(neuralNetworkFile.file).exists()) {
        try {
            ModelSerializer.restoreMultiLayerNetwork(neuralNetworkFile.file)
        } catch (ex: IOException) {
            log.error(ex.message)
            return
        }
    } else {
        LSTMNetwork.buildLstmNetworks(StockDataSetIterator(data, 1.0))
    }

    val testData = iterator.test
    val predicts = Lists.newArrayList<Double>()
    val actually = Lists.newArrayList<Double>()
    val indicators: Array<DoubleArray>? = Array(StockDataSetIterator.VECTOR_K + 1) { DoubleArray(testData.size) }

    var index = 0
    testData.forEach { indArrayDoublePair ->
        val output = net.rnnTimeStep(indArrayDoublePair.key)

        // indicators
        indicators!![0][index] = StockDataSetIterator.deNormalize(
                output.getDouble(0), iterator.mins[0], iterator.maxs[0]
        )
        indicators[1][index] = StockDataSetIterator.deNormalize(
                output.getDouble(1), iterator.mins[1], iterator.maxs[1]
        )
        indicators[2][index] = StockDataSetIterator.deNormalize(
                output.getDouble(2), iterator.mins[2], iterator.maxs[2]
        )
        indicators[3][index] = StockDataSetIterator.deNormalize(
                output.getDouble(3), iterator.mins[3], iterator.maxs[3]
        )
        indicators[4][index] = StockDataSetIterator.deNormalize(
                output.getDouble(4), iterator.mins[4], iterator.maxs[4]
        )
        indicators[5][index] = StockDataSetIterator.deNormalize(
                output.getDouble(5), iterator.mins[5], iterator.maxs[5]
        )
        index++

        // closes
        predicts.add(
            StockDataSetIterator.deNormalize(
                output.getDouble(6), iterator.closes[0], iterator.closes[1]
        ))

        // actually
        actually.add(StockDataSetIterator.deNormalize(indArrayDoublePair.value, iterator.closes[0], iterator.closes[1]))
    }

    plote(actually, predicts, indicators)
}

private fun plote(actuals: List<Double>, predicts: List<Double>, indicators: Array<DoubleArray>?) {
    val series = XYSeries("actually")
    val series2 = XYSeries("predict")

    var max = 0.0
    var min = 999.0
    for (i in predicts.indices) {
        val actual = actuals[i]
        if (actual > max) max = actual
        if (actual < min) min = actual

        val predict = predicts[i]
        if (predict > max) max = predict
        if (predict < min) min = predict

        series.add(i.toDouble(), actual)
        series2.add(i.toDouble(), predict)
    }

    val data = XYSeriesCollection()
    data.addSeries(series)
    data.addSeries(series2)

    for ((k, indicator) in indicators!!.withIndex()) {
        val seriesInd = XYSeries(k)
        var i = 0
        while (i < predicts.size) {
            seriesInd.add(i.toDouble(), indicator[i])
            i++
        }
        data.addSeries(seriesInd)
    }

    val chart = ChartFactory.createXYLineChart(
            "Symbol",
            "Ticks",
            "ClosePrice",
            data,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
    )

    val range = (chart.plot as XYPlot).rangeAxis as NumberAxis
    range.setRange(min, max)

    try {
        val out = FileOutputStream("out/Symbol_$DATA_SIZE.png")
        ChartUtilities.writeChartAsPNG(out, chart, 1024, 600)
    } catch (ex: IOException) {
        log.error(ex.message)
    }
}
