package io.github.jlojkasek.ketape

import io.hammerhead.karooext.models.RideState

/** A running ride first seen after startup cannot include earlier samples. */
internal class RideContinuity {
    private var state: RideState? = null
    @Volatile var incomplete = false
        private set

    fun observe(next: RideState) {
        if (next == RideState.Idle) incomplete = false
        else if (state == null) incomplete = true
        state = next
    }
}
