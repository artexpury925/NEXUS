package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
    fun `read Nexus app name and creator links from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Nexus", context.getString(R.string.app_name))
        assertEquals("@arnold_60011", context.getString(R.string.creator_instagram_handle))
        assertTrue(context.getString(R.string.creator_youtube_url).contains("@arnold60011"))
    }
}
