package io.legado.app.help.book

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.utils.FileUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.exists
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import splitties.init.appCtx
import java.io.File

object AudioCacheHelp {
    private val downloadDir: File = appCtx.externalFiles
    private const val cacheAudioFolderName = "audio_cache"

    fun getAudioCachePath(): String {
        return FileUtils.getPath(downloadDir, cacheAudioFolderName)
    }

    fun getAudioFile(book: Book, chapterIndex: Int, paragraphIndex: Int): File {
        return downloadDir.getFile(
            cacheAudioFolderName,
            book.getFolderName(),
            chapterIndex.toString(),
            "${paragraphIndex}.wav"
        )
    }

    fun isAudioCacheExist(book: Book, chapterIndex: Int, paragraphIndex: Int): Boolean {
        return getAudioFile(book, chapterIndex, paragraphIndex).exists()
    }

    fun clearAudioCache() {
        FileUtils.delete(getAudioCachePath())
    }

    fun clearAudioCache(book: Book) {
        val path = FileUtils.getPath(downloadDir, cacheAudioFolderName, book.getFolderName())
        FileUtils.delete(path)
    }

    fun getAudioCacheChapters(book: Book): HashSet<String> {
        val chapters = hashSetOf<String>()
        val dir = downloadDir.getFile(cacheAudioFolderName, book.getFolderName())
        if (dir.exists() && dir.isDirectory) {
            dir.list()?.forEach {
                if (it.matches(Regex("\\d+"))) {
                    chapters.add(it)
                }
            }
        }
        return chapters
    }

    fun getAllAudioFiles(book: Book): List<File> {
        val files = mutableListOf<File>()
        val dir = downloadDir.getFile(cacheAudioFolderName, book.getFolderName())
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { chapterDir ->
                if (chapterDir.isDirectory && chapterDir.name.matches(Regex("\\d+"))) {
                    chapterDir.listFiles()?.forEach { file ->
                        if (file.extension == "wav") {
                            files.add(file)
                        }
                    }
                }
            }
        }
        return files.sortedBy { it.absolutePath }
    }
}
