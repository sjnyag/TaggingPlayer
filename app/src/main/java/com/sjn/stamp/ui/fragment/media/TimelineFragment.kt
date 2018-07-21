package com.sjn.stamp.ui.fragment.media

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.google.common.collect.Iterables
import com.sjn.stamp.R
import com.sjn.stamp.controller.SongHistoryController
import com.sjn.stamp.model.SongHistory
import com.sjn.stamp.ui.SongAdapter
import com.sjn.stamp.ui.item.AbstractItem
import com.sjn.stamp.ui.item.DateHeaderItem
import com.sjn.stamp.ui.item.SongHistoryItem
import com.sjn.stamp.utils.LogHelper
import com.sjn.stamp.utils.MediaIDHelper
import com.sjn.stamp.utils.RealmHelper
import com.sjn.stamp.utils.findFirstVisibleItemPosition
import eu.davidea.fastscroller.FastScroller
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.helpers.UndoHelper
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IHeader
import io.realm.Realm
import java.util.*

class TimelineFragment : MediaBrowserListFragment(), UndoHelper.OnUndoListener, FlexibleAdapter.OnItemSwipeListener {

    private var songHistoryController: SongHistoryController? = null
    private var allSongHistoryList: List<SongHistory> = ArrayList()
    private var realm: Realm? = null

    /**
     * [ListFragment]
     */
    override val menuResourceId: Int
        get() = R.menu.timeline

    override fun emptyMessage(): String {
        return getString(R.string.empty_message_timeline)
    }

    /**
     * [MediaBrowserListFragment]
     */
    override fun onMediaBrowserChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {}

    override fun onMediaBrowserError(parentId: String) {}

    /**
     * [SwipeRefreshLayout.OnRefreshListener]
     */
    override fun onRefresh() {
        if (swipeRefreshLayout == null || activity == null || songHistoryController == null || listener == null) {
            return
        }
        listener?.destroyActionModeIfCan()
        swipeRefreshLayout?.isRefreshing = false
        draw()
    }

    /**
     * [FlexibleAdapter.OnItemClickListener]
     */
    override fun onItemClick(position: Int): Boolean {
        LogHelper.d(TAG, "onItemClick ")
        val item = adapter?.getItem(position)
        if (item is SongHistoryItem) {
            mediaBrowsable?.playByMediaId(MediaIDHelper.createDirectMediaId(MediaIDHelper.extractMusicIDFromMediaID(item.mediaId)!!))
        }
        return false
    }

    /**
     * [FlexibleAdapter.OnItemLongClickListener]
     */
    override fun onItemLongClick(position: Int) {
        listener?.startActionModeByLongClick(position)
    }

