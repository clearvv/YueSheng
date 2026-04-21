package io.legado.app.service

import android.annotation.SuppressLint
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.AudioCacheHelp
import io.legado.app.help.book.BookHelp
import io.legado.app.model.ReadAloud
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.utils.GSON
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine

class AudioCacheService : BaseService(), TextToSpeech.OnInitListener {

    companion object {
        val cacheProgress = ConcurrentHashMap<String, Int>()
        val cacheMsg = ConcurrentHashMap<String, String>()
    }

    private val groupKey = "${appCtx.packageName}.audioCache"
    private val waitCacheBooks = linkedMapOf<String, BundleConfig>()
    private var cacheJob: Job? = null
    private var notificationContentText = appCtx.getString(R.string.service_starting)

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitFinish = false

    data class BundleConfig(
        val start: Int = 0,
        val end: Int = Int.MAX_VALUE
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.start -> kotlin.runCatching {
                val bookUrl = intent.getStringExtra("bookUrl")!!
                val start = intent.getIntExtra("start", 0)
                val end = intent.getIntExtra("end", Int.MAX_VALUE)
                
                if (!cacheProgress.contains(bookUrl)) {
                    waitCacheBooks[bookUrl] = BundleConfig(start, end)
                    cacheMsg[bookUrl] = "等待生成音频"
                    postEvent(EventBus.EXPORT_BOOK, bookUrl) // Reuse the export update for UI refresh
                    startCacheJob()
                }
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }

            IntentAction.stop -> {
                notificationManager.cancel(NotificationId.ExportBook)
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        cacheProgress.clear()
        cacheMsg.clear()
        waitCacheBooks.keys.forEach {
            postEvent(EventBus.EXPORT_BOOK, it)
        }
        textToSpeech?.runCatching {
            stop()
            shutdown()
        }
        textToSpeech = null
    }

    @Synchronized
    private fun initTts() {
        if (textToSpeech != null) return
        ttsInitFinish = false
        val engine = GSON.fromJsonObject<io.legado.app.lib.dialogs.SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value
        textToSpeech = if (engine.isNullOrBlank()) {
            TextToSpeech(this, this)
        } else {
            TextToSpeech(this, this, engine)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitFinish = true
        } else {
            AppLog.put("AudioCacheService: TTS Init Failed")
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    override fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText("生成朗读音频")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(groupKey)
            .setGroupSummary(true)
        startForeground(NotificationId.ExportBookService, notification.build())
    }

    private fun upNotification(finish: Boolean = false) {
        val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText("生成朗读音频")
            .setContentIntent(activityPendingIntent<CacheActivity>("cacheActivity"))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentText(notificationContentText)
            .setDeleteIntent(servicePendingIntent<AudioCacheService>(IntentAction.stop))
            .setGroup(groupKey)
            .setOnlyAlertOnce(true)
        if (!finish) {
            notification.setOngoing(true)
            notification.addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<AudioCacheService>(IntentAction.stop)
            )
        }
        notificationManager.notify(NotificationId.ExportBook, notification.build())
    }

    private fun startCacheJob() {
        if (cacheJob?.isActive == true) {
            return
        }
        initTts()

        cacheJob = lifecycleScope.launch(IO) {
            // Wait for TTS Init
            while (!ttsInitFinish) {
                kotlinx.coroutines.delay(100)
            }

            while (isActive) {
                val head = waitCacheBooks.entries.firstOrNull() ?: let {
                    notificationContentText = "生成完成"
                    upNotification(true)
                    stopSelf()
                    return@launch
                }
                val bookUrl = head.key
                val config = head.value
                
                cacheProgress[bookUrl] = 0
                waitCacheBooks.remove(bookUrl)
                val book = appDb.bookDao.getBook(bookUrl)

                try {
                    book ?: throw NoStackTraceException("获取${bookUrl}书籍出错")
                    notificationContentText = "正在生成音频 ${book.name} (还剩 ${waitCacheBooks.size} 本)"
                    upNotification()
                    synthesizeBook(book, config.start, config.end)
                    cacheMsg[book.bookUrl] = "生成成功"
                } catch (e: Throwable) {
                    coroutineContext.ensureActive()
                    cacheMsg[bookUrl] = e.localizedMessage ?: "ERROR"
                    AppLog.put("生成报错<${book?.name ?: bookUrl}>", e)
                } finally {
                    cacheProgress.remove(bookUrl)
                    postEvent(EventBus.EXPORT_BOOK, bookUrl)
                }
            }
        }
    }

    private suspend fun synthesizeBook(book: Book, start: Int, end: Int) {
        val allChapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val actualStart = start.coerceAtLeast(0)
        val actualEnd = end.coerceAtMost(allChapters.size - 1)
        
        if (actualStart > actualEnd) {
             return
        }

        val chapterList = allChapters.subList(actualStart, actualEnd + 1)
        var count = 0
        val total = chapterList.size

        chapterList.forEachIndexed { index, chapter ->
            coroutineContext.ensureActive()
            
            val chapterIndex = actualStart + index
            
            // Skip already fully cached chapter
            // Normally we would just check if at least paragraph 0 exists to skip, or read chapter to find out how many paragraphs
            
            val content = BookHelp.getContent(book, chapter)
            if (content.isNullOrBlank()) {
                count++
                return@forEachIndexed
            }
            
            val paragraphs = content.split("\n").filter { it.isNotEmpty() }
            
            for (paragraphIndex in paragraphs.indices) {
                coroutineContext.ensureActive()
                var text = paragraphs[paragraphIndex]
                if (text.matches(io.legado.app.constant.AppPattern.notReadAloudRegex)) continue
                
                val file = AudioCacheHelp.getAudioFile(book, chapterIndex, paragraphIndex)
                if (file.exists()) continue
                
                file.parentFile?.mkdirs()
                
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val utteranceId = "gen_${book.bookUrl}_${chapterIndex}_${paragraphIndex}"
                    
                    val listener = object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId && continuation.isActive) {
                                continuation.resume(true)
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId && continuation.isActive) {
                                continuation.resumeWithException(RuntimeException("TTS Error"))
                            }
                        }
                    }
                    
                    textToSpeech?.setOnUtteranceProgressListener(listener)
                    val result = textToSpeech?.synthesizeToFile(text, null, file, utteranceId)
                    
                    if (result != TextToSpeech.SUCCESS) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(RuntimeException("Failed to queue TTS"))
                        }
                    }
                }
            }
            
            count++
            cacheProgress[book.bookUrl] = (count.toFloat() / total * 100).toInt()
            postEvent(EventBus.EXPORT_BOOK, book.bookUrl)
        }
    }
}
