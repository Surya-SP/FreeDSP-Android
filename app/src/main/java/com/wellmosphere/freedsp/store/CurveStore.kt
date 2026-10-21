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

    /** Validates by parsing first; a file with the same name is replaced. */
    fun add(kind: Kind, name: String, text: String): Curve {
        val clean = name.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().ifEmpty { "Imported" }.take(48)
        val curve = Curve.parse(clean, text)
        File(dir(kind), "$clean.txt").writeText(text)
        return curve
    }

    fun delete(kind: Kind, name: String) { File(dir(kind), "$name.txt").delete() }

    fun selected(kind: Kind): String? = prefs.getString(kind.name, null)
    fun select(kind: Kind, name: String?) = prefs.edit().putString(kind.name, name).apply()
}
