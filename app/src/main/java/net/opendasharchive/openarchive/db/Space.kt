package net.opendasharchive.openarchive.db

import android.text.TextUtils
import com.orm.SugarRecord
import net.opendasharchive.openarchive.util.Constants.EMPTY_STRING
import net.opendasharchive.openarchive.util.Prefs

data class Space(
        var type: Int = 0,
        var name: String = EMPTY_STRING,
        var username: String = EMPTY_STRING,
        var password: String = EMPTY_STRING,
        var host: String = EMPTY_STRING
) : SugarRecord() {

    companion object {
        const val TYPE_WEBDAV = 0
        const val TYPE_INTERNET_ARCHIVE = 1
        const val TYPE_PIRATEBOX = 2
        const val TYPE_DROPBOX = 3
        const val TYPE_DAT = 4
        const val TYPE_SCP = 5

        fun getAllAsList(): Iterator<Space>? {
            return findAll(Space::class.java)
        }

        fun getById(id: Long): Space? {
            val space = findById(Space::class.java, id)

            if (space != null && TextUtils.isEmpty(space.name)) space.name = space.username

            return space
        }

        fun getCurrentSpace(): Space? {
            return getById(Prefs.getCurrentSpaceId())
        }
    }
}