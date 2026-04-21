package io.legado.app.ui.book.cache

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ActivityAudioCacheDetailBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.TitleBar
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioCacheDetailActivity : VMBaseActivity<ActivityAudioCacheDetailBinding, AudioCacheDetailViewModel>() {

    override val binding by viewBinding(ActivityAudioCacheDetailBinding::inflate)
    override val viewModel by viewModels<AudioCacheDetailViewModel>()

    private val adapter by lazy { AudioCacheDetailAdapter(this) }
    private var book: Book? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val bookUrl = intent.getStringExtra("bookUrl") ?: return finish()
        
        lifecycleScope.launch {
            book = withContext(IO) { appDb.bookDao.getBook(bookUrl) }
            book?.let {
                binding.titleBar.title = it.name
                viewModel.loadAudioFiles(it)
            } ?: finish()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()

        viewModel.audioFilesLiveData.observe(this) {
            adapter.setItems(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "全选")
        menu.add(0, 2, 0, "删除选中")
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                adapter.getItems().forEach { it.isSelected = true }
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
            2 -> {
                val selected = adapter.getItems().filter { it.isSelected }
                if (selected.isNotEmpty()) {
                    alert("确认删除", "确定删除选中的 ${selected.size} 个音频段落吗？") {
                        val activity = this@AudioCacheDetailActivity
                        yesButton {
                            book?.let { viewModel.deleteFiles(selected, it) }
                        }
                        noButton()
                    }.show()
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }
}
