package net.opendasharchive.openarchive.publish

import android.content.Context
import android.content.Intent
import android.os.Message
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.scal.secureshareui.controller.SiteController
import io.scal.secureshareui.controller.SiteControllerListener
import net.opendasharchive.openarchive.MainActivity
import net.opendasharchive.openarchive.db.Media

class UploaderListener(
    private val uploadMedia: Media,
    private val lbm: LocalBroadcastManager
) : SiteControllerListener {

    constructor(uploadMedia: Media, context: Context) : this(uploadMedia, LocalBroadcastManager.getInstance(context))

    override fun success(msg: Message?) {
        uploadMedia.progress = uploadMedia.contentLength
        uploadMedia.status = Media.Status.UPLOADED.value
        uploadMedia.save()

        Log.d(this.javaClass.simpleName, "${uploadMedia.id} upload finished")

        notifyMediaUpdated()
    }

    override fun progress(msg: Message?) {
        val contentLengthUploaded = msg?.data?.getLong(SiteController.MESSAGE_KEY_PROGRESS) ?: 0

        Log.d(this.javaClass.simpleName,
            "${uploadMedia.id} uploaded: ${contentLengthUploaded}/${uploadMedia.contentLength}")

        uploadMedia.progress = contentLengthUploaded

        notifyMediaUpdated()
    }

    override fun failure(msg: Message?) {
        val data = msg?.data

        val errorCode = data?.getInt(SiteController.MESSAGE_KEY_CODE)
        val errorMessage = data?.getString(SiteController.MESSAGE_KEY_MESSAGE)
        val error = "Error $errorCode: $errorMessage"

        Log.d(this.javaClass.simpleName, "upload error: $error")

        uploadMedia.statusMessage = error
        uploadMedia.status = Media.Status.ERROR.value
        uploadMedia.save()

        notifyMediaUpdated()
    }

    // Send an Intent with an action named "custom-event-name". The Intent sent should
    // be received by the ReceiverActivity.
    private fun notifyMediaUpdated() {
        val intent = Intent(MainActivity.INTENT_FILTER_NAME)
        // You can also include some extra data.
        intent.putExtra(SiteController.MESSAGE_KEY_MEDIA_ID, uploadMedia.id)
        intent.putExtra(SiteController.MESSAGE_KEY_STATUS, uploadMedia.status)
        intent.putExtra(SiteController.MESSAGE_KEY_PROGRESS, uploadMedia.progress)

        lbm.sendBroadcast(intent)
    }
}