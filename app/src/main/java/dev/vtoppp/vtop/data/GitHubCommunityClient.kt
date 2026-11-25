package dev.vtoppp.vtop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class GitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val htmlUrl: String,
    val author: String,
    val labels: List<String>,
    val commentsCount: Int,
)

data class GitHubContributor(
    val id: Long,
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val contributions: Int,
)

/**
 * Lazy GitHub client for community engagement (open issues and contributors).
 * Never called on startup or in background — only executed when the user
 * explicitly taps "Contribute" or "Contributors" in Settings.
 */
object GitHubCommunityClient {
    private const val ISSUES_URL = "https://api.github.com/repos/vtop-xceed/vtop-android/issues?state=open&per_page=30"
    private const val CONTRIBUTORS_URL = "https://api.github.com/repos/vtop-xceed/vtop-android/contributors?per_page=50"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(sharedDnsWithDohFallback)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchOpenIssues(): Result<List<GitHubIssue>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(ISSUES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "vtop-android-app")
                .build()

            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                val array = JSONArray(resp.body.string())
                val issues = mutableListOf<GitHubIssue>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    // Skip pull requests which GitHub includes in the issues endpoint
                    if (obj.has("pull_request")) continue

                    val labelsArray = obj.optJSONArray("labels")
                    val labels = mutableListOf<String>()
                    if (labelsArray != null) {
                        for (j in 0 until labelsArray.length()) {
                            labels.add(labelsArray.getJSONObject(j).optString("name", ""))
                        }
                    }

                    issues.add(
                        GitHubIssue(
                            id = obj.optLong("id"),
                            number = obj.optInt("number"),
                            title = obj.optString("title", "Untitled issue"),
                            htmlUrl = obj.optString("html_url", "https://github.com/vtop-xceed/vtop-android/issues"),
                            author = obj.optJSONObject("user")?.optString("login", "contributor") ?: "contributor",
                            labels = labels.filter { it.isNotBlank() },
                            commentsCount = obj.optInt("comments", 0),
                        )
                    )
                }
                issues
            }
        }
    }

    suspend fun fetchContributors(): Result<List<GitHubContributor>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(CONTRIBUTORS_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "vtop-android-app")
                .build()

            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                val array = JSONArray(resp.body.string())
                val contributors = mutableListOf<GitHubContributor>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val login = obj.optString("login", "")
                    if (login.isBlank()) continue

                    contributors.add(
                        GitHubContributor(
                            id = obj.optLong("id"),
                            login = login,
                            avatarUrl = obj.optString("avatar_url", ""),
                            htmlUrl = obj.optString("html_url", "https://github.com/$login"),
                            contributions = obj.optInt("contributions", 1),
                        )
                    )
                }
                contributors
            }
        }
    }
}
