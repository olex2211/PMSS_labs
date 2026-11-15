package com.example.lab1

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lab1.helpers.ml.ImuWindowBuilder
import com.example.lab1.helpers.ml.TfliteActivityModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Loads the real bundled .tflite and runs the LiteRT interpreter on the device. */
@RunWith(AndroidJUnit4::class)
class ActivityClassifierTest {
    private lateinit var model: TfliteActivityModel
    private val size = ImuWindowBuilder.WINDOW * ImuWindowBuilder.CHANNELS

    @Before fun load() {
        model = TfliteActivityModel(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After fun close() = model.close()

    @Test fun outputIsASoftmaxOverSixClasses() {
        val p = model.classify(FloatArray(size))
        assertEquals(6, p.scores.size)
        assertEquals(1f, p.scores.sum(), 0.02f)
        assertTrue(p.scores.all { it in 0f..1f })
        assertEquals(p.scores.max(), p.confidence, 0f)
    }

    @Test fun differentInputsGiveDifferentOutputs() {
        val still = FloatArray(size) { if (it % 9 == 8) 1f else 0f }
        val shaking = FloatArray(size) { i -> if (i % 9 < 3) (if ((i / 9) % 2 == 0) 1.5f else -1.5f) else 0f }
        assertNotEquals(model.classify(still).scores, model.classify(shaking).scores)
    }

    @Test fun sameInputIsDeterministic() {
        val w = FloatArray(size) { (it % 17) / 17f }
        assertEquals(model.classify(w).scores, model.classify(w).scores)
    }

    @Test(expected = IllegalArgumentException::class) fun wrongWindowSizeIsRejected() {
        model.classify(FloatArray(10))
    }
}
