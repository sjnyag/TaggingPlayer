package com.sjn.stamp.ui.item

import android.support.v4.media.MediaMetadataCompat
import android.view.View
import com.sjn.stamp.R
import com.sjn.stamp.ui.item.holder.RankedViewHolder
import com.sjn.stamp.utils.AlbumArtHelper
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import java.io.Serializable

class RankedSongItem(
        val track: MediaMetadataCompat,
        private val playCount: Int,
        private val order: Int)
    : AbstractItem<RankedViewHolder>(track.description?.mediaId ?: ""), IFilterable, Serializable {

    val mediaId = track.description?.mediaId ?: ""
    override val title = track.description?.title?.toString() ?: ""
    override val subtitle = track.description?.subtitle?.toString() ?: ""
    private val albumArt = track.description?.iconUri?.toString() ?: ""

    init {
        isDraggable = true
        isSwipeable = true
    }

    override fun getLayoutRes(): Int = R.layout.recycler_ranked_item

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<*>): RankedViewHolder = RankedViewHolder(view, adapter)

    override fun bindViewHolder(adapter: FlexibleAdapter<*>, holder: RankedViewHolder, position: Int, payloads: List<*>) {
        val context = holder.itemView.context
        // In case of searchText matches with Title or with a field this will be highlighted
        if (adapter.hasSearchText()) {
            FlexibleUtils.highlightText(holder.title, title, adapter.searchText)
            FlexibleUtils.highlightText(holder.subtitle, subtitle, adapter.searchText)
        } else {
            holder.title.text = title
            holder.subtitle.text = subtitle
            holder.countView.text = playCount.toString()
            holder.orderView.text = order.toString()
        }
        if (albumArt.isNotEmpty()) AlbumArtHelper.update(context, holder.albumArtView, albumArt, title)
    }

    override fun filter(constraint: String): Boolean =
            track.description?.title?.toString()?.toLowerCase()?.trim { it <= ' ' }?.contains(constraint) == true
                    || track.description?.subtitle?.toString()?.toLowerCase()?.trim { it <= ' ' }?.contains(constraint) == true

}