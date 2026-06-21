package co.rowm.osrsllm.companion

import java.awt.Point
import javax.swing.SwingUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SpeechBubble is a Swing component, so we hop onto the EDT to drive it
 * and assert the visible-state contract. We deliberately don't drive
 * the typewriter timer in real time - we let the component start it and
 * confirm the public surface (visibility, text length, dismiss
 * callback) behaves.
 */
class SpeechBubbleTest {

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block()
        else SwingUtilities.invokeAndWait(block)
    }

    @Test
    fun `show flips isVisibleNow and sets size`() {
        var dismissed = false
        var clicked = 0
        onEdt {
            val bubble = SpeechBubble(
                anchorSupplier = { Point(120, 80) },
                onClick = { clicked++ },
                onDismissed = { dismissed = true },
            )
            bubble.show("Hello, world!")
            assertTrue("bubble must be visible after show", bubble.isVisibleNow)
            assertNotNull(bubble.preferredSize)
            assertTrue("preferred width must be positive", bubble.preferredSize.width > 0)
            assertTrue("preferred height must be positive", bubble.preferredSize.height > 0)
            bubble.dismiss()
            assertFalse(bubble.isVisibleNow)
            assertTrue("dismiss must fire onDismissed", dismissed)
            assertEquals(0, clicked)
        }
    }

    @Test
    fun `empty text shows nothing and does not crash`() {
        onEdt {
            val bubble = SpeechBubble(anchorSupplier = { Point(0, 0) })
            bubble.show("")
            // show always flips isVisibleNow but the paint loop guards on
            // empty text so it stays a no-op visually.
            assertTrue(bubble.isVisibleNow)
            bubble.dismiss()
            assertFalse(bubble.isVisibleNow)
        }
    }

    @Test
    fun `dismiss is idempotent`() {
        onEdt {
            var dismissed = 0
            val bubble = SpeechBubble(
                anchorSupplier = { Point(0, 0) },
                onDismissed = { dismissed++ },
            )
            bubble.show("text")
            bubble.dismiss()
            bubble.dismiss()
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun `reanchor with null anchor is a noop`() {
        onEdt {
            val bubble = SpeechBubble(anchorSupplier = { null })
            // Must not throw.
            bubble.reanchor()
            bubble.show("hi")
            bubble.reanchor()
            assertTrue(bubble.isVisibleNow)
        }
    }

    @Test
    fun `isFullyRevealed only true after typewriter completes`() {
        onEdt {
            val bubble = SpeechBubble(anchorSupplier = { Point(0, 0) })
            bubble.show("a longer message that takes more than one char to reveal")
            // Just after show the typewriter hasn't started revealing yet.
            assertFalse(bubble.isFullyRevealed())
        }
    }
}
