package com.wellmosphere.freedsp.eq

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fits peaking bands to (target − measurement) inside the cable's range. A port of FreeDSP-Studio fit.rs
 * (its "range" mode), which follows AutoEq (Jaakko Pasanen, MIT): matching-pursuit start, then per-parameter
 * golden-section refinement of an MSE loss with a sharpness penalty; outside the range the EQ is pushed flat.
 * Stacked bands are merged and bands that barely help are dropped, so fewer than nine may come back.
 */
object AutoFit {
    private const val N = 180
    private const val SPILL_W = 0.1      // weight of "stay flat outside the fit range"
    private const val STACK_OCT = 1.0 / 12 // two bands closer than this are one filter's job
    private const val PRUNE_DB = 0.02    // a band must improve the fit by this much (dB RMS) to keep its slot
    private const val FS = 48_000.0

    private val fs = DoubleArray(N + 1) { 20.0 * 1000.0.pow(it / N.toDouble()) }
    private val w = DoubleArray(N + 1) { 2 * PI * fs[it] / FS }
    private val c1 = DoubleArray(N + 1) { cos(w[it]) }; private val s1 = DoubleArray(N + 1) { sin(w[it]) }
    private val c2 = DoubleArray(N + 1) { cos(2 * w[it]) }; private val s2 = DoubleArray(N + 1) { sin(2 * w[it]) }
    private val zeros = DoubleArray(N + 1)

    private class B(var freq: Double, var gain: Double, var q: Double) { fun copy() = B(freq, gain, q) }
    private enum class P { FREQ, GAIN, Q }

    private val lo = Limits.FREQ.start
    private val hi = Limits.FREQ.endInclusive
    private val gMin = Limits.GAIN.start
    private val gMax = Limits.GAIN.endInclusive
    private val qMin = Limits.Q.start
    private val qMax = Limits.Q.endInclusive

    private fun jround(x: Double) = floor(x + 0.5)
    private fun round1(x: Double) = jround(x * 10) / 10
    private fun round2(x: Double) = jround(x * 100) / 100

    private fun resp(b: B): DoubleArray {
        if (b.gain == 0.0) return zeros
        val c = Response.biquad(FilterType.PEAK, b.freq, b.gain, b.q, FS)
        return DoubleArray(N + 1) { i ->
            val nr = c[0] + c[1] * c1[i] + c[2] * c2[i]; val ni = -(c[1] * s1[i] + c[2] * s2[i])
            val dr = c[3] + c[4] * c1[i] + c[5] * c2[i]; val di = -(c[4] * s1[i] + c[5] * s2[i])
            10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
        }
    }

    private fun respExcept(bands: List<B>, k: Int): DoubleArray {
        val s = DoubleArray(N + 1)
        bands.forEachIndexed { j, b -> if (j != k && b.gain != 0.0) resp(b).forEachIndexed { i, v -> s[i] += v } }
        return s
    }

    private fun sharpPenalty(b: B) = if (b.q > 4) 0.0006 * (b.q - 4) * (b.q - 4) * (b.gain * b.gain + 1) else 0.0

    private fun loss(band: B, others: DoubleArray, err: DoubleArray): Double {
        val r = resp(band)
        var se = 0.0; var n = 0; var spill = 0.0; var sn = 0
        for (i in 0..N) {
            val comb = others[i] + r[i]
            if (fs[i] < lo || fs[i] > hi) { spill += comb * comb; sn++ } // outside the range: leave it alone
            else { val d = err[i] - comb; se += d * d; n++ }
        }
        return (if (n > 0) se / n else 0.0) + (if (sn > 0) SPILL_W * spill / sn else 0.0) + sharpPenalty(band)
    }

    private fun lossAll(bands: List<B>, err: DoubleArray) =
        loss(B(1000.0, 0.0, 1.0), respExcept(bands, -1), err) + bands.sumOf(::sharpPenalty)

    private fun get(b: B, p: P) = when (p) { P.FREQ -> b.freq; P.GAIN -> b.gain; P.Q -> b.q }
    private fun put(b: B, p: P, v: Double, a: Double, c: Double) = when (p) {
        P.FREQ -> b.freq = jround(v.coerceIn(a, c))
        P.GAIN -> b.gain = round1(v).coerceIn(a, c)
        P.Q -> b.q = round2(v).coerceIn(a, c)
    }

    /** Golden-section on one parameter; only moves if it actually helps (the loss is bumpy along frequency). */
    private fun opt(bands: List<B>, k: Int, p: P, a0: Double, c0: Double, err: DoubleArray, log: Boolean) {
        val others = respExcept(bands, k)
        val b = bands[k]
        var a = if (log) ln(a0) else a0; var c = if (log) ln(c0) else c0
        fun v(x: Double) = if (log) exp(x) else x
        fun ev(x: Double): Double { put(b, p, v(x), a0, c0); return loss(b, others, err) }
        val keep = get(b, p)
        val before = loss(b, others, err)
        val gr = (sqrt(5.0) - 1) / 2
        var x1 = c - gr * (c - a); var x2 = a + gr * (c - a)
        var f1 = ev(x1); var f2 = ev(x2)
        repeat(16) {
            if (f1 < f2) { c = x2; x2 = x1; f2 = f1; x1 = c - gr * (c - a); f1 = ev(x1) }
            else { a = x1; x1 = x2; f1 = f2; x2 = a + gr * (c - a); f2 = ev(x2) }
        }
        put(b, p, v((a + c) / 2), a0, c0)
        if (loss(b, others, err) > before) when (p) { P.FREQ -> b.freq = keep; P.GAIN -> b.gain = keep; P.Q -> b.q = keep }
    }

