package dev.axolotl.bucket.api

/**
 * @author Kenox
 */
@kotlinx.serialization.Serializable
data class ApplicationConfig(
    val host: String,
    val port: Int,
    val apiKey: String,
    val appIdKey: String,
    val appKey: String,
    val bucketName: String,
    val cachePeriod: Int
)
