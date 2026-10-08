package com.egrmeister.watertiles.domain.entries

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Protects each Quick Add activation from duplicate processing: an activation token is processed
 * at most once, and a new activation is ignored only while the previous one is still being saved.
 * Deliberate separate taps after a save completes create separate entries.
 */
class QuickAddGuard {
    private val busy = AtomicBoolean(false)
    private val processed = LinkedHashSet<Long>()

    @Synchronized
    fun tryBegin(activationId: Long): Boolean {
        if (activationId in processed) return false
        if (!busy.compareAndSet(false, true)) return false
        processed += activationId
        if (processed.size > 64) processed.remove(processed.first())
        return true
    }

    fun end() {
        busy.set(false)
    }
}

/** Holds the last undoable action; each can be undone exactly once. */
class UndoSlot<T : Any> {
    private var pending: T? = null

    @Synchronized
    fun offer(value: T) {
        pending = value
    }

    @Synchronized
    fun take(): T? = pending.also { pending = null }

    @Synchronized
    fun clear() {
        pending = null
    }

    @Synchronized
    fun peek(): T? = pending
}
