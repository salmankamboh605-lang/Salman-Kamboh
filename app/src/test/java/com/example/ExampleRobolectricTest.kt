package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.media.VideoEditorEngine
import com.example.model.VideoFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Salman Video", appName)
  }

  @Test
  fun `firebase config resources are populated`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbId = context.getString(R.string.firestore_database_id)
    assertTrue(dbId.isNotBlank() && dbId != "(default)")
  }

  @Test
  fun `video editor builds clip and effects for filter and overlay`() {
    val uri = Uri.parse("file:///tmp/sample.mp4")
    val clipped = VideoEditorEngine.buildClippedMediaItem(uri, trimStartMs = 1500L, trimEndMs = 6500L)
    assertEquals(1500L, clipped.clippingConfiguration.startPositionMs)
    assertEquals(6500L, clipped.clippingConfiguration.endPositionMs)

    val originalEffects = VideoEditorEngine.buildVideoEffects(VideoFilterPreset.ORIGINAL, "")
    assertTrue(originalEffects.isEmpty())

    val neonWithText = VideoEditorEngine.buildVideoEffects(VideoFilterPreset.NEON_CYBER, "Salman Studio")
    assertEquals(3, neonWithText.size)
  }
}
