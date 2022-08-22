package dev.axolotl.bucket

import com.backblaze.b2.client.B2Sdk
import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.B2StorageClientFactory
import com.backblaze.b2.client.contentHandlers.B2ContentFileWriter
import com.backblaze.b2.client.contentSources.B2ContentTypes
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.structures.*
import com.backblaze.b2.util.B2ExecutorUtils.createThreadFactory
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.axolotl.bucket.api.ApplicationConfig
import dev.axolotl.bucket.plugins.configureRouting
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

lateinit var b2Client: B2StorageClient
lateinit var bucket: B2Bucket
lateinit var config: ApplicationConfig

val executor: ExecutorService = Executors.newFixedThreadPool(10, createThreadFactory("B2-executor-%02d"))

private val json = Json { prettyPrint = true }

fun main() {
    // Load config
    val configFile = File("config.json")

    if(!configFile.exists()) {
        val defaultConfig = ApplicationConfig(
            "127.0.0.1",
            8080,
            "unknown",
            "unknown",
            "unknown",
            "unknown",
            86400
        )

        configFile.createNewFile()

        configFile.writeText(json.encodeToString(defaultConfig))

        println("Config created. Shutting down ..")

        exitProcess(0)
    }

    config = json.decodeFromString(configFile.readText())

    println(config.toString())

    // Connect to backblaze b2
    b2Client = B2StorageClientFactory
        .createDefaultFactory()
        .create(config.appIdKey, config.appKey, "axolotl-bucket")

    bucket = b2Client.getBucketOrNullByName(config.bucketName)!!

    println("Running with ${B2Sdk.getName()} version ${B2Sdk.getVersion()}")

    // Cache task
    cacheTask()

    // Start server
    embeddedServer(Netty, port = config.port, host = config.host) {
        configureRouting()
    }.start(wait = true)
}

fun cacheTask() {
    thread {
        while (true) {
            Thread.sleep(5000)

            // Get all cached files
            File("cache").walk().forEach {
                if(it.isDirectory)
                    return@forEach

                val lastModified = it.lastModified() + TimeUnit.SECONDS.toMillis(config.cachePeriod.toLong())
                val shouldDelete = System.currentTimeMillis() >= lastModified

                if(!shouldDelete)
                    return@forEach

                it.delete()

                println("File ${it.name} deleted from cache")
            }
        }
    }
}

fun uploadFile(file: File) {
    if(!::b2Client.isInitialized) {
        println("b2Client not initialized")
        return
    }

    val uploadListener = B2UploadListener { progress: B2UploadProgress ->
        val percent = 100.0 * (progress.bytesSoFar / progress.length.toDouble())
        println(String.format("  progress(%3.2f, %s)", percent, progress.toString()))
    }

    val source = B2FileContentSource.build(file);

    val request: B2UploadFileRequest = B2UploadFileRequest
        .builder(bucket.bucketId, "packs/${file.name}", B2ContentTypes.B2_AUTO, source)
        .setListener(uploadListener)
        .build()

    val timeMillis = System.currentTimeMillis()

    // Get length of file in bytes
    val fileSizeInBytes = file.length()
    // Convert the bytes to Kilobytes (1 KB = 1024 Bytes)
    val fileSizeInKB = fileSizeInBytes / 1024
    // Convert the KB to MegaBytes (1 MB = 1024 KBytes)
    val fileSizeInMB = fileSizeInKB / 1024

    val fileVersion: B2FileVersion = if(fileSizeInMB >= 100) {
        println("File size of ${file.name} = $fileSizeInMB -> so use large file upload")
        b2Client.uploadLargeFile(request, executor)
    } else {
        println("File size of ${file.name} = $fileSizeInMB -> so use small file upload")
        b2Client.uploadSmallFile(request)
    }

    println("B2 | Uploaded file " + fileVersion.fileName + " to backblaze in ${System.currentTimeMillis() - timeMillis}ms")
}

fun downloadFile(fileName: String, output: File) {
    if(!::b2Client.isInitialized) {
        println("b2Client not initialized")
        return
    }

    val request = B2DownloadByNameRequest
        .builder(bucket.bucketName, fileName)
        .build();

    val handler = B2ContentFileWriter
        .builder(output)
        .setVerifySha1ByRereadingFromDestination(true)
        .build();

    val timeMillis = System.currentTimeMillis()

    b2Client.downloadByName(request, handler);

    println("B2 | Download of $fileName successful in ${System.currentTimeMillis() - timeMillis}ms")
}

fun deleteAllFiles(fileNameToDelete: String, exceptFileVersion: B2FileVersion? = null) {
    if(!::b2Client.isInitialized) {
        println("b2Client not initialized")
        return
    }

    val request: B2ListFileVersionsRequest = B2ListFileVersionsRequest
        .builder(bucket.bucketId)
        .setStartFileName(fileNameToDelete)
        .setPrefix(fileNameToDelete)
        .build()

    val start = System.currentTimeMillis()

    for (version in b2Client.fileVersions(request)) {
        if(exceptFileVersion != null && version.fileId == exceptFileVersion.fileId)
            continue

        if (version.fileName == fileNameToDelete) {
            b2Client.deleteFileVersion(version)
            println("B2 | Delete file $fileNameToDelete successful in ${System.currentTimeMillis() - start}ms")
        } else {
            break
        }
    }
}

@Throws(IOException::class)
fun getFileChecksum(digest: MessageDigest, file: File): String {
    val fis = FileInputStream(file)

    val byteArray = ByteArray(1024)
    var bytesCount = 0

    while (fis.read(byteArray).also { bytesCount = it } != -1) {
        digest.update(byteArray, 0, bytesCount)
    }

    fis.close()

    val bytes = digest.digest()

    val sb = StringBuilder()
    for (i in bytes.indices) {
        sb.append(((bytes[i].toInt() and 0xff) + 0x100).toString(16).substring(1))
    }

    return sb.toString()
}
