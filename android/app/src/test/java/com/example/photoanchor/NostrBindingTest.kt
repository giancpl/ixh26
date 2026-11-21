package com.example.photoanchor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind

class NostrBindingTest {
    @Test fun officialBindingSignsAndVerifies() {
        Keys.generate().use { keys ->
            EventBuilder(Kind(1u), "binding check").finalize(keys).use { event ->
                assertTrue(NostrBinding.verify(event.asJson()))
                assertFalse(NostrBinding.verify(event.asJson().replace("binding check", "changed")))
            }
        }
    }
}
