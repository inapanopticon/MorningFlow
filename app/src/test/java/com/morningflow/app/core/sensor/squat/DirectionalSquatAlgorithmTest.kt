package com.morningflow.app.core.sensor.squat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Two kinds of tests:
 *
 * 1. Synthetic signals (see [SyntheticMotion]) prove the state machine's LOGIC:
 *    pauses, back-to-back squats, down-only, bounces, jumps, shaking, phone orientation.
 * 2. Real recordings (src/test/resources/squat) prove the THRESHOLDS work on real
 *    data. Each file was recorded with the SquatLab test app; the true squat count
 *    was confirmed by the person who recorded it. If a threshold change alters these
 *    results, the change needs new evidence, not a new expected number.
 */
class DirectionalSquatAlgorithmTest {

    private fun algorithm() = DirectionalSquatAlgorithm()

    @Test
    fun replayLocalDiagnosticLogsWhenPresent() {
        val pattern = Regex("t_ms=(\\d+).*lin_x=([^ ]+) lin_y=([^ ]+) lin_z=([^ ]+) grav_x=([^ ]+) grav_y=([^ ]+) grav_z=([^ ]+)")
        for (name in listOf("squat-five-then-shake.log", "squat-ten-fast-20261012-141034.log",
            "squat-shake-timing-20261012-152720.log", "squat-fast-comparison-20261012-153315.log")) {
            val file = listOf(java.io.File("tmp/squat-analysis/$name"),
                java.io.File("../tmp/squat-analysis/$name")).firstOrNull { it.exists() } ?: continue
            if (!file.exists()) continue
            val filtered = algorithm()
            file.forEachLine { line ->
                val m = pattern.find(line) ?: return@forEachLine
                val c = m.groupValues
                val sample = MotionSample(c[1].toLong(), c[2].toFloat(), c[3].toFloat(),
                    c[4].toFloat(), c[5].toFloat(), c[6].toFloat(), c[7].toFloat())
                filtered.onSample(sample)
                if (name == "squat-five-then-shake.log" && line.contains("phase_before=CONFIRMING phase=DESCENDING count=5")) {
                    assertEquals(5, filtered.count)
                }
            }
            println("REPLAY $name count=${filtered.count}")
        }
    }

    // -- Normal squats ---------------------------------------------------------

    @Test
    fun countsOneNormalSquat() {
        val m = SyntheticMotion().still(1000).squat().still(1500)
        assertEquals(1, m.countWith(algorithm()))
    }

    @Test
    fun countsFiveBackToBackSquats() {
        val m = SyntheticMotion().still(1000)
        repeat(5) { m.squat() }
        m.still(1500)
        assertEquals(5, m.countWith(algorithm()))
    }

    @Test
    fun countsFiveSquatsWithShortRests() {
        val m = SyntheticMotion().still(1000)
        repeat(5) { m.squat().still(600) }
        m.still(1000)
        assertEquals(5, m.countWith(algorithm()))
    }

    @Test
    fun countsSquatsSeparatedByLongRests() {
        val m = SyntheticMotion().still(1000)
        repeat(3) { m.squat().still(4500) }
        assertEquals(3, m.countWith(algorithm()))
    }

    @Test
    fun countsASquatWithAVeryStrongStandUp() {
        val m = SyntheticMotion().still(1000).squat(ascentMs = 500, ascentAccel = 11.5f).still(1500)
        assertEquals(1, m.countWith(algorithm()))
    }

    @Test
    fun resultDoesNotDependOnHowThePhoneIsHeld() {
        val holds = listOf(
            floatArrayOf(0f, 1f, 0f),       // upright
            floatArrayOf(0f, 0f, 1f),       // flat, face up
            floatArrayOf(0.3f, 0.7f, 0.6f), // tilted
            floatArrayOf(1f, 0f, 0f),       // landscape
        )
        for (up in holds) {
            val m = SyntheticMotion(up = up).still(1000)
            repeat(3) { m.squat(pauseMs = if (it == 1) 1500 else 0) }
            m.still(1500)
            assertEquals("hold=${up.toList()}", 3, m.countWith(algorithm()))
        }
    }

    // -- Pauses and partial movements -----------------------------------------

    @Test
    fun countsOneSquatWithATwoSecondPauseAtTheBottom() {
        val m = SyntheticMotion().still(1000).squat(pauseMs = 2000).still(1500)
        assertEquals(1, m.countWith(algorithm()))
    }

    @Test
    fun countsOneSquatWhenTheBrakeAtTheBottomIsTooGentleToTrigger() {
        val m = SyntheticMotion().still(1000)
            .squat(descentAccel = 1.5f, pauseMs = 1500, ascentAccel = 5.5f).still(1500)
        assertEquals(1, m.countWith(algorithm()))
    }

    @Test
    fun doesNotCountSquattingDownWithoutStandingUp() {
        val m = SyntheticMotion().still(1000).squatDownOnly().still(9000)
        assertEquals(0, m.countWith(algorithm()))
    }

    @Test
    fun countsADownOnlyFollowedLaterByAFullSquatOnce() {
        val m = SyntheticMotion().still(1000).squatDownOnly().still(9000).squat().still(1500)
        assertEquals(1, m.countWith(algorithm()))
    }

    @Test
    fun doesNotDoubleCountABounceAfterStandingUp() {
        val m = SyntheticMotion().still(1000).squat().bounce().still(2000).squat().bounce().still(2000)
        assertEquals(2, m.countWith(algorithm()))
    }

