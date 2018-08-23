package com.oanda.predictor.util

import org.deeplearning4j.nn.api.OptimizationAlgorithm
import org.deeplearning4j.nn.conf.BackpropType
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.Updater
import org.deeplearning4j.nn.conf.layers.DenseLayer
import org.deeplearning4j.nn.conf.layers.GravesLSTM
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.linalg.lossfunctions.LossFunctions

object LSTMNetwork {

    private val learningRate = 0.05
    private val iterations = 1
    private val seed = 777
    private val score = 100

    private val lstmLayer1Size = 256
    private val lstmLayer2Size = 256
    private val denseLayerSize = 32
    private val dropoutRatio = 0.2
    private val truncatedBPTTLength = 22

    @JvmStatic
    fun buildLstmNetworks(iterator: DataSetIterator): MultiLayerNetwork {
        val conf = NeuralNetConfiguration.Builder()
                .seed(seed)
                .iterations(iterations)
                .learningRate(learningRate)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.RMSPROP)
                .regularization(true)
                .l2(1e-4)
                .list()
                .layer(0, GravesLSTM.Builder()
                        .nIn(iterator.inputColumns())
                        .nOut(lstmLayer1Size)
                        .activation(Activation.TANH)
                        .gateActivationFunction(Activation.HARDSIGMOID)
                        .dropOut(dropoutRatio)
                        .build())
                .layer(1, GravesLSTM.Builder()
                        .nIn(lstmLayer1Size)
                        .nOut(lstmLayer2Size)
                        .activation(Activation.TANH)
                        .gateActivationFunction(Activation.HARDSIGMOID)
                        .dropOut(dropoutRatio)
                        .build())
                .layer(2, DenseLayer.Builder()
                        .nIn(lstmLayer2Size)
                        .nOut(denseLayerSize)
                        .activation(Activation.RELU)
                        .build())
                .layer(3, RnnOutputLayer.Builder()
                        .nIn(denseLayerSize)
                        .nOut(iterator.totalOutcomes())
                        .activation(Activation.IDENTITY)
                        .lossFunction(LossFunctions.LossFunction.MSE)
                        .build())
                .backpropType(BackpropType.TruncatedBPTT)
                .tBPTTForwardLength(truncatedBPTTLength)
                .tBPTTBackwardLength(truncatedBPTTLength)
                .pretrain(false)
                .backprop(true)
                .build()

        val net = MultiLayerNetwork(conf)
        net.init()
        net.setListeners(ScoreIterationListener(score))

        for (i in 0 until score) {
            while (iterator.hasNext()) net.fit(iterator.next()) // fit model using mini-batch data
            iterator.reset() // reset iterator
            net.rnnClearPreviousState() // clear previous state
        }

        return net
    }
}
