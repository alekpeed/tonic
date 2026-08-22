package com.tonic.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
 *
 * **Widened from "no permissions at all" to an allowlist, 2026-08-22.** The original closing assertion
 * was that no `<uses-permission>` tag of any kind appeared, which was exactly right while Phase 1 was
 * the only phase there was. Phase 3 adds an optional sung response, and docs/30-PHASE-3-SPEC.md §6.1
 * requires the microphone permission it needs.
 *
 * Rather than drop that assertion, it now names the one permission this app is allowed to declare —
 * which is **stricter** than the old form everywhere except that single entry. Before, a permission
 * outside the four named network ones would have failed only the blanket check; now every declared
 * permission has to be the expected one, so anything added by a future change fails here by name and
 * has to be argued for rather than noticed later.
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

        val declared =
            PERMISSION.findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("android.permission.RECORD_AUDIO"),
            declared,
            "the microphone is the only permission this app may declare - see docs/30-PHASE-3-SPEC.md §6.1",
        )
    }

    private companion object {
        /**
         * Matches the name of every `<uses-permission>` in the source manifest.
         *
         * Deliberately not anchored to a single line or to attribute order beyond the one form this
         * file actually uses: the point is to catch a permission that someone added, and a regex so
         * strict that a reformatted tag slips past it would report an empty set and pass.
         */
        val PERMISSION = Regex("<uses-permission\\s+android:name=\"([^\"]+)\"")
    }
}
