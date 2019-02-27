package com.simplemobiletools.filemanager.pro.fragments

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.StoragePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SORT_BY_SIZE
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.Breadcrumbs
import com.simplemobiletools.commons.views.MyLinearLayoutManager
import com.simplemobiletools.filemanager.pro.R
import com.simplemobiletools.filemanager.pro.activities.MainActivity
import com.simplemobiletools.filemanager.pro.activities.SimpleActivity
import com.simplemobiletools.filemanager.pro.adapters.ItemsAdapter
import com.simplemobiletools.filemanager.pro.dialogs.CreateNewItemDialog
import com.simplemobiletools.filemanager.pro.extensions.config
import com.simplemobiletools.filemanager.pro.extensions.extension
import com.simplemobiletools.filemanager.pro.extensions.isPathOnRoot
import com.simplemobiletools.filemanager.pro.extensions.tryOpenPathIntent
import com.simplemobiletools.filemanager.pro.helpers.NetworkHelper
import com.simplemobiletools.filemanager.pro.helpers.PATH
import com.simplemobiletools.filemanager.pro.helpers.RootHelpers
import com.simplemobiletools.filemanager.pro.interfaces.ItemOperationsListener
import com.simplemobiletools.filemanager.pro.player.MusicConstants
import com.simplemobiletools.filemanager.pro.player.MusicConstants.FILE_NAME_EXTRA_PARAM
import com.simplemobiletools.filemanager.pro.player.MusicConstants.MUSIC_FILE_EXT
import com.simplemobiletools.filemanager.pro.player.SoundService
import kotlinx.android.synthetic.main.items_fragment.view.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class ItemsFragment : Fragment(), ItemOperationsListener, Breadcrumbs.BreadcrumbsListener {
    var currentPath = ""
    var isGetContentIntent = false
    var isGetRingtonePicker = false
    var isPickMultipleIntent = false

    private var isFirstResume = true
    private var showHidden = false
    private var skipItemUpdating = false
    private var isSearchOpen = false
    private var scrollStates = HashMap<String, Parcelable>()

    private var storedItems = ArrayList<FileDirItem>()
    private var storedTextColor = 0

    lateinit var mView: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mView = inflater.inflate(R.layout.items_fragment, container, false)!!
        storeStateVariables()
        return mView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mView.apply {
            items_swipe_refresh.setOnRefreshListener { refreshItems() }
            items_fab.setOnClickListener { createNewItem() }
            breadcrumbs.listener = this@ItemsFragment
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PATH, currentPath)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            currentPath = savedInstanceState.getString(PATH)
            storedItems.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        context!!.updateTextColors(mView as ViewGroup)
        mView.items_fastscroller.updatePrimaryColor()
        val newTextColor = context!!.config.textColor
        if (storedTextColor != newTextColor) {
            storedItems = ArrayList()
            getRecyclerAdapter()?.apply {
                updateTextColor(newTextColor)
                initDrawables()
            }
            mView.breadcrumbs.updateColor(newTextColor)
            storedTextColor = newTextColor
        }

        mView.items_fastscroller.updateBubbleColors()
        mView.items_fastscroller.allowBubbleDisplay = context!!.config.showInfoBubble
        if (!isFirstResume) {
            refreshItems()
        }
        getRecyclerAdapter()?.adjustedPrimaryColor = context!!.getAdjustedPrimaryColor()
        isFirstResume = false
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    private fun storeStateVariables() {
        context!!.config.apply {
            storedTextColor = textColor
        }
    }

    fun openPath(path: String, forceRefresh: Boolean = false) {
        if (!isAdded || (activity as? BaseSimpleActivity)?.isAskingPermissions == true) {
            return
        }

        var realPath = path.trimEnd('/')
        if (realPath.isEmpty()) {
            realPath = "/"
        }

        scrollStates[currentPath] = getScrollState()!!
        currentPath = realPath
        showHidden = context!!.config.shouldShowHidden
        getItems(currentPath) { originalPath, fileDirItems ->
            if (currentPath != originalPath || !isAdded) {
                return@getItems
            }

            FileDirItem.sorting = context!!.config.getFolderSorting(currentPath)
            fileDirItems.sort()
            activity?.runOnUiThread {
                addItems(fileDirItems, forceRefresh)
            }
        }
    }

    private fun addItems(items: ArrayList<FileDirItem>, forceRefresh: Boolean = false) {
        skipItemUpdating = false
        mView.apply {
            activity?.runOnUiThread {
                items_swipe_refresh?.isRefreshing = false
                if (!forceRefresh && items.hashCode() == storedItems.hashCode()) {
                    return@runOnUiThread
                }

                mView.breadcrumbs.setBreadcrumb(currentPath)
                storedItems = items
                ItemsAdapter(activity as SimpleActivity, storedItems, this@ItemsFragment, items_list, isPickMultipleIntent, items_fastscroller) {
                    itemClicked(it as FileDirItem)
                }.apply {
                    addVerticalDividers(true)
                    items_list.adapter = this
                }
                items_fastscroller.allowBubbleDisplay = context.config.showInfoBubble
                items_fastscroller.setViews(items_list, mView.items_swipe_refresh) {
                    items_fastscroller.updateBubbleText(storedItems.getOrNull(it)?.getBubbleText() ?: "")
                }

                getRecyclerLayoutManager().onRestoreInstanceState(scrollStates[currentPath])
                items_list.onGlobalLayout {
                    items_fastscroller.setScrollToY(items_list.computeVerticalScrollOffset())
                }
            }
        }
    }

    fun getScrollState() = getRecyclerLayoutManager().onSaveInstanceState()

    private fun getRecyclerLayoutManager() = (mView.items_list.layoutManager as MyLinearLayoutManager)

    private fun getItems(path: String, callback: (originalPath: String, items: ArrayList<FileDirItem>) -> Unit) {
        skipItemUpdating = false
        Thread {
            if (activity?.isDestroyed == false) {
                /*if (path.startsWith(OTG_PATH)) {
                    val getProperFileSize = context!!.config.sorting and SORT_BY_SIZE != 0
                    context!!.getOTGItems(path, context!!.config.shouldShowHidden, getProperFileSize) {
                        callback(path, it)
                    }
                } else */if (!context!!.config.enableRootAccess || !context!!.isPathOnRoot(path)) {
                    getRegularItemsOf(path, callback)
                } else {
                    RootHelpers(activity!!).getFiles(path, callback)
                }
            }
        }.start()
    }

    private fun getRegularItemsOf(path: String, callback: (originalPath: String, items: ArrayList<FileDirItem>) -> Unit) {
        val items = ArrayList<FileDirItem>()
        val files = File(path).listFiles()?.filterNotNull()
        if (context == null) {
            callback(path, items)
            return
        }

        val isSortingBySize = context!!.config.sorting and SORT_BY_SIZE != 0
        if (files != null) {
            for (file in files) {
                val curPath = file.absolutePath
                val curName = file.name
                if (!showHidden && curName.startsWith(".")) {
                    continue
                }

                val isDirectory = file.isDirectory
                val children = if (isDirectory) file.getDirectChildrenCount(showHidden) else 0
                val size = if (isDirectory) {
                    if (isSortingBySize) {
                        file.getProperSize(showHidden)
                    } else {
                        0L
                    }
                } else {
                    file.length()
                }
                val fileDirItem = FileDirItem(curPath, curName, isDirectory, children, size)
                items.add(fileDirItem)
            }
        }

        callback(path, items)
    }

    private fun itemClicked(item: FileDirItem) {
        if (item.isDirectory) {
            (activity as? MainActivity)?.apply {
                skipItemUpdating = isSearchOpen
                openedDirectory()
            }
            openPath(item.path)
        } else {
            val path = item.path
            if (isGetContentIntent) {
                (activity as MainActivity).pickedPath(path)
            } else if (isGetRingtonePicker) {
                if (path.isAudioFast()) {
                    (activity as MainActivity).pickedRingtone(path)
                } else {
                    activity?.toast(R.string.select_audio_file)
                }
            } else {
//                val match = MUSIC_FILE_EXT.filter { it in path.extension() }
                val found = Arrays.stream(MUSIC_FILE_EXT).anyMatch { t -> t == path.extension() }
                if (found) {
                    tryPlayFile(path)
                } else {
                    activity!!.tryOpenPathIntent(path, false)
                }
            }
        }
    }


    fun tryPlayFile(fileName: String){

            val lState = SoundService.state
            if (lState == MusicConstants.STATE_SERVICE.NOT_INIT) {
                if (!NetworkHelper.isInternetAvailable(activity)) {
                    showError()
                }
                Intent(activity, SoundService::class.java).apply {
                        putExtra(FILE_NAME_EXTRA_PARAM, fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        action = MusicConstants.ACTION.START_ACTION
                        activity!!.startService(this)
                }
            } else if (lState == MusicConstants.STATE_SERVICE.PREPARE || lState == MusicConstants.STATE_SERVICE.PLAY) {
                val lPlayIntent = Intent(activity, SoundService::class.java)
                lPlayIntent.action = MusicConstants.ACTION.PLAY_ACTION
                lPlayIntent.putExtra(FILE_NAME_EXTRA_PARAM, fileName)
                val lPendingPauseIntent = PendingIntent.getService(activity, 0, lPlayIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                try {
                    lPendingPauseIntent.send()
                } catch (e: PendingIntent.CanceledException) {
                    e.printStackTrace()
                }

            } else if (lState == MusicConstants.STATE_SERVICE.PAUSE) {
                if (!NetworkHelper.isInternetAvailable(activity)) {
                    showError()
                }
                val lPauseIntent = Intent(activity, SoundService::class.java)
                lPauseIntent.putExtra(FILE_NAME_EXTRA_PARAM, fileName)
                lPauseIntent.action = MusicConstants.ACTION.PLAY_ACTION
                val lPendingPauseIntent = PendingIntent.getService(activity, 0, lPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                try {
                    lPendingPauseIntent.send()
                } catch (e: PendingIntent.CanceledException) {
                    e.printStackTrace()
                }
            }

    }

    private fun showError() {
        Snackbar.make(activity!!.findViewById(android.R.id.content), "No internet", Snackbar.LENGTH_LONG).show()
    }


    fun searchQueryChanged(text: String) {
        Thread {
            val filtered = storedItems.filter { it.name.contains(text, true) } as ArrayList
            filtered.sortBy { !it.name.startsWith(text, true) }
            activity?.runOnUiThread {
                getRecyclerAdapter()?.updateItems(filtered, text)
            }
        }.start()
    }

    fun searchOpened() {
        isSearchOpen = true
    }

    fun searchClosed() {
        isSearchOpen = false
        if (!skipItemUpdating) {
            getRecyclerAdapter()?.updateItems(storedItems)
        }
        skipItemUpdating = false
    }

    private fun createNewItem() {
        CreateNewItemDialog(activity as SimpleActivity, currentPath) {
            if (it) {
                refreshItems()
            } else {
                activity?.toast(R.string.unknown_error_occurred)
            }
        }
    }

    private fun getRecyclerAdapter() = mView.items_list.adapter as? ItemsAdapter

    override fun breadcrumbClicked(id: Int) {
        if (id == 0) {
            StoragePickerDialog(activity as SimpleActivity, currentPath) {
                getRecyclerAdapter()?.finishActMode()
                openPath(it)
            }
        } else {
            val item = mView.breadcrumbs.getChildAt(id).tag as FileDirItem
            openPath(item.path)
        }
    }

    override fun refreshItems() {
        openPath(currentPath)
    }

    override fun deleteFiles(files: ArrayList<FileDirItem>) {
        val hasFolder = files.any { it.isDirectory }
        val firstPath = files.firstOrNull()?.path
        if (firstPath == null || firstPath.isEmpty() || context == null) {
            return
        }

        if (context!!.isPathOnRoot(firstPath)) {
            RootHelpers(activity!!).deleteFiles(files)
        } else {
            (activity as SimpleActivity).deleteFiles(files, hasFolder) {
                if (!it) {
                    activity!!.runOnUiThread {
                        activity!!.toast(R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        (activity as MainActivity).pickedPaths(paths)
    }
}
