package com.pawly.app.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatMessagePolicyTest {
    @Test
    fun trimsTextAndAcceptsPlainMessages() {
        val result = ChatMessagePolicy.prepare("  Hola  ")
        assertEquals("Hola", result.text)
        assertEquals("", result.imageUrl)
        assertEquals("", result.videoUrl)
    }

    @Test
    fun acceptsOneHttpsAttachmentWithoutText() {
        val image = ChatMessagePolicy.prepare("", " https://res.cloudinary.com/pawly/image.jpg ")
        assertEquals("https://res.cloudinary.com/pawly/image.jpg", image.imageUrl)
        val video = ChatMessagePolicy.prepare("", videoUrl = "https://res.cloudinary.com/pawly/video.mp4")
        assertEquals("https://res.cloudinary.com/pawly/video.mp4", video.videoUrl)
    }

    @Test
    fun rejectsEmptyMessagesAndTwoAttachments() {
        assertThrows(IllegalArgumentException::class.java) { ChatMessagePolicy.prepare("  ") }
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessagePolicy.prepare("Hola", "https://example.com/a.jpg", "https://example.com/a.mp4")
        }
    }

    @Test
    fun rejectsNonHttpsMediaAndOversizedContent() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessagePolicy.prepare("", imageUrl = "http://example.com/a.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessagePolicy.prepare("x".repeat(2001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessagePolicy.prepare("", videoUrl = "https://example.com/" + "x".repeat(2050))
        }
    }
}