    @Test
    fun addsNothingWhileStandingStillAfterASquat() {
        val m = SyntheticMotion().still(1000).squat().still(10_000)
        assertEquals(1, m.countWith(algorithm()))
    }

    // -- Things that are not squats -------------------------------------------

    @Test
    fun doesNotCountJumps() {
        val m = SyntheticMotion().still(1000)
        repeat(3) { m.jump().still(1500) }
        assertEquals(0, m.countWith(algorithm()))
    }

    @Test
    fun doesNotCountASmallJumpWhosePeaksStayBelowTheAmplitudeLimits() {
        // take-off +10 and landing +11 are below maxUpPeak; only the ~0.3 s in the air gives it away
        val m = SyntheticMotion().still(1000)
            .jump(takeoff = 10f, airMs = 300, airAccel = -9.0f, landing = 11f).still(1500)
        assertEquals(0, m.countWith(algorithm()))
    }

    @Test
    fun countsSquatsBetweenJumpsCorrectly() {
        val m = SyntheticMotion().still(1000).squat().still(1000).jump().still(1500).squat().still(1500)
        assertEquals(2, m.countWith(algorithm()))
    }

    @Test
    fun ignoresSideToSideShaking() {
        val m = SyntheticMotion().still(1000).horizontalShake(4000).still(1000)
        assertEquals(0, m.countWith(algorithm()))
    }

    @Test
    fun ignoresFastVerticalShaking() {
        val m = SyntheticMotion().still(1000).verticalShake(4000).still(1000)
        assertEquals(0, m.countWith(algorithm()))
    }

    @Test
    fun doesNotCountTheFirstSwingOfViolentShakingRightAfterASquat() {
        val m = SyntheticMotion().still(1000).squat().still(1500)
            .bounce(accel = 4f, ms = 160)   // moderate upward jerk (< maxUpPeak)
            .bounce(accel = -30f, ms = 200) // huge downward swing
            .verticalShake(2000, amplitude = 30f, hz = 3f)
            .still(1500)
        assertEquals(1, m.countWith(algorithm()))
    }

    // -- Lifecycle -------------------------------------------------------------

    @Test
    fun singleImpactDoesNotRejectMotion() {
        for (impact in listOf(20f, -14f)) {
            val a = algorithm()
            SyntheticMotion().still(1000).samples.forEach(a::onSample)
            a.onSample(MotionSample(1000, 0f, impact, 0f, 0f, 9.81f, 0f))
            org.junit.Assert.assertNotEquals(DirectionalSquatAlgorithm.Phase.REJECTED, a.phase)
            assertEquals(0, a.count)
        }
    }

    @Test
    fun repeatedRapidReversalsRejectMotion() {
        val a = algorithm()
        repeat(4) { i ->
            repeat(10) { j -> a.onSample(MotionSample(i * 200L + j * 20L,
                0f, if (i % 2 == 0) -4f else 4f, 0f, 0f, 9.81f, 0f)) }
        }
        assertEquals(0, a.count)
        assertEquals(DirectionalSquatAlgorithm.Phase.REJECTED, a.phase)
    }

    @Test
    fun clearMotionStateKeepsTheCountButForgetsAHalfFinishedSquat() {
        val a = algorithm()
        SyntheticMotion().still(1000).squat().still(1500).samples.forEach(a::onSample)
        assertEquals(1, a.count)

        // Half a squat (down only), then a pause/resume: the half squat must not complete later.
        val half = SyntheticMotion().still(500).squatDownOnly()
        half.samples.forEach(a::onSample)
        a.clearMotionState()
        assertEquals(1, a.count)
        assertEquals(DirectionalSquatAlgorithm.Phase.IDLE, a.phase)
    }

    @Test
    fun resetClearsTheCount() {
        val a = algorithm()
        SyntheticMotion().still(1000).squat().still(1500).samples.forEach(a::onSample)
        a.reset()
        assertEquals(0, a.count)
    }

    // -- Real recordings ---------------------------------------------------------

    /** 7 squats, then jumps, arm swings and violent shaking. */
    @Test
    fun countsSevenOnRealRecordingWithJumpsAndShaking() {
        assertEquals(7, countRecording("/squat/recording_20260927_203458_actual7.csv"))
    }

    /** 5 squats, jumps, arm swings, shaking, then a slow squat with a pause at the bottom.
     *  Includes a squat peaking at +10.2 m/s² and a jump reaching only +11.8 / -8.3. */
    @Test
    fun countsEightOnRealRecordingWithASlowPausedSquat() {
        assertEquals(8, countRecording("/squat/recording_20260928_013852_actual8.csv"))
    }

    private fun countRecording(resource: String, params: DirectionalSquatAlgorithm.Params = DirectionalSquatAlgorithm.Params()): Int {
        val lines = requireNotNull(javaClass.getResourceAsStream(resource)) { "missing $resource" }
            .bufferedReader().readLines().filterNot { it.startsWith("#") }
        val header = lines.first().split(",")
        fun col(name: String) = header.indexOf(name)
        val t = col("t_ms")
        val lx = col("lin_x"); val ly = col("lin_y"); val lz = col("lin_z")
        val gx = col("grav_x"); val gy = col("grav_y"); val gz = col("grav_z")
        val a = DirectionalSquatAlgorithm(params)
        lines.drop(1).forEach { line ->
            val c = line.split(",")
            a.onSample(
                MotionSample(
                    c[t].toLong(),
                    c[lx].toFloat(), c[ly].toFloat(), c[lz].toFloat(),
                    c[gx].toFloat(), c[gy].toFloat(), c[gz].toFloat(),
                )
            )
        }
        return a.count
    }
}
