package com.wellmosphere.freedsp.store

import android.content.Context
import com.wellmosphere.freedsp.eq.Curve
import java.io.File

/**
 * IEM measurements and target curves. Imported files are kept as text (so they re-parse exactly) in private
 * storage; built-in targets ship in assets/targets (AutoEq, MIT). The picked pair is remembered.
 */
class CurveStore(private val context: Context) {
    enum class Kind { MEASUREMENT, TARGET }

    private val prefs = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
    private fun dir(kind: Kind) = File(context.filesDir, "curves/${kind.name.lowercase()}").apply { mkdirs() }

    val builtInTargets: List<Curve> = context.assets.list("targets").orEmpty().sorted().map { file ->
        Curve.parse(file.removeSuffix(".csv"), context.assets.open("targets/$file").bufferedReader().use { it.readText() })
    }

    fun list(kind: Kind): List<Curve> {
        val imported = dir(kind).listFiles().orEmpty().sortedBy { it.name.lowercase() }
            .mapNotNull { f -> runCatching { Curve.parse(f.nameWithoutExtension, f.readText()) }.getOrNull() }
        return if (kind == Kind.TARGET) builtInTargets + imported.filter { i -> builtInTargets.none { it.name == i.name } } else imported
    }

    fun isBuiltIn(name: String) = builtInTargets.any { it.name == name }
    /** A line on how a built-in target sounds; imported curves have none. */
    fun about(name: String): String? = ABOUT[name]

    /** Validates by parsing first; a file with the same name is replaced. */
    fun add(kind: Kind, name: String, text: String): Curve {
        val clean = name.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().ifEmpty { "Imported" }.take(48)
        val curve = Curve.parse(clean, text)
        File(dir(kind), "$clean.txt").writeText(text)
        return curve
    }

    fun delete(kind: Kind, name: String) { File(dir(kind), "$name.txt").delete() }

    private companion object {
        val ABOUT = mapOf(
            "Harman in-ear 2019" to "The most popular target. Warm bass, clear vocals.",
            "Harman in-ear 2019 without bass" to "Harman 2019 with flat bass, for less low end.",
            "Harman in-ear 2017-2" to "Earlier Harman. More bass and upper-mid bite.",
            "AutoEq in-ear" to "Gentle bass and a smoother, easier treble.",
            "Diffuse field ISO 11904-1" to "Classic neutral. Flat bass, open and revealing.",
            "oratory1990 in-ear" to "Strong bass shelf, calmer upper mids than Harman.",
        )
    }

    /** Where auto-fit stops, in Hz. */
    var fitUpTo: Double
        get() = prefs.getFloat("fitUpTo", 8_000f).toDouble()
        set(v) = prefs.edit().putFloat("fitUpTo", v.toFloat()).apply()

    fun selected(kind: Kind): String? = prefs.getString(kind.name, null)
    fun select(kind: Kind, name: String?) = prefs.edit().putString(kind.name, name).apply()
}
