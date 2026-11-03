package com.morningflow.app.core.sensor.squat

import kotlin.math.abs

/**
 * Squat counting: "down, bottom, up, stop". A squat is counted only when the whole
 * cycle has been seen, and motions far too violent to be a squat are rejected.
 *
 * Pure Kotlin (no Android). [com.morningflow.app.core.sensor.SquatDetector] feeds it
 * live sensor data; unit tests feed it synthetic signals and real recordings.
 *
 * ## Signal
 *
 * Linear acceleration projected onto the gravity vector ([MotionSample.verticalUp]),
 * then smoothed. Positive = accelerating upward. Independent of how the phone is
 * held; horizontal motion projects to ~0.
 *
 * ## One squat on this signal
 *
 *   ① start going down        -> negative dip
 *   ② bottom: brake + push up -> positive bump (two bumps if the user pauses at the bottom)
 *   ③ stop at the top         -> negative dip   <- counted here, after a short confirmation
 *
 * Counting at ③ (not at ②) means squatting down without standing up is not counted,
 * and neither is a bounce after standing up: neither is followed by a real stop at the top.
 *
 * ## State machine
 *
 *   IDLE       --(v < -downThreshold)---------------------------------> DESCENDING
 *   DESCENDING --(v > upThreshold, >= minDescentMs since start)-------> BOTTOM
 *              --(v > upThreshold, too soon)--------------------------> IDLE (jolt)
 *              --(no downward motion for maxBottomWaitMs)-------------> IDLE
 *   BOTTOM     --(v drops back below upThreshold)---------------------> RISING (bump finished)
 *   RISING     --(v < -topStopThreshold)------------------------------> CONFIRMING
 *                (if the user paused at the bottom, a new push-up bump must come first)
 *              --(no top stop within maxRiseMs)-----------------------> IDLE (never stood up)
 *   CONFIRMING --(confirmMs passed, nothing violent, not falling)-----> IDLE, count += 1
 *
 *   From any phase except REJECTED:
 *              --(in the air: v < -freefallLevel for >= freefallMinMs)-> REJECTED (jump)
 *              --(v > maxUpPeak OR v < -maxDownDepth)-----------------> REJECTED (violent swing/shake)
 *   REJECTED   --(calm for rejectQuietMs)-----------------------------> IDLE
 *
 * Why CONFIRMING: the stop at the top and the start of a jump's flight (or of hard
 * shaking) look identical for the first ~0.1 s — both are a downward swing crossing
 * -topStopThreshold. Holding the count briefly lets the jump/violence checks veto it.
 *
 * A counted cycle returns to IDLE so unrelated later pulses cannot inherit its descent.
 *
 * Why a free-fall DURATION for jumps: with feet on the ground the body can never
 * accelerate downward at g, so a squat dips below -6 m/s² only briefly while braking;
 * a jump spends ~0.3 s in the air close to -g.
 */
class DirectionalSquatAlgorithm(val params: Params = Params()) {

    /**
     * Thresholds. Values measured on three real recordings (HUAWEI ALN-AL00, phone held
     * in both hands at chest height, one person) are noted in brackets.
     */
    data class Params(
        /** m/s². Vertical below -this = "started moving down". */
        val downThreshold: Float = 0.8f,
        /** m/s². Vertical above +this after a descent = the bottom bump. (Squat bumps: 3.6..10.2) */
        val upThreshold: Float = 2.0f,
        /** m/s². Vertical below -this after the bottom = "stopping at the top". (Squat stops: -2.9..-7.4) */
        val topStopThreshold: Float = 2.0f,
        /** m/s². Instant upper limit; above this is a violent swing or shake. (Squats: <= +10.2) */
        val maxUpPeak: Float = 16.0f,
        /** m/s². Instant lower limit; below -this is a violent swing or shake. (Squats: >= -7.4) */
        val maxDownDepth: Float = 12.0f,
        /** m/s². Vertical below -this counts as falling freely (feet off the ground). */
        val freefallLevel: Float = 6.0f,
        /** Below -freefallLevel for this long = in the air = a jump. (Squats: <= 160 ms, jumps: >= 280 ms) */
        val freefallMinMs: Long = 220L,
        /** A real descent takes time; a down->up flip faster than this is a jolt. */
        val minDescentMs: Long = 250L,
        /** Longest wait after the last downward motion for the bottom bump. */
        val maxBottomWaitMs: Long = 5000L,
        /** Longest wait after the bottom bump for the stop at the top (covers a pause at the bottom). */
        val maxRiseMs: Long = 7000L,
        /** m/s². |vertical| below this counts as calm. */
        val calmThreshold: Float = 1.0f,
        /** Hold a detected top stop this long before counting it, so a jump/shake can veto it. */
        val confirmMs: Long = 250L,
        /** Calm this long while RISING = paused at the bottom; a new push up is then required.
         *  (Calm gap inside an unpaused squat: <= 460 ms; real pauses: ~3 s) */
        val pauseCalmMs: Long = 1000L,
        /** Calm this long after a rejected motion = back to IDLE. */
        val rejectQuietMs: Long = 600L,
        /** Moving-average window in samples (~100 ms at SENSOR_DELAY_GAME's ~50 Hz). */
        val smoothWindow: Int = 5,
        /** Hysteresis ignores small fluctuations around zero when timing raw reversals. */
        val reversalThreshold: Float = 2.0f,
        val maxShakeReversalMs: Long = 250L,
        val shakeReversalCount: Int = 1,
        val shakeWindowMs: Long = 1800L,
        val shakeWindowReversals: Int = 5,
    )

    enum class Phase { IDLE, DESCENDING, BOTTOM, RISING, CONFIRMING, REJECTED }

