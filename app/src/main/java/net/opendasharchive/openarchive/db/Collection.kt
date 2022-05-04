package net.opendasharchive.openarchive.db

import com.orm.SugarRecord
import net.opendasharchive.openarchive.util.Constants.EMPTY_STRING
import java.util.Date

data class Collection(
        var projectId: Long? = null,
        var uploadDate: Date? = null,
        var serverUrl: String? = null
) : SugarRecord() {

    companion object {

        fun getAllAsList(): List<Collection>? {
            return find(Collection::class.java, EMPTY_STRING, arrayOf(), EMPTY_STRING, "ID DESC", EMPTY_STRING)
        }

        fun getById(id: Long): Collection? {
            return findById(Collection::class.java, id)
        }

        fun getAllAsListByProject(projectId: Long): List<Collection>? {
            val values = arrayOf(projectId.toString() + EMPTY_STRING)
            return find(Collection::class.java, "PROJECT_ID = ?", values, null, "ID DESC", null)
        }
    }
}