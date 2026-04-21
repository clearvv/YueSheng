package io.legado.app.ui.book.cache

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemAudioFileBinding
import io.legado.app.model.AudioFileItem
import io.legado.app.utils.ConvertUtils

class AudioCacheDetailAdapter(context: Context) :
    RecyclerAdapter<AudioFileItem, ItemAudioFileBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemAudioFileBinding {
        return ItemAudioFileBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemAudioFileBinding,
        item: AudioFileItem,
        payloads: MutableList<Any>
    ) {
        binding.run {
            tvTitle.text = item.name
            tvSize.text = ConvertUtils.formatFileSize(item.size)
            cbSelect.isChecked = item.isSelected
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemAudioFileBinding) {
        holder.itemView.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let {
                it.isSelected = !it.isSelected
                binding.cbSelect.isChecked = it.isSelected
            }
        }
    }
}
