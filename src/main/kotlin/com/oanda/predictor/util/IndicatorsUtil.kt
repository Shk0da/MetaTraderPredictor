package com.oanda.predictor.util

import com.tictactec.ta.lib.Core
import com.tictactec.ta.lib.MInteger
import com.tictactec.ta.lib.RetCode
import org.slf4j.LoggerFactory

object IndicatorsUtil {

    private val log = LoggerFactory.getLogger(IndicatorsUtil::class.java)
    private val talib = Core()

    const val MACD_FAST_PERIOD = 7
    const val MACD_SLOW_PERIOD = 21
    const val MACD_PERIOD = 14
    const val RSI_PERIOD = 14
    const val ADX_PERIOD = 14
    const val MA_BLACK_PERIOD = 63
    const val MA_WHITE_PERIOD = 7
    const val EMA_PERIOD = 14

    fun macd(inClose: DoubleArray): Double {
        val outMACD = DoubleArray(inClose.size)
        val outMACDSignal = DoubleArray(inClose.size)
        val outMACDHist = DoubleArray(inClose.size)
        val outBegIdx = MInteger()
        val outNBElement = MInteger()

        val retCode = talib.macd(0, inClose.size - 1, inClose, MACD_FAST_PERIOD, MACD_SLOW_PERIOD, MACD_PERIOD,
                outBegIdx, outNBElement, outMACD, outMACDSignal, outMACDHist)

        var value = 0.0
        if (RetCode.Success == retCode && outNBElement.value > 0) {
            value = outMACD[outNBElement.value - 1]
        }

        log.trace("MACD: {}", value)
        return value
    }

    fun rsi(inClose: DoubleArray): Double {
        val outReal = DoubleArray(inClose.size)
        val outBegIdx = MInteger()
        val outNBElement = MInteger()

        val retCodeRSI = talib.rsi(0, inClose.size - 1, inClose, RSI_PERIOD,
                outBegIdx, outNBElement, outReal)

        var rsi = 0.0
        if (RetCode.Success == retCodeRSI && outNBElement.value > 0) {
            rsi = outReal[outNBElement.value - 1]
        }

        log.trace("RSI: {}", rsi)
        return rsi
    }

    fun adx(inClose: DoubleArray, inLow: DoubleArray, inHigh: DoubleArray): Double {
        val outADX = DoubleArray(inClose.size)
        val beginADX = MInteger()
        val lengthADX = MInteger()
        val retCodeADX = talib.adx(
                0, inClose.size - 1,
                inHigh, inLow, inClose, ADX_PERIOD,
                beginADX, lengthADX, outADX
        )

        var adx = 0.0
        if (RetCode.Success == retCodeADX && lengthADX.value > 0) {
            adx = outADX[lengthADX.value - 1]
        }

        log.trace("ADX: {}", adx)
        return adx
    }

    fun movingAverageBlack(inClose: DoubleArray): Double {
        val outBlackMA = DoubleArray(inClose.size)
        val beginBlackMA = MInteger()
        val lengthBlackMA = MInteger()
        val retCodeBlackMA = talib.trima(
                0, inClose.size - 1, inClose, MA_BLACK_PERIOD, beginBlackMA, lengthBlackMA, outBlackMA
        )

        val blackMA = if (RetCode.Success == retCodeBlackMA && lengthBlackMA.value > 0) outBlackMA[lengthBlackMA.value - 1] else 0.0
        log.trace("MovingAverageBlack: {}", blackMA)
        return blackMA
    }

    fun movingAverageWhite(inClose: DoubleArray): Double {
        val outWhiteMA = DoubleArray(inClose.size)
        val beginWhiteMA = MInteger()
        val lengthWhiteMA = MInteger()
        val retCodeWhiteMA = talib.trima(
                0, inClose.size - 1, inClose, MA_WHITE_PERIOD, beginWhiteMA, lengthWhiteMA, outWhiteMA
        )

        val whiteMA = if (RetCode.Success == retCodeWhiteMA && lengthWhiteMA.value > 0) outWhiteMA[lengthWhiteMA.value - 1] else 0.0
        log.trace("MovingAverageWhite: {}", whiteMA)
        return whiteMA
    }

    fun ema(inClose: DoubleArray): Double {
        val outReal = DoubleArray(inClose.size)
        val outBegIdx = MInteger()
        val outNBElement = MInteger()

        val retCode = talib.ema(
                0, inClose.size - 1, inClose, EMA_PERIOD, outBegIdx, outNBElement, outReal
        )

        val value = if (RetCode.Success == retCode && outNBElement.value > 0) outReal[outNBElement.value - 1] else 0.0
        log.trace("EMA: {}", value)
        return value
    }
}
