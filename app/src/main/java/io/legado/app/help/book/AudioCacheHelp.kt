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
}
