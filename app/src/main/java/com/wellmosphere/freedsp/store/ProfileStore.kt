package com.wellmosphere.freedsp.store

import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.FilterType
import com.wellmosphere.freedsp.eq.Limits
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Profile(val name: String, val eq: Eq)

/**
 * Profiles (in the order they were made), which one is active, the applied preset (which wins over the active
 * profile while set, with any edits to it in [presetEq]), the editor mode, and the exact EQ last
 * written to the cable. A read only returns whole-dB labels and no preamp, so [lastWritten] is how the
 * app knows the cable is playing a profile exactly. One JSON file, replaced through a temp file.
 */
class ProfileStore(dir: File) {
    private val file = File(dir, "profiles.json")
    var profiles: List<Profile> = emptyList(); private set
    var active: String? = null; private set
    var preset: String? = null; private set
    var presetEq: Eq? = null; private set
    var mode: String? = null; private set
    var lastWritten: Eq? = null; private set
    var verifiedWrites = 0; private set

    init {
        runCatching {
            val root = JSONObject(file.readText())
            profiles = root.getJSONArray("profiles").let { a -> (0 until a.length()).map { EqJson.profile(a.getJSONObject(it)) } }
            active = root.optString("active").ifEmpty { null }
            preset = root.optString("preset").ifEmpty { null }
            presetEq = root.optJSONObject("presetEq")?.let(EqJson::read)
            mode = root.optString("mode").ifEmpty { null }
            lastWritten = root.optJSONObject("lastWritten")?.let(EqJson::read)
            verifiedWrites = root.optInt("verifiedWrites")
        }
    }

    /** Adds a profile, or replaces the one with the same name in place. */
    @Synchronized fun put(name: String, eq: Eq) {
        profiles = if (profiles.any { it.name == name }) profiles.map { if (it.name == name) Profile(name, eq) else it }
            else profiles + Profile(name, eq)
        persist()
    }
    @Synchronized fun rename(from: String, to: String) {
        profiles = profiles.map { if (it.name == from) it.copy(name = to) else it }
        if (active == from) active = to
        persist()
    }
    @Synchronized fun delete(name: String) { profiles = profiles.filter { it.name != name }; persist() }
    /** Making a profile active leaves the preset. */
    @Synchronized fun setActive(name: String) { active = name; preset = null; presetEq = null; persist() }
    @Synchronized fun setPreset(name: String, eq: Eq) { preset = name; presetEq = eq; persist() }
    @Synchronized fun setMode(value: String) { mode = value; persist() }
    @Synchronized fun written(eq: Eq) { lastWritten = eq; verifiedWrites++; persist() }

    private fun persist() {
        val root = JSONObject()
            .put("profiles", JSONArray(profiles.map(EqJson::profileJson)))
            .put("active", active ?: "").put("mode", mode ?: "")
            .put("preset", preset ?: "").put("presetEq", presetEq?.let(EqJson::write) ?: JSONObject.NULL)
            .put("lastWritten", lastWritten?.let(EqJson::write) ?: JSONObject.NULL)
            .put("verifiedWrites", verifiedWrites)
        val temp = File(file.parentFile, "profiles.tmp")
        temp.writeText(root.toString(2))
        check(temp.renameTo(file)) { "Couldn't save profiles" }
    }

}


/** EQs and profiles as JSON, shared by the store and backups. */
internal object EqJson {
    fun write(eq: Eq): JSONObject = JSONObject().put("preampDb", eq.preampDb).put("bands", JSONArray(eq.bands.map {
        JSONObject().put("freqHz", it.freqHz).put("gainDb", it.gainDb).put("q", it.q).put("type", it.type.name)
    }))
    fun read(o: JSONObject): Eq {
        val a = o.getJSONArray("bands")
        val bands = (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Band(it.getDouble("freqHz"), it.getDouble("gainDb"), it.getDouble("q"), FilterType.valueOf(it.getString("type")))
        } }
        return Eq(bands, o.getDouble("preampDb"))
    }
    /** Profiles saved under wider, older limits (or edited by hand) are brought into the cable's range as they load. */
    fun profile(o: JSONObject) = Profile(o.getString("name"), read(o.getJSONObject("eq")).let { e -> Eq(e.bands.map(Limits::clamp)).withSafePreamp() })
    fun profileJson(p: Profile): JSONObject = JSONObject().put("name", p.name).put("eq", write(p.eq))
}
