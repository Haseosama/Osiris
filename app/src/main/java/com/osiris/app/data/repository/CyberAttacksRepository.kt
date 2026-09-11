package com.osiris.app.data.repository

import com.osiris.app.data.model.CyberAttack
import com.osiris.app.data.remote.NetworkModule

class CyberAttacksRepository {
    suspend fun fetch(baseUrl: String): List<CyberAttack> =
        NetworkModule.apiFor(baseUrl).cyberAttacks().attacks
}