    private val smoother = MovingAverage(params.smoothWindow)
    private var descentStartMs = 0L
    private var lastDownMs = 0L
    private var bumpEndMs = 0L
    private var pendingSinceMs = 0L
    private var calmSinceMs = -1L
    private var freefallSinceMs = -1L
    private var needPush = false
    private var lastDirection = 0
    private var directionSinceMs = 0L
    private var rapidReversals = 0
    private val reversalTimes = ArrayDeque<Long>()

    /** Current state-machine phase (for logging / debugging). */
    var phase: Phase = Phase.IDLE
        private set

    /** Squats counted since the last [reset]. */
    var count: Int = 0
        private set

    /** The smoothed vertical acceleration of the last sample (for logging / debugging). */
    var lastSignal: Float = 0f
        private set

    /** Last rejection cause, retained until motion state is reset. */
    var lastRejection: String = "NONE"
        private set

    fun onSample(sample: MotionSample) {
        val raw = sample.verticalUp
        val v = smoother.add(raw)
        lastSignal = v
        val now = sample.tMs
        val p = params

        // How long the signal has been calm (used by RISING and REJECTED).
        calmSinceMs = if (abs(v) < p.calmThreshold) {
            if (calmSinceMs < 0) now else calmSinceMs
        } else {
            -1L
        }

        // How long we have been "in the air" (sustained near-free-fall).
        freefallSinceMs = if (v < -p.freefallLevel) {
            if (freefallSinceMs < 0) now else freefallSinceMs
        } else {
            -1L
        }
        val inTheAir = freefallSinceMs >= 0 && now - freefallSinceMs >= p.freefallMinMs
        val direction = when {
            raw > p.reversalThreshold -> 1
            raw < -p.reversalThreshold -> -1
            else -> 0
        }
        if (direction != 0 && direction != lastDirection) {
            val interval = now - directionSinceMs
            rapidReversals = if (lastDirection != 0 && interval <= p.maxShakeReversalMs) rapidReversals + 1 else 0
            if (lastDirection != 0) reversalTimes.addLast(now)
            lastDirection = direction
            directionSinceMs = now
        }
        if (now - directionSinceMs > p.maxShakeReversalMs) rapidReversals = 0
        while (reversalTimes.isNotEmpty() && now - reversalTimes.first() > p.shakeWindowMs) reversalTimes.removeFirst()
        val shaking = rapidReversals >= p.shakeReversalCount || reversalTimes.size >= p.shakeWindowReversals

        // Not a squat, whatever phase we are in: a jump, or a violent swing/shake.
        val extremeMotion = v > p.maxUpPeak || v < -p.maxDownDepth
        if (phase != Phase.REJECTED && (inTheAir || shaking || extremeMotion)) {
            lastRejection = when {
                shaking -> "RAPID_REVERSALS"
                inTheAir -> "FREEFALL"
                else -> "SMOOTH_EXTREME"
            }
            phase = Phase.REJECTED
            return
        }

        when (phase) {
            Phase.IDLE -> {
                if (v < -p.downThreshold) {
                    phase = Phase.DESCENDING
                    descentStartMs = now
                    lastDownMs = now
                }
            }
            Phase.DESCENDING -> {
                if (v < -p.downThreshold) lastDownMs = now
                when {
                    v > p.upThreshold && now - descentStartMs >= p.minDescentMs -> phase = Phase.BOTTOM
                    v > p.upThreshold -> phase = Phase.IDLE // flipped too fast: jolt/shake
                    now - lastDownMs > p.maxBottomWaitMs -> phase = Phase.IDLE
                }
            }
            Phase.BOTTOM -> {
                if (v < p.upThreshold) {
                    phase = Phase.RISING
                    bumpEndMs = now
                    needPush = false
                }
            }
            Phase.RISING -> {
                // Paused at the bottom (calm for a while)? Then the user must push up again
                // (another positive bump) before a top stop is accepted; otherwise the
                // downward start of the NEXT movement would be mistaken for the stop.
                if (calmSinceMs >= 0 && now - calmSinceMs >= p.pauseCalmMs) needPush = true
                if (needPush && v > p.upThreshold) needPush = false
                when {
                    v < -p.topStopThreshold && !needPush -> {
                        phase = Phase.CONFIRMING
                        pendingSinceMs = now
                    }
                    now - bumpEndMs > p.maxRiseMs -> phase = Phase.IDLE // squatted down, never stood up
                }
            }
            Phase.CONFIRMING -> {
                // Reaching here means nothing violent happened on this sample (the check
                // above would have moved us to REJECTED). Never confirm while still in a
                // deep dip: it might be a jump's flight, which proves itself only after
                // freefallMinMs.
                val stillFalling = freefallSinceMs >= 0
                if (now - pendingSinceMs >= p.confirmMs && !stillFalling) {
                    count++
                    // A new cycle must establish its own downward start.
                    phase = Phase.IDLE
                }
            }
            Phase.REJECTED -> {
                if (calmSinceMs >= 0 && now - calmSinceMs >= p.rejectQuietMs) phase = Phase.IDLE
            }
        }
    }

    /**
     * Forget any half-finished movement but keep [count]. Used when observation resumes
     * after a pause: the time gap makes the old state meaningless.
     */
    fun clearMotionState() {
        smoother.reset()
        phase = Phase.IDLE
        descentStartMs = 0L
        lastDownMs = 0L
        bumpEndMs = 0L
        pendingSinceMs = 0L
        calmSinceMs = -1L
        freefallSinceMs = -1L
        needPush = false
        lastDirection = 0
        directionSinceMs = 0L
        rapidReversals = 0
        reversalTimes.clear()
        lastRejection = "NONE"
        lastSignal = 0f
    }

    /** Back to a fresh start: no movement state, count = 0. */
    fun reset() {
        clearMotionState()
        count = 0
    }
}
