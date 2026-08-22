package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §4.2 (Bluetooth is disqualifying), §4.3 (calibration is per route) and §9
 * simulation 6 ("Uncalibrated device: production must be blocked with an explanation, not silently
 * mis-scored").
 *
 * Simulation 6 is a phase-level acceptance criterion, and this is the half of it a JVM test can hold
 * to account: that the *policy* blocks. The other half — that a blocked learner reaches a screen and
 * reads why — needs the screen, which is Stage 4.5, and is recorded as owed rather than assumed.
 * docs/21-HANDOFF.md §4.1 is what that distinction cost the last time it was not drawn.
 */
class ProductionGateTest {
    private val allSlots = CalibrationSlot.entries.toSet()

    @Test
    fun `a calibrated speaker or wired route is ready`() {
        for (route in listOf(AudioOutputRoute.SPEAKER, AudioOutputRoute.WIRED, AudioOutputRoute.USB)) {
            assertEquals(
                ProductionReadiness.Ready,
                ProductionGate.evaluate(route, allSlots),
                "expected $route to allow production when calibrated",
            )
        }
    }

    @Test
    fun `bluetooth is blocked even when every slot is calibrated`() {
        // §4.2: not "less accurate" - uncalibratable. A stored constant from the speaker path says
        // nothing about a Bluetooth one, so having calibrated must not unlock this.
        val readiness = ProductionGate.evaluate(AudioOutputRoute.BLUETOOTH, allSlots)
        assertEquals(ProductionReadiness.Blocked(BlockReason.BLUETOOTH_OUTPUT), readiness)
    }

    @Test
    fun `an uncalibrated but usable route is blocked for calibration, not for the route`() {
        // Simulation 6. The distinction matters to the learner: one of these is fixed by tapping
        // through a calibration screen, the other by unplugging something.
        val readiness = ProductionGate.evaluate(AudioOutputRoute.SPEAKER, emptySet())
        assertEquals(ProductionReadiness.Blocked(BlockReason.NOT_CALIBRATED), readiness)
    }

    @Test
    fun `calibrating one route does not unlock the other`() {
        // §4.3: "Calibration is per output route, not global." A learner who calibrated on the speaker
        // and then plugged in headphones is uncalibrated again, and must be told so rather than scored
        // against the speaker's constant.
        val speakerOnly = setOf(CalibrationSlot.SPEAKER)
        assertEquals(ProductionReadiness.Ready, ProductionGate.evaluate(AudioOutputRoute.SPEAKER, speakerOnly))
        assertEquals(
            ProductionReadiness.Blocked(BlockReason.NOT_CALIBRATED),
            ProductionGate.evaluate(AudioOutputRoute.WIRED, speakerOnly),
        )
    }

    @Test
    fun `usb shares the wired constant`() {
        // The documented, unverified grouping on AudioOutputRoute.USB. Asserted so that if Stage 4.1
        // measures a USB device and finds the grouping wrong, this test fails and the decision gets
        // revisited rather than the mapping quietly drifting.
        val wiredOnly = setOf(CalibrationSlot.WIRED)
        assertEquals(ProductionReadiness.Ready, ProductionGate.evaluate(AudioOutputRoute.USB, wiredOnly))
    }

    @Test
    fun `an unread route blocks and is distinguishable from an unsupported one`() {
        assertEquals(
            ProductionReadiness.Blocked(BlockReason.ROUTE_UNKNOWN),
            ProductionGate.evaluate(AudioOutputRoute.UNKNOWN, allSlots),
        )
        assertEquals(
            ProductionReadiness.Blocked(BlockReason.UNSUPPORTED_ROUTE),
            ProductionGate.evaluate(AudioOutputRoute.OTHER, allSlots),
        )
    }

    @Test
    fun `every route is decided, and no route is both timeable and blocked`() {
        // The invariant RouteTiming exists to make structural. Asserted anyway, because "the type
        // enforces it" is a claim that stops being true the moment someone adds a case.
        for (route in AudioOutputRoute.entries) {
            when (val timing = route.timing) {
                is RouteTiming.Calibratable -> {
                    assertTrue(route.allowsProduction, "$route is calibratable but disallows production")
                    assertIs<ProductionReadiness.Ready>(
                        ProductionGate.evaluate(route, setOf(timing.slot)),
                        "$route should be ready once its own slot is calibrated",
                    )
                }

                is RouteTiming.Unusable -> {
                    assertFalse(route.allowsProduction, "$route is unusable but allows production")
                    assertEquals(
                        ProductionReadiness.Blocked(timing.reason),
                        ProductionGate.evaluate(route, allSlots),
                        "$route should block with its own reason regardless of calibration",
                    )
                }
            }
        }
    }

    @Test
    fun `no two unusable routes report the same reason`() {
        // Each reason is a different sentence to a learner and a different thing for them to do. If two
        // routes ever collapse onto one reason, one of those learners is being told the wrong thing.
        val reasons =
            AudioOutputRoute.entries
                .mapNotNull { (it.timing as? RouteTiming.Unusable)?.reason }
        assertEquals(reasons.size, reasons.toSet().size, "duplicate block reasons across routes: $reasons")
    }
}
