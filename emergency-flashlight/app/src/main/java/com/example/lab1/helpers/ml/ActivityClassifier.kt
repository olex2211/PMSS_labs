package com.example.lab1.helpers.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A model that turns one window (row-major, [ImuWindowBuilder.WINDOW] x [ImuWindowBuilder.CHANNELS]) into a class. */
interface ActivityModel : AutoCloseable {
    fun classify(window: FloatArray): Prediction
}

/**
 * Runs the bundled public model with the TensorFlow Lite interpreter.
 * Tensor contract read from the file: input float32 [1, 128, 9], output float32 [1, 6] (softmax).
 * Inference is CPU heavy; call it from Dispatchers.Default, never from the main thread.
 */
class TfliteActivityModel(context: Context, assetName: String = MODEL_ASSET) : ActivityModel {
    private val interpreter = Interpreter(loadAsset(context, assetName))
    private val input = Array(1) { Array(ImuWindowBuilder.WINDOW) { FloatArray(ImuWindowBuilder.CHANNELS) } }
    private val output = Array(1) { FloatArray(Activity.entries.size) }

    @Synchronized
    override fun classify(window: FloatArray): Prediction {
        require(window.size == ImuWindowBuilder.WINDOW * ImuWindowBuilder.CHANNELS) { "Bad window size ${window.size}" }
        for (t in 0 until ImuWindowBuilder.WINDOW) {
            window.copyInto(input[0][t], 0, t * ImuWindowBuilder.CHANNELS, (t + 1) * ImuWindowBuilder.CHANNELS)
        }
        interpreter.run(input, output)
        return Prediction.fromScores(output[0])
    }

    @Synchronized
    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "imu_pruned_int8.tflite"

        private fun loadAsset(context: Context, name: String): ByteBuffer {
            val bytes = context.assets.open(name).use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
        }
    }
}
