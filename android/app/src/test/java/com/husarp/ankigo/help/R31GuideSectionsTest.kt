package com.husarp.ankigo.help

import org.junit.Assert.*
import org.junit.Test

class R31GuideSectionsTest {
    @Test fun sectionsHaveHeadingsAndPreserveEveryGameRule() {
        for (chapter in GameGuide.chapters()) {
            val sections = GuideLessons.sections(chapter)
            assertTrue(sections.isNotEmpty())
            assertTrue(sections.all { it.title.isNotBlank() && it.icon != 0 && it.lines.isNotEmpty() })
            val rules = sections.flatMap { it.lines } + GuideLessons.details(chapter).flatMap { it.second }
            for (point in chapter.points) assertTrue("${chapter.id}: $point", point in rules)
        }
    }
}
