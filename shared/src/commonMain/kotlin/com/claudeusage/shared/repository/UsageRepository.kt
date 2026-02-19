package com.claudeusage.shared.repository

import com.claudeusage.shared.model.Credentials
import com.claudeusage.shared.model.ExtraUsageInfo
import com.claudeusage.shared.model.UsageData
import com.claudeusage.shared.model.UsageMetric
import com.claudeusage.shared.platform.userAgent
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*

class UsageRepository {

    private val client = HttpClient {
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchUsageData(credentials: Credentials): Result<UsageData> {
        return try {
            coroutineScope {
                val usageDeferred = async {
                    fetchJson(
                        "$BASE_URL/api/organizations/${credentials.organizationId}/usage",
                        credentials.sessionKey
                    )
                }
                val overageDeferred = async {
                    runCatching {
                        fetchJson(
                            "$BASE_URL/api/organizations/${credentials.organizationId}/overage_spend_limit",
                            credentials.sessionKey
                        )
                    }.getOrNull()
                }
                val prepaidDeferred = async {
                    runCatching {
                        fetchJson(
                            "$BASE_URL/api/organizations/${credentials.organizationId}/prepaid/credits",
                            credentials.sessionKey
                        )
                    }.getOrNull()
                }

                val usageResult = usageDeferred.await()
                val overageJson = overageDeferred.await()
                val prepaidJson = prepaidDeferred.await()

                val usageData = parseUsageData(usageResult)

                // Merge overage spending data
                var extraMetric = usageData.extraUsage
                var extraInfo: ExtraUsageInfo? = null

                if (overageJson != null) {
                    val limit = overageJson.intOrNull("monthly_credit_limit")
                        ?: overageJson.intOrNull("spend_limit_amount_cents")
                    val used = overageJson.intOrNull("used_credits")
                        ?: overageJson.intOrNull("balance_cents")
                    val enabled = if (overageJson.containsKey("is_enabled"))
                        overageJson["is_enabled"]?.jsonPrimitive?.booleanOrNull ?: false
                    else (limit != null)

                    if (enabled && limit != null && limit > 0 && used != null) {
                        val utilization = (used.toDouble() / limit) * 100
                        extraMetric = UsageMetric(utilization, null)
                        extraInfo = ExtraUsageInfo(usedCents = used, limitCents = limit)
                    }
                }

                // Merge prepaid balance
                if (prepaidJson != null) {
                    val amount = prepaidJson.intOrNull("amount")
                    if (amount != null) {
                        extraInfo = (extraInfo ?: ExtraUsageInfo()).copy(balanceCents = amount)
                    }
                }

                Result.success(
                    usageData.copy(
                        extraUsage = extraMetric ?: usageData.extraUsage,
                        extraUsageInfo = extraInfo
                    )
                )
            }
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: CloudflareException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchJson(url: String, sessionKey: String): JsonObject {
        val response = client.get(url) {
            header("Cookie", "sessionKey=$sessionKey")
            header("User-Agent", userAgent())
            header("Accept", "application/json")
            header("Referer", BASE_URL)
        }

        val body = response.bodyAsText()

        when {
            response.status.value == 401 || response.status.value == 403 -> {
                throw AuthException("Session expired. Please log in again.")
            }
            !response.status.isSuccess() -> {
                throw Exception("HTTP ${response.status.value}: ${response.status.description}")
            }
            body.contains("Just a moment") || body.contains("Enable JavaScript") -> {
                throw CloudflareException("Cloudflare challenge detected. Please try again.")
            }
            body.trimStart().startsWith("<") -> {
                throw Exception("Unexpected HTML response from server.")
            }
        }

        return json.parseToJsonElement(body).jsonObject
    }

    suspend fun fetchOrganizationId(sessionKey: String): Result<String> {
        return try {
            val response = client.get("$BASE_URL/api/organizations") {
                header("Cookie", "sessionKey=$sessionKey")
                header("User-Agent", userAgent())
                header("Accept", "application/json")
                header("Referer", BASE_URL)
            }

            val body = response.bodyAsText()

            when {
                response.status.value == 401 || response.status.value == 403 -> {
                    Result.failure(AuthException("Invalid session key."))
                }
                !response.status.isSuccess() -> {
                    Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
                }
                else -> {
                    val jsonArray = json.parseToJsonElement(body).jsonArray
                    if (jsonArray.isNotEmpty()) {
                        val orgId = jsonArray[0].jsonObject["uuid"]?.jsonPrimitive?.content
                            ?: return Result.failure(Exception("No organization UUID found."))
                        Result.success(orgId)
                    } else {
                        Result.failure(Exception("No organizations found."))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancelPendingRequests() {
        client.close()
    }

    suspend fun validateSession(credentials: Credentials): Boolean {
        return fetchUsageData(credentials).isSuccess
    }

    companion object {
        const val BASE_URL = "https://claude.ai"
    }
}

// JSON parsing helpers

private fun JsonObject.intOrNull(key: String): Int? {
    val element = this[key] ?: return null
    return element.jsonPrimitive.intOrNull
}

private fun parseUsageMetric(obj: JsonObject?): UsageMetric? {
    if (obj == null) return null
    return try {
        UsageMetric(
            utilization = obj["utilization"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            resetsAt = obj["resets_at"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
                ?.let { Instant.parse(it) }
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseUsageData(jsonObj: JsonObject): UsageData {
    val keys = jsonObj.keys.toList()
    return UsageData(
        fiveHour = parseUsageMetric(jsonObj["five_hour"]?.jsonObject),
        sevenDay = parseUsageMetric(jsonObj["seven_day"]?.jsonObject),
        sevenDaySonnet = parseUsageMetric(jsonObj["seven_day_sonnet"]?.jsonObject),
        sevenDayOpus = parseUsageMetric(jsonObj["seven_day_opus"]?.jsonObject),
        sevenDayCowork = parseUsageMetric(jsonObj["seven_day_cowork"]?.jsonObject),
        sevenDayOauthApps = parseUsageMetric(jsonObj["seven_day_oauth_apps"]?.jsonObject),
        extraUsage = parseUsageMetric(jsonObj["extra_usage"]?.jsonObject),
        rawKeys = keys
    )
}
