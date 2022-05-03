package net.opendasharchive.openarchive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.facebook.imagepipeline.decoder.SimpleProgressiveJpegConfig
import com.orm.SugarApp
import com.orm.SugarRecord.findById
import info.guardianproject.netcipher.client.StrongBuilder
import info.guardianproject.netcipher.client.StrongOkHttpClientBuilder
import info.guardianproject.netcipher.proxy.OrbotHelper
import net.opendasharchive.openarchive.db.Space
import net.opendasharchive.openarchive.publish.PublishService
import net.opendasharchive.openarchive.util.Prefs
import net.opendasharchive.openarchive.util.Prefs.TRACK_LOCATION
import net.opendasharchive.openarchive.util.Prefs.getCurrentSpaceId
import okhttp3.OkHttpClient
import org.acra.ACRA
import org.acra.ReportField
import org.acra.annotation.AcraCore
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.StringFormat

@AcraCore(buildConfigClass = BuildConfig::class)
class OpenArchiveApp : SugarApp() {

    @Volatile
    var orbotConnected = false


    private val mCleanInsights = CleanInsightsManager()

    private var mCurrentSpace: Space? = null

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()

        val config = ImagePipelineConfig.newBuilder(this)
            .setProgressiveJpegConfig(SimpleProgressiveJpegConfig())
            .setResizeAndRotateEnabledForNetwork(true)
            .setDownsampleEnabled(true)
            .build()

        Fresco.initialize(this, config)
        Prefs.setContext(this)

        // Disable proofmode GPS dat tracking by default.
        Prefs.putBoolean(TRACK_LOCATION, false)

        if (getUseTor() && OrbotHelper.isOrbotInstalled(this))
            initNetCipher(this)

        initCrashReporting()

        uploadQueue()
    }

    private fun initCrashReporting() {
        val builder = CoreConfigurationBuilder(this)
            .setBuildConfigClass(BuildConfig::class.java)
            .setReportFormat(StringFormat.KEY_VALUE_LIST)
            .setReportContent( //ReportField.USER_COMMENT,
                ReportField.REPORT_ID,
                ReportField.APP_VERSION_NAME,
                ReportField.APP_VERSION_CODE,
                ReportField.ANDROID_VERSION,
                ReportField.PHONE_MODEL,
                ReportField.PACKAGE_NAME,
                ReportField.CRASH_CONFIGURATION,
                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE,
                ReportField.APPLICATION_LOG,
                ReportField.BUILD
            )
        // The following line triggers the initialization of ACRA
        ACRA.init(this, builder)
    }

    fun uploadQueue() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, PublishService::class.java))
        } else {
            startService(Intent(this, PublishService::class.java))
        }
    }

    fun initNetCipher(context: Context) {
        val LOG_TAG = "NetCipherClient"
        Log.i(LOG_TAG, "Initializing NetCipher client")
        val appContext = context.applicationContext
        val oh = OrbotHelper.get(appContext)
        if (BuildConfig.DEBUG) {
            oh.skipOrbotValidation()
        }
        if (oh.init()) {
            orbotConnected = true
        }
        try {
            StrongOkHttpClientBuilder.forMaxSecurity(appContext)
                .build(object : StrongBuilder.Callback<OkHttpClient?> {
                    override fun onConnected(okHttpClient: OkHttpClient?) {
                        orbotConnected = true
                        Log.i("NetCipherClient", "Connection to orbot established!")
                        // from now on, you can create upload requests
                        // as usual, and they will be proxied through TOR.
                        // Bear in mind that
                    }

                    override fun onConnectionException(exc: Exception) {
                        orbotConnected = false
                        Log.e("NetCipherClient", "onConnectionException()", exc)
                    }

                    override fun onTimeout() {
                        orbotConnected = false
                        Log.e("NetCipherClient", "onTimeout()")
                    }

                    override fun onInvalid() {
                        orbotConnected = false
                        Log.e("NetCipherClient", "onInvalid()")
                    }
                })
        } catch (exc: Exception) {
            Log.e("Error", "Error while initializing TOR Proxy OkHttpClient", exc)
        }
    }


    @Synchronized
    fun getCurrentSpace(): Space? {
        if (mCurrentSpace == null) {
            val spaceId = getCurrentSpaceId()
            if (spaceId != -1L) {
                mCurrentSpace = findById(Space::class.java, spaceId)
            }
        }
        return mCurrentSpace
    }


    fun getUseTor(): Boolean {
        return orbotConnected
    }

    fun hasCleanInsightsConsent(): Boolean {
        return mCleanInsights.hasConsent()
    }

    fun showCleanInsightsConsent(activity: Activity) {
        mCleanInsights.getConsent(activity)
    }

    fun measureView(viewId: String) {
        mCleanInsights.measureView(viewId)
    }

    fun measureEvent(key: String, value: String) {
        mCleanInsights.measureEvent(key, value)
    }
}