    /** The bands that bring [measurement] closest to [target], as a full nine-band EQ with a safe preamp. */
    fun fit(measurement: Curve, target: Curve): Eq {
        val err = DoubleArray(N + 1) { target.at(fs[it]) - measurement.at(fs[it]) }
        val inRange = BooleanArray(N + 1) { fs[it] in lo..hi }
        fun near(bands: List<B>, i: Int) = bands.any { abs(log2(fs[i] / it.freq)) < STACK_OCT }
        // Matching pursuit: a band on the biggest remaining error, never on top of an existing one.
        fun pick(bands: List<B>, r: DoubleArray): Pair<Int, Double>? {
            var bi = -1; var bv = 0.0
            for (i in 0..N) if (inRange[i] && !near(bands, i) && abs(r[i]) > abs(bv)) { bv = r[i]; bi = i }
            return if (bi < 0) null else bi to bv
        }
        fun start(i: Int, v: Double) = B(jround(fs[i].coerceIn(lo, hi)), round1(v).coerceIn(gMin, gMax), 1.4)

        val initial = mutableListOf<B>()
        val resid = err.copyOf()
        repeat(Eq.BANDS) {
            val (bi, bv) = pick(initial, resid) ?: return@repeat
            if (abs(bv) < 0.1) return@repeat // nothing left worth a band
            val band = start(bi, bv)
            initial += band
            // Subtract what the spot needs, not the gain-capped band, so capped bands don't pile up.
            resp(B(band.freq, round1(bv), band.q)).forEachIndexed { i, x -> resid[i] -= x }
        }

        fun refine(bands: List<B>, rounds: Int) = repeat(rounds) {
            for (k in bands.indices) {
                opt(bands, k, P.GAIN, gMin, gMax, err, false)
                opt(bands, k, P.Q, qMin, qMax, err, false)
                opt(bands, k, P.FREQ, maxOf(lo, bands[k].freq / 2), minOf(hi, bands[k].freq * 2), err, true)
            }
        }
        // Fold pairs closer than STACK_OCT into one band (|gain|-weighted centre and width, gains summed).
        fun mergeStacked(bands: MutableList<B>): Int {
            var merged = 0; var a = 0
            while (a < bands.size) {
                var b = bands.size - 1
                while (b > a) {
                    val x = bands[a]; val y = bands[b]
                    if (abs(log2(x.freq / y.freq)) < STACK_OCT) {
                        val wa = abs(x.gain).takeIf { it != 0.0 } ?: 1e-6; val wb = abs(y.gain).takeIf { it != 0.0 } ?: 1e-6
                        x.freq = jround(exp((wa * ln(x.freq) + wb * ln(y.freq)) / (wa + wb)))
                        x.q = round2((wa * x.q + wb * y.q) / (wa + wb)).coerceIn(qMin, qMax)
                        x.gain = round1(x.gain + y.gain).coerceIn(gMin, gMax)
                        bands.removeAt(b); merged++
                    }
                    b--
                }
                a++
            }
            return merged
        }
        fun leftover(bands: List<B>): DoubleArray = err.copyOf().also { e -> bands.forEach { b -> resp(b).forEachIndexed { i, x -> e[i] -= x } } }

        fun finish(midMerge: Boolean): Pair<MutableList<B>, Double> {
            val bands = initial.map { it.copy() }.toMutableList()
            refine(bands, 6)
            var pass = 0
            while (midMerge && pass < 3 && mergeStacked(bands) > 0) {
                while (bands.size < Eq.BANDS) {
                    val (bi, bv) = pick(bands, leftover(bands)) ?: break
                    if (abs(bv) < 0.1) break
                    bands += start(bi, bv)
                }
                refine(bands, 3); pass++
            }
            // Drop bands that do nothing: near-flat ones, then whichever costs least to lose, while under PRUNE_DB.
            bands.removeAll { abs(it.gain) <= 0.1 }
            while (bands.size > 1) {
                val base = sqrt(lossAll(bands, err))
                val (k, cost) = bands.indices.map { k -> k to sqrt(lossAll(bands.filterIndexed { j, _ -> j != k }, err)) - base }.minBy { it.second }
                if (cost >= PRUNE_DB) break
                bands.removeAt(k); refine(bands, 1)
            }
            if (mergeStacked(bands) > 0) refine(bands, 1)
            mergeStacked(bands)
            return bands to lossAll(bands, err)
        }

        val plain = finish(false); val merged = finish(true)
        val best = (if (merged.second < plain.second) merged else plain).first.sortedBy { it.freq }
        val fitted = best.map { Limits.clamp(Band(it.freq, it.gain, it.q)) }
        return Eq(fitted + Eq.FLAT.bands.drop(fitted.size)).withSafePreamp()
    }

    /** RMS distance (dB) between target and measurement + [eq] across the fit range: lower is closer. */
    fun error(measurement: Curve, target: Curve, eq: Eq): Double {
        val inside = fs.filter { it in lo..hi }.toDoubleArray()
        val bands = Response.curve(eq, inside)
        val diffs = inside.indices.map { target.at(inside[it]) - measurement.at(inside[it]) - bands[it] }
        val mean = diffs.average() // overall level doesn't matter, only shape
        return sqrt(diffs.sumOf { (it - mean) * (it - mean) } / diffs.size)
    }
}
