package com.wellmosphere.freedsp.store

import com.wellmosphere.freedsp.eq.Band
import com.wellmosphere.freedsp.eq.Eq
import com.wellmosphere.freedsp.eq.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileBackupTest {
    private val rock = Profile("Rock", Presets.named("Rock")!!.eq)
    private val mine = Profile("Mine", Eq.FLAT.withBand(4, Band(909.0, -2.4, 1.2)).withSafePreamp())

    @Test fun roundTripsExactly() {
        assertEquals(listOf(rock, mine), ProfileBackup.decode(ProfileBackup.encode(listOf(rock, mine))))
    }

    @Test fun restoringSkipsWhatYouHaveAndRenamesClashes() {
        val changed = mine.copy(eq = Presets.named("Jazz")!!.eq)
        val (added, skipped) = ProfileBackup.merge(listOf(rock, changed), listOf(rock, mine, Profile("New", Eq.FLAT)))
        assertEquals(1, skipped)
        assertEquals(listOf("Mine 2", "New"), added.map { it.name })
        assertEquals(mine.eq, added[0].eq)
    }

    @Test fun otherFilesAreRefused() {
        for (text in listOf("not json", "{}", """{"format":"something-else","profiles":[]}"""))
            assertThrows(IllegalArgumentException::class.java) { ProfileBackup.decode(text) }
    }

    @Test fun handEditedValuesAreBroughtIntoRange() {
        val text = ProfileBackup.encode(listOf(mine)).replace("-2.4", "-40")
        assertEquals(-12.0, ProfileBackup.decode(text)[0].eq.bands[4].gainDb, 0.0)
    }
}
