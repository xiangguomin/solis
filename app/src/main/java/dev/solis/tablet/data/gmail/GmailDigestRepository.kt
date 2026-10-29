package dev.solis.tablet.data.gmail

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Fetches REAL important Gmail threads (`newer_than:1d is:important -category:promotions -category:social`)
 * from the user's account (`xiangguomin@gmail.com`) via:
 * 1. Android AccountManager.newChooseAccountIntent + getAuthToken (`gmail.readonly`),
 * 2. Real-time Gmail Notification Listener on the tablet (`com.google.android.gm`).
 * Never returns fake demo emails.
 */
class GmailDigestRepository(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val imapClient: GmailImapClient = GmailImapClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "SolisGmail"
        const val DEFAULT_GMAIL_QUERY =
            "newer_than:1d is:important -category:promotions -category:social -category:forums"
        const val GMAIL_OAUTH_SCOPE =
            "oauth2:https://www.googleapis.com/auth/gmail.readonly"

        fun inferCategory(text: String): DigestCategory {
            val lower = text.lowercase()
            return when {
                "deliver" in lower || "package" in lower || "shipped" in lower || "amazon" in lower ->
                    DigestCategory.DELIVERY
                "statement" in lower || "invoice" in lower || "payment" in lower || "bank" in lower || "chase" in lower ->
                    DigestCategory.FINANCE
                "flight" in lower || "hotel" in lower || "reservation" in lower || "airline" in lower ->
                    DigestCategory.TRAVEL
                "urgent" in lower || "action" in lower || "please sign" in lower || "due" in lower ->
                    DigestCategory.ACTION_REQUIRED
                else -> DigestCategory.PERSONAL
            }
        }
    }

    /**
     * Attempts to obtain an OAuth2 token for `gmail.readonly` from the Google account signed in
     * on the Lenovo tablet (`xiangguomin@gmail.com`).
     * - If the app does not yet have Android 11+ Account Visibility for `com.google`, returns
     *   `NeedsAccountPicker` with `AccountManager.newChooseAccountIntent` so the user can select
     *   `xiangguomin@gmail.com` in one tap.
     * - If OAuth consent is needed, returns `NeedsConsent` with the Google consent `Intent`.
     */
    suspend fun requestDeviceGoogleAuthToken(context: Context): TokenResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val am = AccountManager.get(context)
                val googleAccount: Account? = am.getAccountsByType("com.google").firstOrNull()

                if (googleAccount == null) {
                    val chooseAccountIntent = AccountManager.newChooseAccountIntent(
                        null,
                        null,
                        arrayOf("com.google"),
                        null,
                        null,
                        null,
                        null
                    )
                    Log.d(TAG, "Account visibility not granted yet; returning ChooseAccountIntent")
                    return@withContext TokenResult.NeedsConsent(
                        consentIntent = chooseAccountIntent,
                        email = "Google Account"
                    )
                }

                suspendCancellableCoroutine<TokenResult> { cont ->
                    am.getAuthToken(
                        googleAccount,
                        GMAIL_OAUTH_SCOPE,
                        null,
                        false,
                        { future ->
                            val result = runCatching {
                                val bundle = future.result
                                val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
                                val intent = bundle.getParcelable(AccountManager.KEY_INTENT, Intent::class.java)
                                when {
                                    !token.isNullOrBlank() -> {
                                        Log.d(TAG, "Obtained OAuth token for ${googleAccount.name}")
                                        TokenResult.Success(token, googleAccount.name)
                                    }
                                    intent != null -> {
                                        Log.d(TAG, "Needs user OAuth consent for ${googleAccount.name}")
                                        TokenResult.NeedsConsent(intent, googleAccount.name)
                                    }
                                    else -> TokenResult.Error("Unable to obtain token for ${googleAccount.name}")
                                }
                            }.getOrElse { e ->
                                Log.w(TAG, "getAuthToken failed: ${e.message}")
                                TokenResult.Error(e.message ?: "Auth error")
                            }
                            if (cont.isActive) cont.resume(result)
                        },
                        null
                    )
                }
            }.getOrElse { e -> TokenResult.Error(e.message ?: "AccountManager error") }
        }

    sealed class TokenResult {
        data class Success(val token: String, val email: String) : TokenResult()
        data class NeedsConsent(val consentIntent: Intent, val email: String) : TokenResult()
        data class Error(val reason: String) : TokenResult()
    }

    suspend fun fetchDailyDigest(
        gmailOAuthToken: String,
        geminiApiKey: String,
        gmailEmail: String = "xiangguomin@gmail.com"
    ): List<GmailDigestItem> = withContext(Dispatchers.IO) {
        val cleanCredential = gmailOAuthToken.trim()

        // 1. If the user entered a 16-char Google App Password (or non-ya29 password), parse the Spark Daily Email Digest first!
        if (cleanCredential.isNotBlank() && !cleanCredential.startsWith("ya29.")) {
            val imapItems = runCatching {
                imapClient.fetchParsedDailyDigestItems(gmailEmail, cleanCredential)
            }.getOrDefault(emptyList())

            if (imapItems.isNotEmpty()) {
                return@withContext imapItems
            }
        }

        // 2. Try Gmail REST API if we have an OAuth token (`ya29....`)
        if (cleanCredential.startsWith("ya29.")) {
            val apiItems = runCatching {
                val rawMessages = fetchImportantMessages(cleanCredential)
                if (rawMessages.isEmpty()) return@runCatching emptyList<GmailDigestItem>()

                val byId = rawMessages.associateBy { it.id }
                if (geminiApiKey.isNotBlank()) {
                    summarizeWithGemini(rawMessages, geminiApiKey).map { item ->
                        val raw = byId[item.threadId]
                        if (raw != null) {
                            item.copy(
                                subject = raw.subject.ifEmpty { item.summaryOneLiner },
                                senderEmail = raw.from,
                                fullBodySnippet = raw.fullBody.ifEmpty { raw.snippet }
                            )
                        } else {
                            item
                        }
                    }
                } else {
                    rawMessages.map { msg ->
                        GmailDigestItem(
                            threadId = msg.id,
                            category = inferCategory(msg.subject + " " + msg.snippet),
                            senderName = msg.from.substringBefore("<").trim().removeSurrounding("\"")
                                .ifEmpty { "Gmail" },
                            summaryOneLiner = msg.subject.ifEmpty { msg.snippet },
                            receivedTimeText = msg.dateText.ifEmpty { "Today" },
                            subject = msg.subject,
                            senderEmail = msg.from,
                            fullBodySnippet = msg.fullBody.ifEmpty { msg.snippet }
                        )
                    }
                }
            }.getOrDefault(emptyList())

            if (apiItems.isNotEmpty()) {
                return@withContext apiItems
            }
        }

        // 3. Otherwise return real live notifications captured from the tablet's Gmail app
        GmailNotificationListener.liveNotificationItems.value
    }

    private fun fetchImportantMessages(token: String): List<RawEmailMessage> {
        val encodedQuery = URLEncoder.encode(DEFAULT_GMAIL_QUERY, "UTF-8")
        val listUrl =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=8&q=$encodedQuery"
        val listReq = Request.Builder()
            .url(listUrl)
            .header("Authorization", "Bearer $token")
            .build()

        val messageIds = mutableListOf<String>()
        httpClient.newCall(listReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "Gmail messages.list HTTP ${resp.code}: ${resp.body?.string()}")
                return emptyList()
            }
            val root = JSONObject(resp.body?.string() ?: "{}")
            val arr = root.optJSONArray("messages") ?: return emptyList()
            for (i in 0 until arr.length()) {
                messageIds.add(arr.getJSONObject(i).getString("id"))
            }
        }

        return messageIds.mapNotNull { id ->
            val msgUrl =
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=full"
            val msgReq = Request.Builder()
                .url(msgUrl)
                .header("Authorization", "Bearer $token")
                .build()
            httpClient.newCall(msgReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = JSONObject(resp.body?.string() ?: "{}")
                val threadId = obj.optString("threadId", id)
                val snippet = obj.optString("snippet", "")
                val payload = obj.optJSONObject("payload")
                val headers = payload?.optJSONArray("headers")
                var from = ""
                var subject = ""
                var dateText = ""
                if (headers != null) {
                    for (i in 0 until headers.length()) {
                        val h = headers.getJSONObject(i)
                        when (h.optString("name")) {
                            "From" -> from = h.optString("value")
                            "Subject" -> subject = h.optString("value")
                            "Date" -> dateText = h.optString("value").take(16)
                        }
                    }
                }
                val bodyText = extractPlainTextBody(payload).ifBlank { snippet }
                RawEmailMessage(
                    id = threadId,
                    from = from,
                    subject = subject,
                    dateText = dateText,
                    snippet = snippet,
                    fullBody = bodyText
                )
            }
        }
    }

    private fun extractPlainTextBody(payload: JSONObject?): String {
        if (payload == null) return ""
        val mimeType = payload.optString("mimeType", "")
        val bodyData = payload.optJSONObject("body")?.optString("data", "") ?: ""
        if (mimeType == "text/plain" && bodyData.isNotEmpty()) {
            return decodeBase64Url(bodyData)
        }
        val parts = payload.optJSONArray("parts") ?: return ""
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val text = extractPlainTextBody(part)
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun decodeBase64Url(encoded: String): String {
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8).trim()
        }.getOrDefault("")
    }

    private fun summarizeWithGemini(
        messages: List<RawEmailMessage>,
        geminiApiKey: String
    ): List<GmailDigestItem> {
        val promptBuilder = StringBuilder()
        promptBuilder.appendLine(
            "You are an executive assistant for a smart home tablet display. " +
                "Summarize the following important emails from the last 24 hours into 3 to 5 concise digest items. " +
                "Return ONLY a JSON array with objects matching: " +
                "{\"threadId\": string, \"category\": \"ACTION_REQUIRED\"|\"DELIVERY\"|\"FINANCE\"|\"TRAVEL\"|\"PERSONAL\", " +
                "\"senderName\": string, \"summaryOneLiner\": string, \"receivedTimeText\": \"Today\"}."
        )
        messages.forEach { s ->
            promptBuilder.appendLine("- ID: ${s.id} | From: ${s.from} | Subject: ${s.subject} | Snippet: ${s.snippet}")
        }

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", promptBuilder.toString()))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json")
            )
        }

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Gemini API HTTP ${resp.code}" }
            val root = JSONObject(resp.body?.string() ?: "{}")
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            return json.decodeFromString<List<GmailDigestItem>>(text)
        }
    }

    private data class RawEmailMessage(
        val id: String,
        val from: String,
        val subject: String,
        val dateText: String,
        val snippet: String,
        val fullBody: String
    )
}
