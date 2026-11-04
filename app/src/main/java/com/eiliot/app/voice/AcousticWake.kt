package com.eiliot.app.voice

import java.nio.ByteBuffer
import kotlin.math.*

private const val FRAME_SAMPLES = 512
private const val HOP_SAMPLES = 256
private const val COEFFICIENTS = 12
private const val MIN_SAMPLES = 4000
private const val MAX_SAMPLES = 32000
private const val MAX_FEATURES = (1 + (MAX_SAMPLES - FRAME_SAMPLES) / HOP_SAMPLES) * COEFFICIENTS
internal const val ACOUSTIC_WAKE = "hey_elliot_acoustic"

/** Bounded, amplitude-independent spectral features. No PCM is stored in the profile. */
internal object AcousticWakeFeatures {
    private const val FILTERS = 24
    private val window = DoubleArray(FRAME_SAMPLES) { .5 - .5 * cos(2 * PI * it / (FRAME_SAMPLES - 1)) }
    private val fftCos = DoubleArray(FRAME_SAMPLES / 2) { cos(2 * PI * it / FRAME_SAMPLES) }
    private val fftSin = DoubleArray(FRAME_SAMPLES / 2) { -sin(2 * PI * it / FRAME_SAMPLES) }
    private val reversed = IntArray(FRAME_SAMPLES) { Integer.reverse(it) ushr 23 }
    private fun mel(hz: Double) = 2595 * log10(1 + hz / 700)
    private val melEdges = DoubleArray(FILTERS + 2) { mel(80.0) + (mel(7400.0) - mel(80.0)) * it / (FILTERS + 1) }
    private val filters = Array(FILTERS) { filter ->
        val left = melEdges[filter]
        val center = melEdges[filter + 1]
        val right = melEdges[filter + 2]
        (0..FRAME_SAMPLES / 2).mapNotNull { bin ->
            val m = mel(bin * 16000.0 / FRAME_SAMPLES)
            val weight = min((m - left) / (center - left), (right - m) / (right - center))
            if (weight > 0) bin to weight else null
        }
    }
    private val dct = Array(COEFFICIENTS) { index ->
        DoubleArray(FILTERS) { cos(PI * (index + 1) * (it + .5) / FILTERS) * sqrt(2.0 / FILTERS) }
    }

