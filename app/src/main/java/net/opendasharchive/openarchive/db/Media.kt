@file:Suppress("unused")

package net.opendasharchive.openarchive.db

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.orm.SugarRecord
import net.opendasharchive.openarchive.util.Constants.EMPTY_STRING
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

data class Media(
    var originalFilePath: String = EMPTY_STRING,
    var mimeType: String = EMPTY_STRING,
    var createDate: Date? = null,
    var updateDate: Date? = null,
    var uploadDate: Date? = null,
    var serverUrl: String = EMPTY_STRING,
    var title: String = EMPTY_STRING,
    var description: String = EMPTY_STRING,
    var author: String = EMPTY_STRING,
    var location: String = EMPTY_STRING,
    private var tags: String = EMPTY_STRING,
    var licenseUrl: String? = null,

    @SerializedName(value = "mediaHashBytes")
    var mediaHash: ByteArray = byteArrayOf(),

    @SerializedName(value = "mediaHash")
    var mediaHashString: String = EMPTY_STRING,

    var status: Int = 0,
    var statusMessage: String = EMPTY_STRING,
    var projectId: Long = 0,
    var collectionId: Long = 0,
    var contentLength: Long = 0,
    var progress: Long = 0,
    var flag: Boolean = false,
    var priority: Int = 0,
    var selected: Boolean = false
) : SugarRecord() {

    enum class MediaType {
        AUDIO, IMAGE, VIDEO, FILE
    }

    fun getFormattedCreateDate(): String {
        return createDate?.let {
            SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT).format(it)
        } ?: EMPTY_STRING
    }

    fun getTags(): String {
        return tags
    }

    fun setTags(tags: String) {
        // repace spaces and commas with semicolons
        this.tags = tags.replace(' ', ';').replace(',', ';')
    }

    fun getAllMediaAsList(): List<Media>? {
        return find(
            Media::class.java,
            "status <= ?",
            WHERE_NOT_DELETED,
            EMPTY_STRING,
            "ID DESC",
            EMPTY_STRING
        )
    }

    fun getMediaByProjectAndUploadDate(projectId: Long, uploadDate: Long): List<Media>? {
        val values =
            arrayOf(projectId.toString() + EMPTY_STRING, uploadDate.toString() + EMPTY_STRING)
        return find(
            Media::class.java,
            "PROJECT_ID = ? AND UPLOAD_DATE = ?",
            values,
            EMPTY_STRING,
            "STATUS, ID DESC",
            EMPTY_STRING
        )
    }

    fun getMediaByProjectAndStatus(
        projectId: Long,
        statusMatch: String,
        status: Long
    ): List<Media>? {
        val values = arrayOf(projectId.toString() + EMPTY_STRING, status.toString() + EMPTY_STRING)
        return find(
            Media::class.java,
            "PROJECT_ID = ? AND STATUS $statusMatch ?",
            values,
            EMPTY_STRING,
            "STATUS, ID DESC",
            EMPTY_STRING
        )
    }

    fun deleteMediaById(mediaId: Long): Boolean {
        val media: Media = findById(Media::class.java, mediaId)
        return media.delete()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Media

        if (originalFilePath != other.originalFilePath) return false
        if (mimeType != other.mimeType) return false
        if (createDate != other.createDate) return false
        if (updateDate != other.updateDate) return false
        if (uploadDate != other.uploadDate) return false
        if (serverUrl != other.serverUrl) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (author != other.author) return false
        if (location != other.location) return false
        if (tags != other.tags) return false
        if (licenseUrl != other.licenseUrl) return false
        if (!mediaHash.contentEquals(other.mediaHash)) return false
        if (mediaHashString != other.mediaHashString) return false
        if (status != other.status) return false
        if (statusMessage != other.statusMessage) return false
        if (projectId != other.projectId) return false
        if (collectionId != other.collectionId) return false
        if (contentLength != other.contentLength) return false
        if (progress != other.progress) return false
        if (flag != other.flag) return false
        if (priority != other.priority) return false
        if (selected != other.selected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = originalFilePath.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (createDate?.hashCode() ?: 0)
        result = 31 * result + (updateDate?.hashCode() ?: 0)
        result = 31 * result + (uploadDate?.hashCode() ?: 0)
        result = 31 * result + serverUrl.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + (licenseUrl?.hashCode() ?: 0)
        result = 31 * result + mediaHash.contentHashCode()
        result = 31 * result + mediaHashString.hashCode()
        result = 31 * result + status
        result = 31 * result + statusMessage.hashCode()
        result = 31 * result + projectId.hashCode()
        result = 31 * result + collectionId.hashCode()
        result = 31 * result + contentLength.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + flag.hashCode()
        result = 31 * result + priority
        result = 31 * result + selected.hashCode()
        return result
    }

    enum class Status(val value: Int) {
        NEW(0),
        LOCAL(1),
        QUEUED(2),
        PUBLISHED(3),
        UPLOADING(4),
        UPLOADED(5),
        DELETE_REMOTE(7),
        ERROR(9);

        override fun toString(): String {
            return value.toString()
        }
    }
    companion object {
        const val ORDER_PRIORITY = "PRIORITY DESC"
        private val WHERE_NOT_DELETED = arrayOf(Status.UPLOADED.value.toString())
        private const val PRIORITY_DESC = "priority DESC"
        private const val ORDER_STATUS_AND_PRIORITY = "STATUS, PRIORITY DESC"


        fun getMediaByProjectAndCollection(projectId: Long, collectionId: Long): List<Media>? {
            val values =
                arrayOf(projectId.toString() + EMPTY_STRING, collectionId.toString() + EMPTY_STRING)
            return find(
                Media::class.java,
                "PROJECT_ID = ? AND COLLECTION_ID = ?",
                values,
                EMPTY_STRING,
                "STATUS, ID DESC",
                EMPTY_STRING
            )
        }

        fun getMediaByStatus(status: Status): List<Media>? {
            return find(
                Media::class.java,
                "status = ?",
                arrayOf(status.toString()),
                EMPTY_STRING,
                "STATUS DESC",
                EMPTY_STRING
            )
        }

        fun getMediaByStatus(statuses: Array<Status>, order: String?): List<Media>? {
            return find(
                Media::class.java,
                statuses.joinToString(", ", "status IN (", ")") { "?" },
                statuses.map { it.toString() }.toTypedArray(),
                EMPTY_STRING,
                order,
                EMPTY_STRING
            )
        }

        fun getMediaById(mediaId: Long): Media {
            return findById(Media::class.java, mediaId)
        }

        fun getMediaByProject(projectId: Long): List<Media>? {
            val values = arrayOf(projectId.toString() + EMPTY_STRING)
            return find(
                Media::class.java,
                "PROJECT_ID = ?",
                values,
                EMPTY_STRING,
                "STATUS, ID DESC",
                EMPTY_STRING
            )
        }

        fun serializeToJson(media: Media): String {
            return try {
                Gson().toJson(media)
            } catch (ex: Exception) {
                EMPTY_STRING
            }
        }

        fun deserializeFromJson(json: String): Media? {
            return try {
                Gson().fromJson(json, Media::class.java)
            } catch (ex: Exception) {
                null
            }
        }
    }
}