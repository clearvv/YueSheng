package io.legado.app.ui.book.cache

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.Book
import io.legado.app.help.book.AudioCacheHelp
import io.legado.app.model.AudioFileItem
import io.legado.app.utils.FileUtils
import io.legado.app.utils.sendValue
import java.io.File

class AudioCacheDetailViewModel(application: Application) : BaseViewModel(application) {
    val audioFilesLiveData = MutableLiveData<List<AudioFileItem>>()
    
    fun loadAudioFiles(book: Book) {
        execute {
            val files = AudioCacheHelp.getAllAudioFiles(book)
            val items = files.map { file ->
                // chapterIndex/paragraphIndex are encoded in the path: .../book/chapterIndex/paragraphIndex.wav
                val paragraphIndex = file.nameWithoutExtension.toIntOrNull() ?: 0
                val chapterIndex = file.parentFile?.name?.toIntOrNull() ?: 0
                AudioFileItem(chapterIndex, paragraphIndex, file)
            }
            audioFilesLiveData.sendValue(items)
        }
    }

    fun deleteFiles(items: List<AudioFileItem>, book: Book) {
        execute {
            items.forEach {
                FileUtils.delete(it.file)
                // If chapter directory is empty, delete it too
                it.file.parentFile?.let { dir ->
                    if (dir.list().isNullOrEmpty()) {
                        FileUtils.delete(dir)
                    }
                }
            }
            loadAudioFiles(book)
        }
    }
}