    fun extract(pcm: FloatArray, count: Int): List<Float>? {
        val rms = DoubleArray(count / 160) { block ->
            var power = 0.0
            for (i in block * 160 until (block + 1) * 160) power += pcm[i].toDouble() * pcm[i]
            sqrt(power / 160)
        }
        val gate = max(.001, (rms.maxOrNull() ?: 0.0) * .08)
        val first = rms.indexOfFirst { it > gate }
        if (first < 0) return null
        val start = max(0, first - 2) * 160
        val end = min(rms.size, rms.indexOfLast { it > gate } + 3) * 160
        if (end - start !in MIN_SAMPLES..MAX_SAMPLES) return null

        val real = DoubleArray(FRAME_SAMPLES)
        val imaginary = DoubleArray(FRAME_SAMPLES)
        val bands = DoubleArray(FILTERS)
        val features = ArrayList<Float>()
        for (at in start..end - FRAME_SAMPLES step HOP_SAMPLES) {
            imaginary.fill(0.0)
            for (i in real.indices) real[reversed[i]] = pcm[at + i] * window[i]
            var length = 2
            while (length <= FRAME_SAMPLES) {
                val half = length / 2
                for (block in 0 until FRAME_SAMPLES step length) {
                    for (i in 0 until half) {
                        val left = block + i
                        val right = left + half
                        val twiddle = i * FRAME_SAMPLES / length
                        val re = real[right] * fftCos[twiddle] - imaginary[right] * fftSin[twiddle]
                        val im = real[right] * fftSin[twiddle] + imaginary[right] * fftCos[twiddle]
                        real[right] = real[left] - re
                        imaginary[right] = imaginary[left] - im
                        real[left] += re
                        imaginary[left] += im
                    }
                }
                length *= 2
            }
            for (band in bands.indices) {
                bands[band] = filters[band].sumOf { (bin, weight) ->
                    weight * (real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                }
            }
            val floor = max(1e-7, bands.max() * .0001)
            for (band in bands.indices) bands[band] = ln(max(floor, bands[band]))
            for (row in dct) features.add(row.indices.sumOf { row[it] * bands[it] }.toFloat())
        }
        return features
    }

    /** Align the complete phrase, with a narrow allowance for different speaking speeds. */
    fun distance(a: List<Float>, b: List<Float>): Float {
        val rows = a.size / COEFFICIENTS
        val columns = b.size / COEFFICIENTS
        if (rows.toFloat() / columns !in .7f..1.43f) return Float.POSITIVE_INFINITY
        var previous = FloatArray(columns + 1) { Float.POSITIVE_INFINITY }
        var current = previous.copyOf()
        previous[0] = 0f
        for (i in 1..rows) {
            current.fill(Float.POSITIVE_INFINITY)
            val center = i * columns / rows
            for (j in max(1, center - 12)..min(columns, center + 12)) {
                var cost = 0f
                for (k in 0 until COEFFICIENTS) {
                    val delta = a[(i - 1) * COEFFICIENTS + k] - b[(j - 1) * COEFFICIENTS + k]
                    cost += delta * delta
                }
                cost /= COEFFICIENTS
                current[j] = minOf(previous[j - 1] + 2 * cost, previous[j] + cost + .15f, current[j - 1] + cost + .15f)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[columns] / (rows + columns)
    }
}

/** The same bounded speech window is used by enrollment, fresh checks and live wake. */
internal class AcousticWakeSegmenter {
    private val preRoll = AudioPreRoll(FRAME_SAMPLES * 8)
    private val pcm = FloatArray(FRAME_SAMPLES * 88)
    private var used = 0
    private var quiet = 0
    private var overflow = false

    fun accept(frame: FloatArray, speechProbability: Float): List<Float>? {
        require(frame.size == FRAME_SAMPLES)
        val speaking = speechProbability > if (used == 0) .5f else .35f
        if (used == 0) {
            if (!speaking) {
                preRoll.add(frame)
                return null
            }
            val prefix = preRoll.snapshot()
            prefix.copyInto(pcm)
            used = prefix.size
            prefix.fill(0f)
        }
        if (used + frame.size <= pcm.size) {
            frame.copyInto(pcm, used)
            used += frame.size
        } else {
            overflow = true
        }
        quiet = if (speaking) 0 else quiet + 1
        if (quiet < 10) return null
        val features = if (overflow) null else AcousticWakeFeatures.extract(pcm, used - 7 * FRAME_SAMPLES)
        reset()
        return features
    }

    fun reset() {
        preRoll.clear()
        pcm.fill(0f, 0, used)
        used = 0
        quiet = 0
        overflow = false
    }
}

data class AcousticWakeProfile internal constructor(
    val threshold: Float,
    val wakes: List<List<Float>>,
    val controls: List<List<Float>>
) {
    init {
        require(threshold.isFinite() && threshold in .08f..2f)
        require(wakes.size in 3..4 && controls.size in 1..2)
        require((wakes + controls).all { features ->
            features.size in COEFFICIENTS * 14..MAX_FEATURES &&
                features.size % COEFFICIENTS == 0 && features.all { it.isFinite() && abs(it) <= 80f }
        })
    }

    internal fun matches(features: List<Float>, skipWake: Int = -1): Boolean {
        val wake = wakes.indices.filter { it != skipWake }.minOf { AcousticWakeFeatures.distance(wakes[it], features) }
        return wake <= threshold && wake < .8f * controls.minOf { AcousticWakeFeatures.distance(it, features) }
    }

    internal fun encode(): ByteArray {
        val all = wakes + controls
        val buffer = ByteBuffer.allocate(8 + all.sumOf { 2 + it.size * 4 })
        buffer.putShort(1).putFloat(threshold).put(wakes.size.toByte()).put(controls.size.toByte())
        for (features in all) {
            buffer.putShort(features.size.toShort())
            features.forEach { buffer.putFloat(it) }
        }
        return buffer.array()
    }

    companion object {
        internal fun decode(bytes: ByteArray): AcousticWakeProfile {
            require(bytes.size in 8..8 + 6 * (2 + MAX_FEATURES * 4))
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.short == 1.toShort())
            val threshold = buffer.float
            val wakeCount = buffer.get().toInt()
            val controlCount = buffer.get().toInt()
            require(wakeCount in 3..4 && controlCount in 1..2)
            val features = List(wakeCount + controlCount) {
                val size = buffer.short.toInt()
                require(size in COEFFICIENTS * 14..MAX_FEATURES && size * 4 <= buffer.remaining())
                List(size) { buffer.float }
            }
            require(!buffer.hasRemaining())
            return AcousticWakeProfile(threshold, features.take(wakeCount), features.drop(wakeCount))
        }

        internal fun enroll(wakes: List<List<Float>>, controls: List<List<Float>>): AcousticWakeProfile? {
            if (wakes.size !in 3..4 || controls.size !in 1..2) return null
            val neighbors = wakes.indices.map { i ->
                wakes.indices.filter { it != i }.minOf { j -> AcousticWakeFeatures.distance(wakes[i], wakes[j]) }
            }.sorted()
            val closestControl = wakes.minOf { wake -> controls.minOf { AcousticWakeFeatures.distance(wake, it) } }
            val limit = minOf(max(.12f, neighbors[2] * 1.7f), closestControl * .8f, 2f)
            if (limit < .08f) return null
            return AcousticWakeProfile(limit, wakes, controls)
        }
    }
}
