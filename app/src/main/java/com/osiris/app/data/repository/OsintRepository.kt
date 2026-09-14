package com.osiris.app.data.repository

import com.osiris.app.data.model.OsintPost
import com.osiris.app.data.source.TelegramOsintSource

class OsintRepository {
    suspend fun fetch(baseUrl: String): List<OsintPost> =
        TelegramOsintSource.fetch().filter { it.coords != null }
}
