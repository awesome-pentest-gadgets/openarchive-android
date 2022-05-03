package net.opendasharchive.openarchive.features.media

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orm.SugarRecord.find
import com.orm.SugarRecord.findById
import io.scal.secureshareui.controller.ArchiveSiteController
import io.scal.secureshareui.controller.SiteController
import net.opendasharchive.openarchive.MainActivity
import net.opendasharchive.openarchive.db.Collection
import net.opendasharchive.openarchive.db.Media
import net.opendasharchive.openarchive.db.Project
import net.opendasharchive.openarchive.db.Project.Companion.getById
import net.opendasharchive.openarchive.db.Space
import net.opendasharchive.openarchive.db.Space.Companion.getCurrentSpace
import net.opendasharchive.openarchive.publish.UploaderListener
import net.opendasharchive.openarchive.services.dropbox.DropboxSiteController.Companion.SITE_KEY
import net.opendasharchive.openarchive.services.webdav.WebDAVSiteController
import net.opendasharchive.openarchive.util.Constants
import java.util.*

class MediaWorker(private val ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val datePublish = Date()

        val where = "status IN (?, ?)"
        val whereArgs = arrayOf(Media.Status.QUEUED.toString(), Media.Status.UPLOADING.toString())

        try {
            val results = find(
                Media::class.java, where, whereArgs, null, "priority DESC", null
            )

            results?.map { media ->
                val coll = findById(Collection::class.java, media.collectionId)
                val proj = findById(Project::class.java, coll.projectId )

                proj?.let {
                    if (media.status != Media.Status.UPLOADING.value) {
                        media.uploadDate = datePublish
                        media.progress = 0 //should we reset this?
                        media.status = Media.Status.UPLOADING.value
                        media.statusMessage = Constants.EMPTY_STRING
                    }

                    media.licenseUrl = proj.licenseUrl

                    val project = getById(media.projectId)
                    project?.let {
                        val valueMap = ArchiveSiteController.getMediaMetadata(ctx, media)
                        media.serverUrl = project.description ?: Constants.EMPTY_STRING
                        media.status = Media.Status.UPLOADING.value
                        media.save()
                        notifyMediaUpdated(media)

                        val space: Space? = if (project.spaceId != -1L) findById(
                            Space::class.java, project.spaceId
                        ) else getCurrentSpace()

                        space?.let {

                            var sc: SiteController? = null

                            try {
                                when (space.type) {
                                    Space.TYPE_WEBDAV -> sc =
                                        SiteController.getSiteController(
                                            WebDAVSiteController.SITE_KEY,
                                            ctx,
                                            UploaderListener(media, ctx),
                                            null)

                                    Space.TYPE_INTERNET_ARCHIVE -> sc =
                                        SiteController.getSiteController(
                                            ArchiveSiteController.SITE_KEY,
                                            ctx,
                                            UploaderListener(media, ctx),
                                            null)

                                    Space.TYPE_DROPBOX -> sc =
                                        SiteController.getSiteController(
                                            SITE_KEY,
                                            ctx,
                                            UploaderListener(media, ctx),
                                            null)
                                }
                                val result = sc?.upload(space, media, valueMap)
                                if (result == true) {
                                    if (coll != null) {
                                        coll.uploadDate = datePublish
                                        coll.save()
                                        proj.openCollectionId = -1L
                                        proj.save()
                                    }
                                    media.save()
                                } else {
                                    return Result.failure()
                                }
                            } catch (ex: Exception) {
                                val err = "error in uploading media: " + ex.message
                                Log.d(javaClass.name, err, ex)

                                media.statusMessage = err

                                media.status = Media.Status.ERROR.value
                                media.save()
                                Result.failure()
                            }
                        }
                    } ?: run {
                        media.delete()
                        return Result.failure()
                    }
                } ?: run {
                    media.status = Media.Status.LOCAL.value
                }
            }
            return Result.success()
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.failure()
        }
    }

    // Send an Intent with an action named "custom-event-name". The Intent sent should
    // be received by the ReceiverActivity.
    private fun notifyMediaUpdated(media: Media) {
        Log.d("sender", "Broadcasting message")
        val intent = Intent(MainActivity.INTENT_FILTER_NAME)
        // You can also include some extra data.
        intent.putExtra(SiteController.MESSAGE_KEY_MEDIA_ID, media.id)
        intent.putExtra(SiteController.MESSAGE_KEY_STATUS, media.status)
        intent.putExtra(SiteController.MESSAGE_KEY_PROGRESS, media.progress)
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
    }
}