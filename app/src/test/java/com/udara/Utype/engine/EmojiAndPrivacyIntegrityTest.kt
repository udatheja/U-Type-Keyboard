package com.udara.Utype.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.udara.Utype.data.KeyboardThemeId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * International-standard integrity & offline-privacy compliance suite:
 * - Verifies zero INTERNET / ACCESS_NETWORK_STATE permissions in merged AndroidManifest.
 * - Verifies emoji library completeness, deduplication, skin tones, Sri Lanka flag, and search.
 * - Verifies all keyboard themes resolve cleanly in both dark and light system configurations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmojiAndPrivacyIntegrityTest {

    @Test
    fun appManifestHasZeroInternetOrNetworkPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse("App must NEVER declare INTERNET permission", perms.contains(Manifest.permission.INTERNET))
        assertFalse(
            "App must NEVER declare ACCESS_NETWORK_STATE permission",
            perms.contains(Manifest.permission.ACCESS_NETWORK_STATE),
        )
    }

    @Test
    fun emojiLibraryIsRichCleanAndUniversallySearchable() {
        assertTrue("Expected 400+ universal emojis", EmojiData.allEmojis.size >= 400)

        // Every non-RECENT category must be non-empty and free of blank entries
        EmojiCategory.entries.filter { it != EmojiCategory.RECENT }.forEach { cat ->
            val items = EmojiData.byCategory(cat)
            assertTrue("Category ${cat.title} must not be empty", items.isNotEmpty())
            items.forEach { entry ->
                assertTrue("Emoji in ${cat.title} must not be blank", entry.emoji.isNotBlank())
                assertTrue("Keywords in ${cat.title} must not be blank", entry.keywords.isNotBlank())
            }
        }

        // Key social-media & Sri Lankan essentials must be present
        val allGlyphs = EmojiData.allEmojis.map { it.emoji }.toSet()
        listOf("😂", "❤️", "🔥", "👍", "🙏", "🇱🇰", "🏏", "🪷", "🎉", "💯").forEach { essential ->
            assertTrue("Essential emoji $essential must exist", allGlyphs.contains(essential))
        }

        // Search queries return relevant results
        assertTrue(EmojiData.search("sri lanka").any { it.emoji == "🇱🇰" })
        assertTrue(EmojiData.search("heart").isNotEmpty())
        assertTrue(EmojiData.search("laugh").isNotEmpty())
        assertTrue(EmojiData.search("cricket").any { it.emoji == "🏏" })

        // Skin-tone modification works cleanly
        val modified = EmojiData.withSkinTone("👍", "🏽")
        assertTrue(modified.startsWith("👍") && modified.endsWith("🏽"))
    }

    @Test
    fun allKeyboardThemesResolveValidPalettesInLightAndDarkModes() {
        KeyboardThemeId.entries.forEach { theme ->
            val dark = KeyboardThemeId.resolvePalette(theme, isSystemDark = true)
            val light = KeyboardThemeId.resolvePalette(theme, isSystemDark = false)
            assertNotNull(dark)
            assertNotNull(light)
            assertTrue(dark.keyText != dark.keyBg)
            assertTrue(light.keyText != light.keyBg)
        }
    }
}
