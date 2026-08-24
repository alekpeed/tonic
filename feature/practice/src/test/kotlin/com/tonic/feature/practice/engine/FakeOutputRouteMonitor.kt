package com.tonic.feature.practice.engine

import com.tonic.core.audio.route.OutputRouteMonitor
import com.tonic.core.model.rhythm.AudioOutputRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where audio is going, in a test — docs/40-PHASE-4-SPEC.md §4.2.
 *
 * The real implementation reads `AudioManager` and is the one part of routing no JVM test can
 * exercise. Everything that acts on the answer is pure, which is what lets §9's simulation 6 be a
 * JVM test: hand it each route in turn and check what the app does.
 *
 * Mutable, because §4.3 requires acting on the *change* rather than on a value read once.
 */
class FakeOutputRouteMonitor(
    initial: AudioOutputRoute = AudioOutputRoute.SPEAKER,
) : OutputRouteMonitor {
    private val flow = MutableStateFlow(initial)
    override val route: StateFlow<AudioOutputRoute> = flow.asStateFlow()

    fun set(route: AudioOutputRoute) {
        flow.value = route
    }
}
