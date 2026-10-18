package `in`.aladin.keyboard

import kotlin.math.exp
import kotlin.math.tanh

/**
 * Tiny on-device adaptive scorer. Trains repeatedly on the user's locally learned
 * word counts and word-pair counts; no network and no typed text is uploaded.
 */
class LocalNeuralPredictor {
    private val inputSize = 4
    private val hiddenSize = 8
    private val w1 = Array(hiddenSize) { i -> DoubleArray(inputSize) { j -> if (i == j) 0.12 else ((i * 7 + j * 3) % 9 - 4) * 0.015 } }
    private val b1 = DoubleArray(hiddenSize)
    private val w2 = DoubleArray(hiddenSize) { (it % 3 - 1) * 0.04 }
    private var b2 = 0.0
    private var trainedSteps = 0

    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x.coerceIn(-12.0, 12.0)))

    fun train(wordCounts: Map<String, Int>, pairCounts: Map<String, Int>, epochs: Int = 8) {
        if (wordCounts.isEmpty()) return
        val maxCount = (wordCounts.values.maxOrNull() ?: 1).coerceAtLeast(1).toDouble()
        val samples = wordCounts.entries.toList()
        repeat(epochs.coerceIn(1, 16)) {
            samples.forEach { (word, count) ->
                val pairCount = pairCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                val features = doubleArrayOf(
                    (count / maxCount).coerceIn(0.0, 1.0),
                    (word.length / 16.0).coerceIn(0.0, 1.0),
                    (if (word.any { it.code > 127 }) 1.0 else 0.0),
                    ((pairCounts["|$word"] ?: 0).toDouble() / pairCount).coerceIn(0.0, 1.0)
                )
                // Frequency is a weak positive target; repeated epochs adapt the tiny MLP.
                val target = (0.25 + 0.65 * (count / maxCount)).coerceIn(0.0, 1.0)
                update(features, target)
            }
        }
    }

    private fun update(x: DoubleArray, target: Double) {
        val hidden = DoubleArray(hiddenSize) { i ->
            var z = b1[i]
            for (j in 0 until inputSize) z += w1[i][j] * x[j]
            tanh(z)
        }
        val y = sigmoid(b2 + hidden.indices.sumOf { w2[it] * hidden[it] })
        val error = (y - target).coerceIn(-1.0, 1.0)
        val lr = 0.025
        val oldW2 = w2.copyOf()
        for (i in w2.indices) w2[i] -= lr * error * hidden[i] * y * (1.0 - y)
        b2 -= lr * error * y * (1.0 - y)
        for (i in hidden.indices) {
            val grad = error * oldW2[i] * (1.0 - hidden[i] * hidden[i]) * y * (1.0 - y)
            for (j in 0 until inputSize) w1[i][j] -= lr * grad * x[j]
            b1[i] -= lr * grad
        }
        trainedSteps++
    }

    fun score(word: String, count: Int, maxCount: Int, prefixLength: Int, pairBoost: Int): Double {
        val x = doubleArrayOf(
            (count.toDouble() / maxCount.coerceAtLeast(1)).coerceIn(0.0, 1.0),
            (word.length / 16.0).coerceIn(0.0, 1.0),
            (if (word.any { it.code > 127 }) 1.0 else 0.0),
            (pairBoost / 10.0).coerceIn(0.0, 1.0)
        )
        val hidden = DoubleArray(hiddenSize) { i ->
            var z = b1[i]
            for (j in 0 until inputSize) z += w1[i][j] * x[j]
            tanh(z)
        }
        return sigmoid(b2 + hidden.indices.sumOf { w2[it] * hidden[it] }) +
            (prefixLength * 0.02) + (pairBoost * 0.015)
    }

    fun getTrainingSteps() = trainedSteps
}

/**
 * Lightweight self-attention-style contextual reranker over locally observed word pairs.
 * This is a tiny from-scratch attention component, not a pretrained large language model.
 */
class LocalSelfAttention {
    fun contextBoost(candidate: String, recentWords: List<String>, pairCounts: Map<String, Int>): Double {
        if (recentWords.isEmpty()) return 0.0
        val tail = recentWords.takeLast(4)
        var weighted = 0.0
        var totalWeight = 0.0
        tail.forEachIndexed { index, context ->
            val distanceWeight = (index + 1).toDouble() / tail.size
            val observed = (pairCounts["${context}|${candidate}"] ?: 0).toDouble()
            // Attention-like normalization: recent contexts receive higher weight.
            val attention = exp(distanceWeight).coerceAtMost(4.0)
            weighted += attention * observed
            totalWeight += attention
        }
        return if (totalWeight == 0.0) 0.0 else (weighted / totalWeight) * 0.08
    }
}
