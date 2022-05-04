package net.opendasharchive.openarchive.publish

import android.app.*
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.scal.secureshareui.controller.ArchiveSiteController
import io.scal.secureshareui.controller.SiteController
import net.opendasharchive.openarchive.MainActivity
import net.opendasharchive.openarchive.R
import net.opendasharchive.openarchive.db.Collection
import net.opendasharchive.openarchive.db.Media
import net.opendasharchive.openarchive.db.Project
import net.opendasharchive.openarchive.db.Space
import net.opendasharchive.openarchive.db.Space.Companion.getCurrentSpace
import net.opendasharchive.openarchive.services.dropbox.DropboxSiteController
import net.opendasharchive.openarchive.services.webdav.WebDAVSiteController
import net.opendasharchive.openarchive.util.Constants
import net.opendasharchive.openarchive.util.Prefs.getUploadWifiOnly
import java.io.IOException
import java.util.*

class PublishService : Service(), Runnable {
    private var isRunning = false
    private var keepUploading = true

    private var mUploadThread: Thread? = null
    private val listControllers = ArrayList<SiteController>()

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel()
        }

        doForeground()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (mUploadThread?.isAlive != true) {
            mUploadThread = Thread(this)
            mUploadThread!!.start()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        keepUploading = false

        for (sc in listControllers) {
            sc.cancel()
        }
        listControllers.clear()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun shouldPublish(): Boolean {
        if (isNetworkAvailable(getUploadWifiOnly())) {
            return true
        }

        // Try again when there is a network.
        scheduleJob(this)
        return false
    }

    override fun run() {
        if (!isRunning) {
            doPublish()
        }

        stopSelf()
    }

    private fun doPublish() {
        isRunning = true

        // Check if online, and connected to appropriate network type.
        if (shouldPublish()) {

            // Get all media items that are set into queued state.
            var results: List<Media>? = emptyList()
            val datePublish = Date()
            val status = arrayOf(Media.Status.QUEUED, Media.Status.UPLOADING)

            while (keepUploading
                && Media.getMediaByStatus(status, Media.ORDER_PRIORITY).also { results = it }?.isNotEmpty() == true
            ) {
                for (media in results ?: emptyList()) {
                    val coll = Collection.getById(media.collectionId)
                    val projectId = coll?.projectId
                    val proj = if (projectId != null) Project.getById(projectId) else null

                    if (proj != null) {
                        if (media.status != Media.Status.UPLOADING.value) {
                            media.uploadDate = datePublish
                            media.progress = 0 //should we reset this?
                            media.status = Media.Status.UPLOADING.value
                            media.statusMessage = Constants.EMPTY_STRING
                        }

                        media.licenseUrl = proj.licenseUrl

                        try {
                            if (uploadMedia(media)) {
                                coll?.uploadDate = datePublish
                                coll?.save()
                                proj.openCollectionId = -1L
                                proj.save()
                                media.save()
                            }
                        }
                        catch (ioe: IOException) {
                            val err = "error in uploading media: " + ioe.message
                            Log.d(javaClass.simpleName, err, ioe)
                            media.statusMessage = err
                            media.status = Media.Status.ERROR.value
                            media.save()
                        }
                    }
                    else {
                        // Project was deleted, so we should stop uploading.
                        media.status = Media.Status.LOCAL.value
                        media.save()
                    }

                    if (!keepUploading) {
                        return  // Time to end this.
                    }
                }
            }
        }
        isRunning = false
    }

    @Throws(IOException::class)
    private fun uploadMedia(media: Media): Boolean {
        val project = Project.getById(media.projectId)

        if (project == null) {
            media.delete()

            return false
        }

        val valueMap = ArchiveSiteController.getMediaMetadata(this, media)

        val description = project.description
        if (description != null) media.serverUrl = description

        media.status = Media.Status.UPLOADING.value
        media.save()
        notifyMediaUpdated(media)

        val spaceId = project.spaceId ?: -1L

        val space = if (spaceId != -1L) {
            Space.getById(spaceId)
        }
        else {
            getCurrentSpace()
        }

        if (space == null) return true

        val sc = when (space.type) {
            Space.TYPE_WEBDAV -> {
                SiteController.getSiteController(
                    WebDAVSiteController.SITE_KEY, this,
                    UploaderListener(media, this), null
                )
            }
            Space.TYPE_INTERNET_ARCHIVE -> {
                SiteController.getSiteController(
                    ArchiveSiteController.SITE_KEY, this,
                    UploaderListener(media, this), null
                )
            }
            Space.TYPE_DROPBOX -> {
                SiteController.getSiteController(
                    DropboxSiteController.SITE_KEY, this,
                    UploaderListener(media, this), null
                )
            }
            else -> null
        }

        if (sc != null) {
            listControllers.add(sc)
            sc.upload(space, media, valueMap)
        }

        return true
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(requireWifi: Boolean): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        val info = cm?.activeNetworkInfo

        if (info?.isConnected != true) {
            return false
        }

        // Network is present and connected.
        return if (requireWifi) {
            info.type == ConnectivityManager.TYPE_WIFI
        } else true
    }

    /**
     * Send an Intent with an action named "custom-event-name". The Intent sent should
     * be received by the ReceiverActivity.
     */
    private fun notifyMediaUpdated(media: Media) {
        val intent = Intent(MainActivity.INTENT_FILTER_NAME)
        intent.putExtra(SiteController.MESSAGE_KEY_MEDIA_ID, media.id)
        intent.putExtra(SiteController.MESSAGE_KEY_STATUS, media.status)
        intent.putExtra(SiteController.MESSAGE_KEY_PROGRESS, media.progress)

        Log.d(javaClass.simpleName, intent.toString())

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val mChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW
        )
        mChannel.description = getString(R.string.app_subtext)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mChannel.setShowBadge(false)
        mChannel.lockscreenVisibility = Notification.VISIBILITY_SECRET

        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        nm?.createNotificationChannel(mChannel)
    }

    @Synchronized
    private fun doForeground() {
        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE
        }

        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), flags)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_notify)
            .setContentTitle(getString(R.string.app_name))
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1337, notification)
    }

    companion object {
        private const val MY_BACKGROUND_JOB = 0
        private const val NOTIFICATION_CHANNEL_ID = "oasave_channel_1"

        fun scheduleJob(context: Context) {
            val job = JobInfo.Builder(MY_BACKGROUND_JOB,
                ComponentName(context, PublishJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresCharging(false)
                .build()

            val js = context.getSystemService(JOB_SCHEDULER_SERVICE) as? JobScheduler
            js?.schedule(job)
        }
    }
}