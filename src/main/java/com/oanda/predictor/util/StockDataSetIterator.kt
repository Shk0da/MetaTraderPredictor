package com.oanda.predictor.util

import com.oanda.predictor.domain.Candle
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.dataset.api.DataSetPreProcessor
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.primitives.Pair
import org.slf4j.LoggerFactory
import java.util.*

class StockDataSetIterator(stockDataList: List<Candle>, splitRatio: Double = 1.0) : DataSetIterator {

    private val log = LoggerFactory.getLogger(StockDataSetIterator::class.java)

    private val exampleStartOffsets = LinkedList<Int>()
    private var dataSetPreProcessor: DataSetPreProcessor? = null

    private val train: List<Candle>
    val test: List<Pair<INDArray, Double>>

    var indicators: Array<DoubleArray>? = null
    val maxs: DoubleArray = doubleArrayOf(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
    val mins: DoubleArray = doubleArrayOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
    val closes: DoubleArray = doubleArrayOf(Double.MAX_VALUE, Double.MIN_VALUE)

    companion object {
        val CHUNK_SHIFT = 91
        @JvmField
        val VECTOR_K = 5
        @JvmField
        val VECTOR_SIZE_1 = 5 * VECTOR_K
        @JvmField
        val VECTOR_SIZE_2 = 5
        val LENGTH = 22
        val MINI_BATCH_SIZE = 32

        @JvmStatic
        fun normalize(input: Double, min: Double, max: Double): Double {
            return (input - min) / (max - min) * 0.8 + 0.0001
        }

        @JvmStatic
        fun deNormalize(input: Double, min: Double, max: Double): Double {
            return min + (input - 0.0001) * (max - min) / 0.8
        }

        @JvmStatic
        fun getVectorSize(): Int {
            return CHUNK_SHIFT + VECTOR_SIZE_1 + VECTOR_SIZE_2
        }
    }

    init {
        val split = Math.round(stockDataList.size * splitRatio).toInt()

        indicators = Array(VECTOR_K + 1) { DoubleArray(stockDataList.size) }
        initializeIndicators(stockDataList)

        var startIndexTrainData = 0
        for (indicator in indicators!!) {
            for (j in indicator.indices) {
                if (indicator[j] != 0.0 && j > startIndexTrainData) {
                    startIndexTrainData = j
                    break
                }
            }
        }
        val indicatorsSlice = Array(VECTOR_K + 1) { DoubleArray(stockDataList.size - startIndexTrainData) }
        for (i in indicators!!.indices) {
            System.arraycopy(indicators!![i], startIndexTrainData, indicatorsSlice[i], 0, indicatorsSlice[i].size)
        }

        indicators = indicatorsSlice
        train = stockDataList.subList(startIndexTrainData, split)
        test = generateTestDataSet(stockDataList.subList(split, stockDataList.size))

        initializeOffsets()
    }

    private fun initializeIndicators(stockDataList: List<Candle>) {
        val candles = ArrayList(stockDataList)

        // MACD
        val chunkMacdSize = IndicatorsUtil.MACD_SLOW_PERIOD + CHUNK_SHIFT
        var chunkMacdCounter = chunkMacdSize
        for (i in chunkMacdSize until candles.size) {
            val inClose = DoubleArray(chunkMacdSize)
            var k = 0
            var j = i - chunkMacdSize
            while (k < chunkMacdSize) {
                inClose[k] = candles[j].close
                k++
                j++
            }
            val value = IndicatorsUtil.macd(inClose)
            indicators!![0][chunkMacdCounter++] = value
            if (value < mins[0]) mins[0] = value
            if (value > maxs[0]) maxs[0] = value
        }

        // RSI
        val chunkRsiSize = IndicatorsUtil.RSI_PERIOD + CHUNK_SHIFT
        var chunkRsiCounter = chunkRsiSize
        for (i in chunkRsiSize until candles.size) {
            val inClose = DoubleArray(chunkRsiSize)
            var k = 0
            var j = i - chunkRsiSize
            while (k < chunkRsiSize) {
                inClose[k] = candles[j].close
                k++
                j++
            }
            val value = IndicatorsUtil.rsi(inClose)
            indicators!![1][chunkRsiCounter++] = value
            if (value < mins[1]) mins[1] = value
            if (value > maxs[1]) maxs[1] = value
        }

        // ADX
        val chunkAdxSize = IndicatorsUtil.ADX_PERIOD + CHUNK_SHIFT
        var chunkAdxCounter = chunkAdxSize
        for (i in chunkAdxSize until candles.size) {
            val inClose = DoubleArray(chunkAdxSize)
            val inHigh = DoubleArray(chunkAdxSize)
            val inLow = DoubleArray(chunkAdxSize)
            var k = 0
            var j = i - chunkAdxSize
            while (k < chunkAdxSize) {
                inClose[k] = candles[j].close
                inHigh[k] = candles[j].high
                inLow[k] = candles[j].low
                k++
                j++
            }
            val value = IndicatorsUtil.adx(inClose, inLow, inHigh)
            indicators!![2][chunkAdxCounter++] = value
            if (value < mins[2]) mins[2] = value
            if (value > maxs[2]) maxs[2] = value
        }

        // MA Black
        var chunkMABCounter = IndicatorsUtil.MA_BLACK_PERIOD
        for (i in IndicatorsUtil.MA_BLACK_PERIOD until candles.size) {
            val inClose = DoubleArray(IndicatorsUtil.MA_BLACK_PERIOD)
            var k = 0
            var j = i - IndicatorsUtil.MA_BLACK_PERIOD
            while (k < IndicatorsUtil.MA_BLACK_PERIOD) {
                inClose[k] = candles[j].close
                k++
                j++
            }
            val value = IndicatorsUtil.movingAverageBlack(inClose)
            indicators!![3][chunkMABCounter++] = value
            if (value < mins[3]) mins[3] = value
            if (value > maxs[3]) maxs[3] = value
        }

        // MA White
        var chunkMAWCounter = IndicatorsUtil.MA_WHITE_PERIOD
        for (i in IndicatorsUtil.MA_WHITE_PERIOD until candles.size) {
            val inClose = DoubleArray(IndicatorsUtil.MA_WHITE_PERIOD)
            var k = 0
            var j = i - IndicatorsUtil.MA_WHITE_PERIOD
            while (k < IndicatorsUtil.MA_WHITE_PERIOD) {
                inClose[k] = candles[j].close
                k++
                j++
            }
            val value = IndicatorsUtil.movingAverageWhite(inClose)
            indicators!![4][chunkMAWCounter++] = value
            if (value < mins[4]) mins[4] = value
            if (value > maxs[4]) maxs[4] = value
        }

        // EMA
        var chunkEmaCounter = IndicatorsUtil.EMA_PERIOD
        for (i in IndicatorsUtil.EMA_PERIOD until candles.size) {
            val inClose = DoubleArray(IndicatorsUtil.EMA_PERIOD)
            var k = 0
            var j = i - IndicatorsUtil.EMA_PERIOD
            while (k < IndicatorsUtil.EMA_PERIOD) {
                inClose[k] = candles[j].close
                k++
                j++
            }
            val value = IndicatorsUtil.ema(inClose)
            indicators!![5][chunkEmaCounter++] = value
            if (value < mins[5]) mins[5] = value
            if (value > maxs[5]) maxs[5] = value
        }

        for (candle in candles) {
            if (candle.close < closes[0]) closes[0] = candle.close
            if (candle.close > closes[1]) closes[1] = candle.close
        }
    }

    private fun initializeOffsets() {
        exampleStartOffsets.clear()
        val window = LENGTH + 1
        for (i in 0 until train.size - window) {
            exampleStartOffsets.add(i)
        }
    }

    private fun generateTestDataSet(stockDataList: List<Candle>): List<Pair<INDArray, Double>> {
        val test = ArrayList<Pair<INDArray, Double>>()
        var l = 0
        for (i in VECTOR_SIZE_2 until stockDataList.size - 1) {
            var k = 0
            val input = Nd4j.create(intArrayOf(1, inputColumns()), 'f')
            val debugVector = ArrayList<String>(inputColumns())
            while (k < VECTOR_SIZE_1) {
                var n = 0
                while (n <= VECTOR_K) {
                    input.putScalar(intArrayOf(0, k), normalize(indicators!![n][l], mins[n], maxs[n]))
                    debugVector.add("$n(" + (l) + "):" + indicators!![n][l])
                    k++
                    n++
                }
                l++
            }
            l = i - VECTOR_SIZE_2 + 1
            k = VECTOR_SIZE_1
            var j = VECTOR_SIZE_2
            while (k < VECTOR_SIZE_1 + VECTOR_SIZE_2) {
                input.putScalar(intArrayOf(0, k), normalize(stockDataList[i - j].close, closes[0], closes[1]))
                debugVector.add((i - j).toString() + ":" + stockDataList[i - j].close)
                k++
                j--
            }
            test.add(Pair(input, normalize(stockDataList[i - j].close, closes[0], closes[1])))
            debugVector.add("res " + (i - j) + ": " + stockDataList[i - j].close)
            log.trace("TestDataSet " + (i - VECTOR_SIZE_2) + ": " + debugVector.toString())
        }
        return test
    }

    override fun next(num: Int): DataSet {
        if (exampleStartOffsets.size == 0) throw NoSuchElementException()

        val actualMiniBatchSize = Math.min(num, exampleStartOffsets.size)
        val input = Nd4j.create(intArrayOf(actualMiniBatchSize, inputColumns(), LENGTH), 'f')
        val label = Nd4j.create(intArrayOf(actualMiniBatchSize, totalOutcomes(), LENGTH), 'f')
        for (index in 0 until actualMiniBatchSize) {
            val startIdx = exampleStartOffsets.removeFirst() + VECTOR_SIZE_2
            var l = startIdx - VECTOR_SIZE_2
            for (i in startIdx until startIdx + LENGTH) {
                // input
                var k = 0
                val debugVector = ArrayList<String>(inputColumns())
                while (k < VECTOR_SIZE_1) {
                    var n = 0
                    while (n <= VECTOR_K) {
                        input.putScalar(intArrayOf(index, k, i - startIdx), normalize(indicators!![n][l], mins[n], maxs[n]))
                        debugVector.add("$n(" + (l) + "):" + indicators!![n][l])
                        k++
                        n++
                    }
                    l++
                }
                l = i - VECTOR_SIZE_2 + 1
                k = VECTOR_SIZE_1
                var j = VECTOR_SIZE_2
                while (k < VECTOR_SIZE_1 + VECTOR_SIZE_2) {
                    if (i - j >= train.size - 1) break
                    input.putScalar(intArrayOf(index, k, i - startIdx), normalize(train[i - j].close, closes[0], closes[1]))
                    debugVector.add((i - j).toString() + ":" + train[i - j].close)
                    k++
                    j--
                }
                // label
                val predictIndex = when {
                    train.size - 1 > i + 3 -> i + 3
                    train.size - 1 > i + 2 -> i + 2
                    train.size - 1 > i + 1 -> i + 1
                    train.size - 1 > i -> i
                    else -> train.size - 1
                }
                for (labelValue in 0 until totalOutcomes() - 1) {
                    label.putScalar(intArrayOf(index, labelValue, i - startIdx), normalize(indicators!![labelValue][predictIndex], mins[labelValue], maxs[labelValue]))
                    debugVector.add("resInd" + labelValue + " $predictIndex:" + indicators!![labelValue][predictIndex])
                }
                label.putScalar(intArrayOf(index, totalOutcomes() - 1, i - startIdx), normalize(train[predictIndex].close, closes[0], closes[1]))
                debugVector.add("resClose" + (totalOutcomes() - 1) + " $predictIndex:" + train[predictIndex].close)
                log.trace("NextDataSet $index(" + (i - startIdx) + "): " + debugVector.toString())
            }
            if (exampleStartOffsets.size == 0) break
        }

        return DataSet(input, label)
    }

    override fun remove() {
        // nothing
    }

    override fun totalExamples(): Int {
        return train.size - LENGTH - 1
    }

    override fun inputColumns(): Int {
        return VECTOR_SIZE_1 + VECTOR_SIZE_2 + 1
    }

    override fun totalOutcomes(): Int {
        return indicators!!.size + 1
    }

    override fun resetSupported(): Boolean {
        return false
    }

    override fun asyncSupported(): Boolean {
        return false
    }

    override fun reset() {
        initializeOffsets()
    }

    override fun batch(): Int {
        return MINI_BATCH_SIZE
    }

    override fun cursor(): Int {
        return totalExamples() - exampleStartOffsets.size
    }

    override fun numExamples(): Int {
        return totalExamples()
    }

    override fun setPreProcessor(dataSetPreProcessor: DataSetPreProcessor) {
        this.dataSetPreProcessor = dataSetPreProcessor
    }

    override fun getPreProcessor(): DataSetPreProcessor? {
        return dataSetPreProcessor
    }

    override fun getLabels(): List<String>? {
        return null
    }

    override fun hasNext(): Boolean {
        return exampleStartOffsets.size > 0
    }

    override fun next(): DataSet {
        return next(MINI_BATCH_SIZE)
    }
}
