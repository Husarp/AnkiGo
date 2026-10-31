package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import com.husarp.ankigo.study.CardResourcePolicy.Route

/** Pure routing tests: layoutlib stubs Uri/WebResourceResponse and cannot run Chromium. */
class TemplateResourceTest {
    @Test fun mainFramesNeverBecomeMediaErrors() {
        for (host in listOf("ankigo.media", "example.com", null)) {
            for (path in listOf("/", "/missing.html", null)) {
                assertEquals(Route.DOCUMENT, CardResourcePolicy.route(true, host, path))
            }
        }
    }

    @Test fun cardBaseIsADocumentEvenWhenNotMarkedMainFrame() {
        for (path in listOf("/", "", null)) {
            assertEquals(Route.DOCUMENT, CardResourcePolicy.route(false, "ankigo.media", path))
        }
    }

    @Test fun externalSubresourcesKeepTheOldEmptySuccessPolicy() {
        for (host in listOf("example.com", "ankigo.media.example.com", null)) {
            assertEquals(Route.EMPTY, CardResourcePolicy.route(false, host, "/script.js"))
        }
    }

    @Test fun mediaDictionaryMathAndFontsStillReachTheMediaHandler() {
        listOf("/picture.png", "/missing.png", "/_slownik_math.js", "/style.css",
            "/mathjax/tex-mml-chtml.js", "/mathjax/input/tex/extensions/html.js",
            "/fonts/Nunito.ttf", "/mathjax/output/chtml/fonts/woff-v2/MathJax_Main-Regular.woff").forEach {
            assertEquals(it, Route.MEDIA, CardResourcePolicy.route(false, "ankigo.media", it))
        }
    }
}
