package com.sjn.stamp.ui.item

import android.app.Activity
import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.view.View
import com.sjn.stamp.R
import com.sjn.stamp.ui.MediaBrowsable
import com.sjn.stamp.ui.item.holder.RankedViewHolder
import com.sjn.stamp.utils.AlbumArtHelper
import com.sjn.stamp.utils.MediaIDHelper
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import java.io.Serializable

class RankedArtistItem(
        val context: Context,
        val track: MediaMetadataCompat,
        val artistName: String,
        val playCount: Int,
        val order: Int,
        private val mediaBrowsable: MediaBrowsable?)
    : AbstractItem<RankedViewHolder>(artistName), IFilterable, Serializable {

    private val mostPlayedSongTitle = track.description.title?.toString() ?: ""
    override val title = artistName
    override val subtitle = context.getString(R.string.item_ranking_most_played, mostPlayedSongTitle)
            ?: ""

    init {
        isDraggable = true
        isSwipeable = true
    }

    override fun getLayoutRes(): Int = R.layout.recycler_ranked_item

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<*>): RankedViewHolder = RankedViewHolder(view, adapter)

    override fun bindViewHolder(adapter: FlexibleAdapter<*>, holder: RankedViewHolder, position: Int, payloads: List<*>) {
        if (adapter.hasSearchText()) {
            FlexibleUtils.highlightText(holder.title, title, adapter.searchText)
            FlexibleUtils.highlightText(holder.subtitle, subtitle, adapter.searchText)
        } else {
            holder.title.text = title
            holder.subtitle.text = subtitle
            holder.countView.text = playCount.toString()
            holder.orderView.text = order.toString()
        }
        AlbumArtHelper.searchAndUpdate(context as Activity, holder.albumArtView, title, MediaIDHelper.createMediaID(null, MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST, title), mediaBrowsable)
    }

    override fun filter(constraint: String): Boolean = artistName.toLowerCase().trim { it <= ' ' }.contains(constraint)
}