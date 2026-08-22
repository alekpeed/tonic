package com.tonic.feature.practice.engine

import com.tonic.core.model.items.Item

/**
 * Narrows [PracticeLoopState.currentItem] to the recognition item these tests are written about.
 *
 * The loop became polymorphic over item types when `M9` arrived, so `currentItem` is now `Item?`. Every
 * assertion in these Phase 1 suites is about `M2` specifically — degrees, reference plans, home
 * reminders — and none of them changed. This exists so they did not have to: the alternative was
 * editing dozens of assertions to add a cast, which is exactly the kind of churn that hides a real
 * behavioral change inside a mechanical diff.
 *
 * Fails loudly rather than returning null on the wrong type: a test that asked for a recognition item
 * and silently got nothing would pass vacuously.
 */
internal val PracticeLoopState.recognitionItem: Item.FunctionalRecognitionItem?
    get() =
        when (val item = currentItem) {
            null -> null
            is Item.FunctionalRecognitionItem -> item
            else -> error("expected a recognition item on screen, got ${item::class.simpleName}")
        }
