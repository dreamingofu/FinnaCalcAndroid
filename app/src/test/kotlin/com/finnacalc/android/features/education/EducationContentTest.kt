package com.finnacalc.android.features.education

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog and its relevance search, which is the whole tab's behaviour:
 * a query that ranks one way on the web must rank the same way here.
 */
class EducationContentTest {

    @Test
    fun `every catalog entry is a real link`() {
        val all = EducationContent.videoLessons.values.flatten() +
            EducationContent.readingResources.values.flatten()
        all.forEach {
            assertTrue("${it.title} has no url", it.url.startsWith("https://"))
            assertTrue("${it.title} has no title", it.title.isNotBlank())
        }
    }

    @Test
    fun `the search index carries every video and article`() {
        assertEquals(
            EducationContent.totalVideoCount + EducationContent.totalArticleCount,
            EducationContent.searchIndex.size,
        )
        assertEquals(
            EducationContent.totalVideoCount,
            EducationContent.searchIndex.count { it.type == EduSearchDoc.Kind.Video },
        )
        // Search-doc ids must be unique or list keys collide.
        assertEquals(
            EducationContent.searchIndex.size,
            EducationContent.searchIndex.map { it.id }.toSet().size,
        )
    }

    @Test
    fun `catalog topics are all known topic ids`() {
        val ids = EducationContent.topics.map { it.first }.toSet()
        (EducationContent.videoLessons.keys + EducationContent.readingResources.keys).forEach {
            assertTrue("$it isn't a listed topic", ids.contains(it))
        }
    }

    // MARK: - Search

    @Test
    fun `a one-character query returns nothing`() {
        assertTrue(EducationContent.search("a").isEmpty())
        assertTrue(EducationContent.search("").isEmpty())
    }

    @Test
    fun `an exact title match ranks first`() {
        val hits = EducationContent.search("What Is a Credit Score?")
        assertEquals("What Is a Credit Score?", hits.first().title)
    }

    @Test
    fun `search is forgiving about word endings`() {
        // "taxes" stems to "tax", which the tax lessons all share.
        val hits = EducationContent.search("taxes")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.topic == "taxes" })
    }

    @Test
    fun `an unrelated query returns nothing rather than noise`() {
        assertTrue(EducationContent.search("zzzzqqqq").isEmpty())
    }

    @Test
    fun `an all-stopword query falls back to substring matching`() {
        // Every token is a stop word, so this can only substring-match.
        val hits = EducationContent.search("how to")
        assertTrue(hits.all { "${it.title} ${it.topicName}".lowercase().contains("how to") })
    }

    @Test
    fun `results are capped at twenty-four`() {
        // A broad query would otherwise return the whole catalog.
        assertTrue(EducationContent.search("what is").size <= 24)
    }

    @Test
    fun `topic names resolve, and unknown ids pass through`() {
        assertEquals("Credit & Debt", EducationContent.topicName("credit"))
        assertEquals("nonsense", EducationContent.topicName("nonsense"))
    }

    // MARK: - YouTube helpers

    @Test
    fun `a watch url yields its id and thumbnail`() {
        val url = "https://www.youtube.com/watch?v=jwML94IOW0s"
        assertEquals("jwML94IOW0s", EducationContent.youTubeId(url))
        assertEquals(
            "https://img.youtube.com/vi/jwML94IOW0s/hqdefault.jpg",
            EducationContent.thumbnailUrl(url),
        )
    }

    @Test
    fun `extra query parameters are trimmed off the id`() {
        assertEquals(
            "abc123",
            EducationContent.youTubeId("https://www.youtube.com/watch?v=abc123&t=42s"),
        )
    }

    @Test
    fun `a non-youtube url yields no thumbnail rather than a broken one`() {
        assertNull(EducationContent.youTubeId("https://www.khanacademy.org/some/article"))
        assertNull(EducationContent.thumbnailUrl("https://www.khanacademy.org/some/article"))
    }

    @Test
    fun `every video in the catalog resolves a thumbnail`() {
        EducationContent.videoLessons.values.flatten().forEach {
            assertFalse("${it.title} has no thumbnail", EducationContent.thumbnailUrl(it.url) == null)
        }
    }
}
