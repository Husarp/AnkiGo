package com.husarp.ankigo.study

/** Distinguish in-memory card documents from their offline subresources before opening media. */
internal object CardResourcePolicy {
    enum class Route { DOCUMENT, EMPTY, MEDIA }

    fun route(mainFrame: Boolean, host: String?, path: String?): Route = when {
        mainFrame -> Route.DOCUMENT
        host != "ankigo.media" -> Route.EMPTY
        path.isNullOrEmpty() || path == "/" -> Route.DOCUMENT
        else -> Route.MEDIA
    }
}
