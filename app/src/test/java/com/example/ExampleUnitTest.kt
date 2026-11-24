package com.example

import com.example.model.RoyaltyFreeAudioCatalog
import com.example.model.VideoFilterPreset
import com.example.model.VideoPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun filterPreset_parsesValidAndDefaultsToOriginal() {
    assertEquals(VideoFilterPreset.NEON_CYBER, VideoFilterPreset.fromRaw("NEON_CYBER"))
    assertEquals(VideoFilterPreset.NOIR_MONO, VideoFilterPreset.fromRaw("NOIR_MONO"))
    assertEquals(VideoFilterPreset.ORIGINAL, VideoFilterPreset.fromRaw("UNKNOWN"))
    assertEquals(VideoFilterPreset.ORIGINAL, VideoFilterPreset.fromRaw(null))
  }

  @Test
  fun videoPrivacy_parsesValidAndDefaultsToPublic() {
    assertEquals(VideoPrivacy.FOLLOWERS, VideoPrivacy.fromRaw("FOLLOWERS"))
    assertEquals(VideoPrivacy.PRIVATE, VideoPrivacy.fromRaw("PRIVATE"))
    assertEquals(VideoPrivacy.PUBLIC, VideoPrivacy.fromRaw("BAD_VALUE"))
  }

  @Test
  fun royaltyFreeCatalog_containsOriginalAndLicensedTracks() {
    assertTrue(RoyaltyFreeAudioCatalog.tracks.size >= 4)
    assertEquals("original_sound", RoyaltyFreeAudioCatalog.tracks.first().id)
    assertTrue(RoyaltyFreeAudioCatalog.tracks.drop(1).all { it.url.startsWith("https://") })
  }
}
