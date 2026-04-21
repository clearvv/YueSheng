package io.legado.app.model

import java.io.File

data class AudioFileItem(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val file: File,
    var isSelected: Boolean = false
) {
    val size: Long
        get() = file.length()
    
    val name: String
        get() = "第${chapterIndex + 1}章 - 第${paragraphIndex + 1}段"
}
