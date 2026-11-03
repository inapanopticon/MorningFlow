package com.morningflow.app.core.wakeup

/** Controls only the currently running wake-detection Service, never future Alarms. */
fun interface WakeDetectionRuntimeController {
    /**
     * Requests that the current detection run stop.
     *
     * Returns `true` when Android found a running Service to stop. A `false` result is a safe
     * terminal state: no run was active or the platform rejected the request. Callers must not
     * use this result to undo a Session that has already started.
     */
    fun stopCurrentRun(): Boolean
}
