package com.wellmosphere.freedsp.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * Every profile in one file, to move them to a new phone or keep them through a reinstall. Restoring never
 * replaces anything: profiles you already have are skipped, and a different one with a taken name gets a new name.
 */
object ProfileBackup {
    private const val FORMAT = "freedsp-android-profiles"

    fun encode(profiles: List<Profile>): String = JSONObject()
        .put("format", FORMAT).put("version", 1)
        .put("profiles", JSONArray(profiles.map(EqJson::profileJson)))
        .toString(2)

    fun decode(text: String): List<Profile> {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root?.optString("format") == FORMAT) { "That isn't a FreeDSP profiles backup" }
        val a = root!!.getJSONArray("profiles")
        return (0 until a.length()).map { EqJson.profile(a.getJSONObject(it)) }
    }

    /** What to add to [existing] from [backup], and how many were already there. */
    fun merge(existing: List<Profile>, backup: List<Profile>): Pair<List<Profile>, Int> {
        val names = existing.map { it.name }.toMutableSet()
        var skipped = 0
        val added = backup.mapNotNull { p ->
            if (existing.any { it.name == p.name && it.eq.soundsLike(p.eq) }) { skipped++; return@mapNotNull null }
            val name = generateSequence(1) { it + 1 }.map { if (it == 1) p.name else "${p.name} $it" }.first { it !in names }
            names += name
            p.copy(name = name)
        }
        return added to skipped
    }
}
