package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.GithubRepo
import com.osiris.app.recon.GithubResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** GitHub REST API lookup, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/osint/github/route.ts`. Keyless (unauthenticated, 60 req/hr/IP —
 * per-device now instead of shared off one backend IP). */
object GithubSource {

    private val UA = mapOf("User-Agent" to "OSIRIS-Recon")

    @Serializable
    private data class GithubUser(
        val login: String? = null,
        val name: String? = null,
        val company: String? = null,
        val blog: String? = null,
        val location: String? = null,
        val email: String? = null,
        val bio: String? = null,
        val twitter_username: String? = null,
        val public_repos: Int? = null,
        val followers: Int? = null,
        val created_at: String? = null,
    )

    @Serializable
    private data class GithubRepoDto(val name: String? = null, val language: String? = null, val updated_at: String? = null)

    suspend fun lookup(username: String): GithubResult = coroutineScope {
        val userDeferred = async {
            runCatching { DirectHttp.getJson<GithubUser>("https://api.github.com/users/${URLEncoder.encode(username, "UTF-8")}", headers = UA) }
        }
        val reposDeferred = async {
            runCatching {
                DirectHttp.getJson<List<GithubRepoDto>>(
                    "https://api.github.com/users/${URLEncoder.encode(username, "UTF-8")}/repos?sort=updated&per_page=5",
                    headers = UA,
                )
            }.getOrDefault(emptyList())
        }

        val user = userDeferred.await().getOrNull() ?: return@coroutineScope GithubResult(error = "User not found")
        val repos = reposDeferred.await()

        GithubResult(
            username = user.login,
            name = user.name,
            company = user.company,
            blog = user.blog,
            location = user.location,
            email = user.email,
            bio = user.bio,
            twitter = user.twitter_username,
            publicRepos = user.public_repos,
            followers = user.followers,
            createdAt = user.created_at,
            recentRepos = repos.map { GithubRepo(name = it.name, language = it.language, updated = it.updated_at) },
        )
    }
}
