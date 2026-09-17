package com.catguard.monitoring

/**
 * States of the deterrent state machine.
 *
 * IDLE -> MONITORING -> POSSIBLE_CAT -> CONFIRMED_CAT -> BARKING -> COOLDOWN -> MONITORING
 */
enum class GuardState {
    /** Guard is not running. */
    IDLE,

    /** Running, nothing that looks like a cat in the detection zone. */
    MONITORING,

    /** A cat was seen in at least one recent frame, but temporal confirmation has not passed. */
    POSSIBLE_CAT,

    /** Temporal confirmation passed: we believe a cat is really there. */
    CONFIRMED_CAT,

    /** The deterrent was just triggered (a short display state at the head of the cooldown). */
    BARKING,

    /** Deterrent recently fired; it will not fire again until this expires. */
    COOLDOWN,
}
