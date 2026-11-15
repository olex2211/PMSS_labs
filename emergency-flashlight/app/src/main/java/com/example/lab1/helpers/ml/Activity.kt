package com.example.lab1.helpers.ml

/**
 * Classes of the public UCI HAR model, in the order of its output tensor.
 * The model recognises human activity; it has no "shake" class.
 */
enum class Activity(val label: String, val moving: Boolean) {
    Walking("Walking", true),
    WalkingUpstairs("Walking upstairs", true),
    WalkingDownstairs("Walking downstairs", true),
    Sitting("Sitting", false),
    Standing("Standing", false),
    Laying("Laying", false),
}

/** Result of one inference: the winning class, its probability and all probabilities in class order. */
data class Prediction(val activity: Activity, val confidence: Float, val scores: List<Float>) {
    companion object {
        /** [scores] are the softmax output of the model. */
        fun fromScores(scores: FloatArray): Prediction {
            require(scores.size == Activity.entries.size) { "Expected ${Activity.entries.size} scores, got ${scores.size}" }
            val best = scores.indices.maxBy { scores[it] }
            return Prediction(Activity.entries[best], scores[best], scores.toList())
        }
    }
}
