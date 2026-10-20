package com.wellmosphere.freedsp.store

import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.FilterType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Profile(val name: String, val eq: Eq)

/**
 * Saved profiles plus the exact EQ last written to the cable. A read only shows whole-dB labels and no preamp,
 * so when the cable's records match [lastWritten] the app shows that exact version instead.
 * One JSON file, replaced through a temp file so a crash can't leave half of it.
 */
class ProfileStore(dir: File) {
    private val file = File(dir, "profiles.json")
    var profiles: List<Profile> = emptyList(); private set
    var lastWritten: Eq? = null; private set
    var verifiedWrites = 0; private set

    init {
        runCatching {
            val root = JSONObject(file.readText())
            profiles = root.getJSONArray("profiles").let { a -> (0 until a.length()).map { profile(a.getJSONObject(it)) } }
            lastWritten = root.optJSONObject("lastWritten")?.let(::eq)
            verifiedWrites = root.optInt("verifiedWrites")
        }
    }

    fun save(name: String, eq: Eq) {
        profiles = (profiles.filter { it.name != name } + Profile(name, eq)).sortedBy { it.name.lowercase() }
        persist()
    }
    fun delete(name: String) { profiles = profiles.filter { it.name != name }; persist() }
    fun written(eq: Eq) { lastWritten = eq; verifiedWrites++; persist() }

    private fun persist() {
        val root = JSONObject()
            .put("profiles", JSONArray(profiles.map { JSONObject().put("name", it.name).put("eq", json(it.eq)) }))
            .put("lastWritten", lastWritten?.let(::json) ?: JSONObject.NULL)
            .put("verifiedWrites", verifiedWrites)
        val temp = File(file.parentFile, "profiles.tmp")
        temp.writeText(root.toString(2))
        check(temp.renameTo(file)) { "Couldn't save profiles" }
    }

    private fun json(eq: Eq) = JSONObject().put("preampDb", eq.preampDb).put("bands", JSONArray(eq.bands.map {
        JSONObject().put("freqHz", it.freqHz).put("gainDb", it.gainDb).put("q", it.q).put("type", it.type.name)
    }))
    private fun eq(o: JSONObject): Eq {
        val a = o.getJSONArray("bands")
        val bands = (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Band(it.getDouble("freqHz"), it.getDouble("gainDb"), it.getDouble("q"), FilterType.valueOf(it.getString("type")))
        } }
        return Eq(bands, o.getDouble("preampDb"))
    }
    private fun profile(o: JSONObject) = Profile(o.getString("name"), eq(o.getJSONObject("eq")))
}
