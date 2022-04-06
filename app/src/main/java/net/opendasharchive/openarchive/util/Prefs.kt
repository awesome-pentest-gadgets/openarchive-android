package net.opendasharchive.openarchive.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object Prefs{

    const val PREF_UPLOAD_WIFI_ONLY = "upload_wifi_only"
    const val PREF_NEARBY_USE_BLUETOOTH = "nearby_use_bluetooth"
    const val PREF_NEARBY_USE_WIFI = "nearby_use_wifi"
    const val PREF_USE_TOR = "use_tor"
    const val PREF_USE_PROOFMODE = "use_proofmode"
    const val PREF_USE_NEXTCLOUD_CHUNKING = "upload_nextcloud_chunks"
    const val PREF_CURRENT_SPACE_ID = "current_space"
    const val TRACK_LOCATION = "trackLocation"

    private var prefs: SharedPreferences? = null

    fun setContext(context: Context) {
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    fun putBoolean(key: String?, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun getBoolean(key: String?): Boolean {
        return prefs?.getBoolean(key, false) ?: false
    }


    fun putString(key: String?, value: String?) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun useNextcloudChunking(): Boolean {
        return prefs?.getBoolean(PREF_USE_NEXTCLOUD_CHUNKING, false) ?: false
    }

    fun getUploadWifiOnly(): Boolean {
        return prefs?.getBoolean(PREF_UPLOAD_WIFI_ONLY, false) ?: false
    }

    fun setUploadWifiOnly(wifiOnly: Boolean) {
        putBoolean(PREF_UPLOAD_WIFI_ONLY, wifiOnly)
    }

    fun getNearbyUseBluetooth(): Boolean {
        return prefs?.getBoolean(PREF_NEARBY_USE_BLUETOOTH, false) ?: false
    }

    fun setNearbyUseBluetooth(useBluetooth: Boolean) {
        putBoolean(PREF_NEARBY_USE_BLUETOOTH, useBluetooth)
    }

    fun getNearbyUseWifi(): Boolean {
        return prefs?.getBoolean(PREF_NEARBY_USE_WIFI, false) ?: false
    }

    fun setNearbyUseWifi(useWifi: Boolean) {
        putBoolean(PREF_NEARBY_USE_WIFI, useWifi)
    }

    fun getUseProofMode(): Boolean {
        return prefs?.getBoolean(PREF_USE_PROOFMODE, false) ?: false
    }

    fun getUseTor(): Boolean {
        return prefs?.getBoolean(PREF_USE_TOR, false) ?: false
    }

    fun setUseTor(useTor: Boolean) {
        putBoolean(PREF_USE_TOR, useTor)
    }

    fun getCurrentSpaceId(): Long {
        return prefs?.getLong(PREF_CURRENT_SPACE_ID, -1L) ?: -1L
    }

    fun setCurrentSpaceId(spaceId: Long) {
        prefs?.edit()?.putLong(PREF_CURRENT_SPACE_ID, spaceId)?.commit()
    }

}