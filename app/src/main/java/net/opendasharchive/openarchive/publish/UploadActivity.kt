package net.opendasharchive.openarchive.publish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.scal.secureshareui.controller.SiteController.*
import net.opendasharchive.openarchive.MainActivity.Companion.INTENT_FILTER_NAME
import net.opendasharchive.openarchive.OpenArchiveApp
import net.opendasharchive.openarchive.R
import net.opendasharchive.openarchive.databinding.UploadActivityBinding
import net.opendasharchive.openarchive.db.Media
import net.opendasharchive.openarchive.db.MediaAdapter
import net.opendasharchive.openarchive.features.media.list.MediaListFragment

class UploadActivity : AppCompatActivity(), MediaListFragment.OnStartDragListener {

    private lateinit var mBinding: UploadActivityBinding

    private lateinit var mAdapter: MediaAdapter

    private var mMenuEdit: MenuItem? = null
    private var mEditMode = false

    private val mBroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            when (Media.Status.getByValue(intent?.getIntExtra(MESSAGE_KEY_STATUS, -1))) {
                Media.Status.UPLOADING -> {
                    mAdapter.updateItem(
                        intent?.getLongExtra(MESSAGE_KEY_MEDIA_ID, -1) ?: -1,
                        intent?.getLongExtra(MESSAGE_KEY_PROGRESS, 0) ?: 0)
                }

                Media.Status.UPLOADED, Media.Status.QUEUED -> {
                    refresh()
                }

                Media.Status.ERROR -> {
                    if ((application as? OpenArchiveApp)?.hasCleanInsightsConsent() == false) {
                        (application as? OpenArchiveApp)?.showCleanInsightsConsent(this@UploadActivity)
                    }
                }

                else -> { /* ignore */ }
            }
        }
    }

    private val mItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.END or ItemTouchHelper.START
    ) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            mAdapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)

            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            mAdapter.onItemDismiss(viewHolder.adapterPosition)
        }

    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = UploadActivityBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mAdapter = MediaAdapter(this, R.layout.activity_media_list_row_short, ArrayList(),
            mBinding.recyclerview, this)

        setSupportActionBar(mBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mBinding.recyclerview.layoutManager = LinearLayoutManager(this)
        mBinding.recyclerview.adapter = mAdapter
    }

    override fun onResume() {
        super.onResume()

        refresh()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(mBroadcastReceiver, IntentFilter(INTENT_FILTER_NAME))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_upload, menu)

        mMenuEdit = menu?.findItem(R.id.menu_edit)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setEditMode(false)
            finish()

            return true
        }

        if (item == mMenuEdit) {
            setEditMode(!mEditMode)

            refresh()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        setEditMode(false)

        super.onBackPressed()
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        if (viewHolder != null) mItemTouchHelper.startDrag(viewHolder)
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)

        super.onPause()
    }

    private fun setEditMode(value: Boolean) {
        mEditMode = value

        val intent = Intent(this, PublishService::class.java)

        if (mEditMode) {
            mMenuEdit?.title = getString(R.string.menu_done)

            stopService(intent)
        }
        else {
            mMenuEdit?.title = getString(R.string.menu_edit)

            startService(intent)
        }
    }

    private fun refresh() {
        mAdapter.setEditMode(mEditMode)

        val data = Media.getMediaByStatus(
            arrayOf(Media.Status.QUEUED, Media.Status.UPLOADING, Media.Status.ERROR),
            Media.ORDER_PRIORITY) ?: emptyList()

        supportActionBar?.title = if (data.isNotEmpty()) {
            getString(R.string.title_uploading, data.size)
        }
        else {
            getString(R.string.title_uploading_done)
        }

        mAdapter.updateData(ArrayList(data))
    }
}