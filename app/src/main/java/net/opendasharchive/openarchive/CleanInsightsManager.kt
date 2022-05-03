package net.opendasharchive.openarchive

import android.app.Activity
import android.content.Context
import com.maxkeppeler.sheets.info.InfoSheet
import org.cleaninsights.sdk.*
import java.io.IOException

open class CleanInsightsManager {

    companion object {
        private const val CI_CAMPAIGN = "upload-failures"
    }

    private var mMeasure: CleanInsights? = null

    fun initMeasurement(context: Context) {
        if (mMeasure == null) {
            // Instantiate with configuration and directory to write store to, best in an `Application` subclass.
            try {
                mMeasure = CleanInsights(
                    context.assets.open("cleaninsights.json").reader().readText(),
                    context.filesDir)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // Only use this during development.
//        mMeasure?.testServer {
//            Log.d("CI Test Server", it?.stackTraceToString() ?: "success")
//        }
    }

    fun hasConsent(): Boolean {
        return mMeasure?.isCampaignCurrentlyGranted(CI_CAMPAIGN) ?: false
    }

    fun getConsent(context: Activity) {
        mMeasure?.requestConsent(CI_CAMPAIGN, object : JavaConsentRequestUi {

            override fun show(campaignId: String, campaign: Campaign, handler: ConsentRequestUiCompletionHandler) {

                InfoSheet().show(context) {
                    title(context.getString(R.string.ci_title))
                    content(context.getString(R.string.clean_insight_consent_prompt))
                    onNegative(context.getString(R.string.ci_negative)) {
                        // Handle event
                        handler.completed(false)
                    }
                    onPositive(context.getString(R.string.ci_confirm)) {
                        // Handle event
                        handler.completed(true)
                        mMeasure?.grant(CI_CAMPAIGN)
                    }
                }
            }

            override fun show(feature: Feature, handler: ConsentRequestUiCompletionHandler) {
                handler.completed(false)
            }
        })
    }

    fun measureView(view: String) {
        mMeasure?.measureVisit(arrayListOf(view), CI_CAMPAIGN)
    }

    fun measureEvent(key: String, value: String) {
        mMeasure?.measureEvent(key, value, CI_CAMPAIGN)
    }
}
