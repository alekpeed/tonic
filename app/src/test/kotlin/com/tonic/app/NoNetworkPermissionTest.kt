package com.tonic.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * docs/09-BUILD-PLAN.md Stage 10: "Verify no network permission in the manifest at all." Phase 1 is
 * fully offline (CLAUDE.md §1/§7) - a network permission appearing here, now or in a later change,
 * would mean some code path started assuming connectivity that isn't supposed to exist.
 *
 * Scans the source manifest, not a merged/built one: a merged manifest is a build *output*, and a JVM
 * unit test isn't reliably ordered after the task that produces it. The real gate against a dependency
 * merging in a permission transitively is `AndroidManifest.xml`'s own `tools:node="remove"` escape
 * hatch, which isn't needed here because no dependency in this project's version catalog is a network
 * library in the first place (verified by inspecting the actual merged manifest at Stage 10's build
 * time, and by the version catalog itself never declaring one).
 */
class NoNetworkPermissionTest {
    @Test
    fun `the manifest declares no network-related permission`() {
        val manifest = File("src/main/AndroidManifest.xml")
        check(manifest.exists()) { "expected ${manifest.absolutePath} to exist" }
        val text = manifest.readText()

        val forbidden =
            listOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.CHANGE_NETWORK_STATE",
            )
        for (permission in forbidden) {
            assertFalse(permission in text, "found forbidden permission '$permission' in the manifest")
        }
        assertFalse("<uses-permission" in text, "no <uses-permission> tag of any kind is expected in Phase 1")
    }
}
