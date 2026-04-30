package top.kagg886.pmf.backend.archive

import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.util.decodeBase64String
import kotlin.time.measureTime
import kotlinx.serialization.json.Json
import moe.tarsin.gif.encodeGif
import okio.FileSystem
import okio.buffer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.IllustImagesType
import top.kagg886.pixko.module.illust.get
import top.kagg886.pmf.backend.dataPath
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.IllustArchive
import top.kagg886.pmf.backend.database.dao.IllustArchiveMediaType
import top.kagg886.pmf.backend.database.dao.fileNames
import top.kagg886.pmf.backend.useTempDir
import top.kagg886.pmf.backend.useTempFile
import top.kagg886.pmf.util.UGOIRA_SCHEME
import top.kagg886.pmf.util.absolutePath
import top.kagg886.pmf.util.createNewFile
import top.kagg886.pmf.util.exists
import top.kagg886.pmf.util.logger
import top.kagg886.pmf.util.mkdirs
import top.kagg886.pmf.util.parentFile
import top.kagg886.pmf.util.source
import top.kagg886.pmf.util.unzip
import top.kagg886.pmf.util.writeBytes

const val ILLUST_ARCHIVE_SCHEME = "pixiv-archive"

private val archiveRoot by lazy {
    dataPath.resolve("archive").resolve("illust")
}

fun illustArchiveDir(illustId: Int) = archiveRoot.resolve(illustId.toString())

fun archiveUri(illustId: Int, fileName: String): Uri = "$ILLUST_ARCHIVE_SCHEME://$illustId/$fileName".toUri()

fun archiveKeyParts(key: String): Pair<Int, String>? = key.takeIf { it.startsWith("$ILLUST_ARCHIVE_SCHEME://") }?.let {
    val payload = it.removePrefix("$ILLUST_ARCHIVE_SCHEME://")
    val illustId = payload.substringBefore("/")
    val fileName = payload.substringAfter("/", "")
    if (illustId.isBlank() || fileName.isBlank()) {
        return@let null
    }
    illustId.toIntOrNull()?.let { id -> id to fileName }
}

fun archivePathFromKey(key: String) = archiveKeyParts(key)?.let { (illustId, fileName) -> illustArchiveDir(illustId).resolve(fileName) }

fun IllustArchive.previewUrisOrNull() = fileNames.takeIf { it.isNotEmpty() && it.all { file -> illustArchiveDir(illustId).resolve(file).exists() } }
    ?.map { archiveUri(illustId, it) }

private fun guessMimeType(path: okio.Path) = when (path.name.substringAfterLast('.', "").lowercase()) {
    "gif" -> "image/gif"
    "png" -> "image/png"
    "webp" -> "image/webp"
    else -> "image/jpeg"
}

class IllustArchiveFetcher(private val data: Uri) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val path = archivePathFromKey(data.toString()) ?: error("unknown archive uri: $data")
        return SourceFetchResult(
            source = ImageSource(
                file = path,
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = guessMimeType(path),
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: coil3.ImageLoader): Fetcher? {
            if (data.scheme != ILLUST_ARCHIVE_SCHEME) return null
            return IllustArchiveFetcher(data)
        }
    }
}

class IllustArchiveManager : KoinComponent {
    private val database by inject<AppDatabase>()
    private val net by inject<HttpClient>()

    suspend fun find(illustId: Int) = database.illustArchiveDao().find(illustId)

    suspend fun deleteIncompleteArchives() {
        val dao = database.illustArchiveDao()
        dao.list().filter { it.previewUrisOrNull() == null }.forEach {
            dao.delete(it.illustId)
        }
    }

    suspend fun archive(illust: Illust, media: List<Uri>) {
        val archiveMedia = mediaForArchive(illust, media)
        if (archiveMedia.isEmpty()) return

        val targetDir = illustArchiveDir(illust.id)
        targetDir.mkdirs()

        val files = if (archiveMedia.singleOrNull()?.scheme == UGOIRA_SCHEME) {
            listOf(storeUgoira(archiveMedia.single(), targetDir))
        } else {
            archiveMedia.mapIndexed { index, uri ->
                storeImage(index, uri, targetDir)
            }
        }

        if (files.isEmpty() || files.any { !targetDir.resolve(it).exists() }) return

        database.illustArchiveDao().insert(
            IllustArchive(
                illustId = illust.id,
                illust = illust,
                mediaType = if (archiveMedia.singleOrNull()?.scheme == UGOIRA_SCHEME) IllustArchiveMediaType.UGOIRA else IllustArchiveMediaType.IMAGE,
                mediaFiles = Json.encodeToString(files),
            ),
        )
    }

    private fun mediaForArchive(illust: Illust, currentMedia: List<Uri>): List<Uri> {
        if (currentMedia.singleOrNull()?.scheme == UGOIRA_SCHEME) return currentMedia
        return illust.contentImages[IllustImagesType.ORIGIN]?.map(String::toUri)?.takeIf { it.isNotEmpty() }
            ?: currentMedia
    }

    private suspend fun storeImage(index: Int, uri: Uri, targetDir: okio.Path): String {
        val extension = uri.toString()
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 5 }
            ?: "jpg"
        val fileName = "page-$index.$extension"
        val target = targetDir.resolve(fileName)
        copyUriTo(uri, target)
        return fileName
    }

    private suspend fun storeUgoira(uri: Uri, targetDir: okio.Path): String {
        val fileName = "ugoira.gif"
        val target = targetDir.resolve(fileName)
        val source = archivePathFromKey(uri.toString())
        if (source != null) {
            copyFile(source, target)
            return fileName
        }

        val metadata = Json.decodeFromString<top.kagg886.pixko.module.ugoira.UgoiraMetadata>(
            uri.authority!!.decodeBase64String(),
        )

        runCatching {
            useTempFile { zip ->
                val bytes: ByteArray
                measureTime {
                    bytes = net.get(metadata.url.content).bodyAsBytes()
                }.also {
                    logger.i { "Download archived ugoira takes ${it.inWholeMilliseconds} ms" }
                }
                zip.writeBytes(bytes)
                useTempDir { workDir ->
                    zip.unzip(workDir)
                    target.parentFile()?.mkdirs()
                    if (!target.exists()) {
                        target.createNewFile()
                    }
                    encodeGif(target) {
                        for (frame in metadata.frames) {
                            frame(path = workDir.resolve(frame.file), delay = frame.delay)
                        }
                    }
                }
            }
        }.getOrElse {
            logger.w("archive ugoira failed", it)
            throw it
        }
        return fileName
    }

    private suspend fun copyUriTo(uri: Uri, target: okio.Path) {
        archivePathFromKey(uri.toString())?.let {
            copyFile(it, target)
            return
        }
        target.parentFile()?.mkdirs()
        if (!target.exists()) {
            target.createNewFile()
        }
        target.writeBytes(net.get(uri.toString()).bodyAsBytes())
    }

    private fun copyFile(source: okio.Path, target: okio.Path) {
        if (source.absolutePath() == target.absolutePath()) return
        target.parentFile()?.mkdirs()
        if (!target.exists()) {
            target.createNewFile()
        }
        target.writeBytes(source.source().buffer().use { input -> input.readByteArray() })
    }
}
