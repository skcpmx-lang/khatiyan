package com.shohan.khatiyan.utilities

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Tiny app-wide "data changed" signal. Repositories poke it after every
 * successful write; dashboard/report screens reload their aggregates in
 * response. Keeps screens reactive without dozens of bespoke flows.
 */
object DataBus {
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 32)

    fun poke() {
        changes.tryEmit(Unit)
    }
}