    /**
     * [FlexibleAdapter.EndlessScrollListener]
     */
    override fun noMoreLoad(newItemsSize: Int) {
        LogHelper.d(TAG, "newItemsSize=$newItemsSize")
        LogHelper.d(TAG, "Total pages loaded=" + adapter!!.endlessCurrentPage)
        LogHelper.d(TAG, "Total items loaded=" + adapter!!.mainItemCount)
    }

    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        adapter?.let {
            createItemList(it.mainItemCount - it.headerItems.size, 30).run {
                currentItems = currentItems.plus(this)
                it.onLoadMoreComplete(this, 5000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        realm = RealmHelper.realmInstance
    }

    override fun onDestroy() {
        super.onDestroy()
        realm?.close()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_list, container, false)

        context?.let {
            songHistoryController = SongHistoryController(it)
        }
        if (savedInstanceState != null) {
            listState = savedInstanceState.getParcelable(ListFragment.LIST_STATE_KEY)
        }

        loading = rootView.findViewById(R.id.progressBar)
        emptyView = rootView.findViewById(R.id.empty_view)
        fastScroller = rootView.findViewById(R.id.fast_scroller)
        emptyTextView = rootView.findViewById(R.id.empty_text)
        swipeRefreshLayout = rootView.findViewById(R.id.refresh)
        recyclerView = rootView.findViewById(R.id.recycler_view)

        swipeRefreshLayout?.apply {
            setOnRefreshListener(this@TimelineFragment)
            setColorSchemeColors(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        }
        adapter = SongAdapter(currentItems, this).apply {
            setNotifyChangeOfUnfilteredItems(true)
            setAnimationOnScrolling(false)
        }
        recyclerView?.apply {
            activity?.let {
                this.layoutManager = SmoothScrollLinearLayoutManager(it).apply {
                    listState?.let { onRestoreInstanceState(it) }
                }
            }
            this.adapter = this@TimelineFragment.adapter
        }
        adapter?.apply {
            fastScroller = rootView.findViewById<View>(R.id.fast_scroller) as FastScroller
            isLongPressDragEnabled = false
            isHandleDragEnabled = false
            isSwipeEnabled = true
            setUnlinkAllItemsOnRemoveHeaders(false)
            setDisplayHeadersAtStartUp(true)
            setStickyHeaders(true)
            showAllHeaders()
            setEndlessScrollListener(this@TimelineFragment, progressItem)
        }
        initializeFabWithStamp()
        notifyFragmentChange()
        if (currentItems.isEmpty()) {
            loading?.visibility = View.VISIBLE
            draw()
        } else {
            loading?.visibility = View.GONE
        }

        return rootView
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.upward -> {
                recyclerView?.let {
                    it.post {
                        it.scrollToPosition(calcGoToTopBufferedPosition(15))
                        it.smoothScrollToPosition(0)
                    }
                }
                return false
            }
            else -> {
            }
        }
        return false
    }

    @Synchronized
    private fun draw() {
        adapter ?: return
        realm?.let {
            allSongHistoryList = songHistoryController?.getManagedTimeline(it) ?: emptyList()
        }
        currentItems = createItemList(0, 30)
        loading?.visibility = View.INVISIBLE
        adapter?.updateDataSet(currentItems)
        if (currentItems.isEmpty()) {
            hideFab()
        } else {
            showFab()
        }
    }

    private fun calcGoToTopBufferedPosition(bufferSize: Int): Int =
            calcCurrentPosition().let { position -> if (position > bufferSize) bufferSize else position }

    private fun calcCurrentPosition(): Int =
            recyclerView?.findFirstVisibleItemPosition() ?: 0

    private fun newSimpleItem(songHistory: SongHistory, header: IHeader<*>, resources: Resources, activity: Activity): SongHistoryItem {
        return SongHistoryItem(songHistory, header as DateHeaderItem, resources, activity)
    }

    private fun createItemList(startPosition: Int, size: Int): List<AbstractFlexibleItem<*>> {
        val end = if (startPosition + size >= allSongHistoryList.size) allSongHistoryList.size else startPosition + size
        val headerItemList = ArrayList<AbstractFlexibleItem<*>>()
        var header = if (adapter?.headerItems?.isEmpty() == true) null else adapter?.let { Iterables.getLast<IHeader<*>>(it.headerItems) }
        for (i in startPosition until allSongHistoryList.size) {
            if (header is DateHeaderItem?) {
                if (header == null || !header.isDateOf(allSongHistoryList[i].recordedAt)) {
                    if (i >= end) {
                        break
                    }
                    header = newHeader(allSongHistoryList[i])
                }
                activity?.let {
                    headerItemList.add(newSimpleItem(allSongHistoryList[i], header, resources, it))
                }
            }
        }
        return headerItemList
    }

    override fun onItemSwipe(position: Int, direction: Int) {
        LogHelper.i(TAG, "onItemSwipe position=" + position +
                " direction=" + if (direction == ItemTouchHelper.LEFT) "LEFT" else "RIGHT")
        if (adapter?.getItem(position) !is AbstractItem<*>) return
        val item = adapter?.getItem(position) as AbstractItem<*>
        activity?.run {
            tryRemove(item, position)
        }
    }

    private fun tryRemove(item: AbstractItem<*>, position: Int) {
        val positions = mutableListOf(position)
        val message = StringBuilder().append(item.toString()).append(" ").append(getString(R.string.action_deleted))
        if (item.isSelectable) adapter?.setRestoreSelectionOnUndo(false)
        adapter?.isPermanentDelete = false
        swipeRefreshLayout?.isRefreshing = true
        activity?.let {
            UndoHelper(adapter, this@TimelineFragment)
                    .withPayload(null)
                    .withConsecutive(true)
                    .start(positions, it.findViewById(R.id.main_view), message, getString(R.string.undo), UndoHelper.UNDO_TIMEOUT)
        }
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        LogHelper.i(TAG, "onActionStateChanged actionState=$actionState")
        swipeRefreshLayout?.isEnabled = actionState == ItemTouchHelper.ACTION_STATE_IDLE
    }

    override fun onActionCanceled(action: Int) {
        LogHelper.i(TAG, "onUndoConfirmed action=$action")
        adapter?.restoreDeletedItems()
        swipeRefreshLayout?.isRefreshing = false
        if (adapter?.isRestoreWithSelection == true) listener?.restoreSelection()
    }

    override fun onActionConfirmed(action: Int, event: Int) {
        LogHelper.i(TAG, "onDeleteConfirmed action=$action")
        swipeRefreshLayout?.isRefreshing = false
        for (adapterItem in adapter?.deletedItems ?: emptyList()) {
            try {
                when (adapterItem.layoutRes) {
                    R.layout.recycler_song_history_item -> {
                        val subItem = adapterItem as AbstractItem<*>
                        activity?.let { adapterItem.delete(it) }
                        LogHelper.i(TAG, "Confirm removed " + subItem.toString())
                    }
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(TimelineFragment::class.java)

        fun newHeader(songHistory: SongHistory): DateHeaderItem {
            return DateHeaderItem(songHistory.recordedAt)
        }
    }
}