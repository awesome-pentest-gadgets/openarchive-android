package net.opendasharchive.openarchive.publish;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.opendasharchive.openarchive.OpenArchiveApp;
import net.opendasharchive.openarchive.R;
import net.opendasharchive.openarchive.db.Media;
import net.opendasharchive.openarchive.features.media.list.MediaListFragment;

import static io.scal.secureshareui.controller.SiteController.MESSAGE_KEY_MEDIA_ID;
import static io.scal.secureshareui.controller.SiteController.MESSAGE_KEY_PROGRESS;
import static io.scal.secureshareui.controller.SiteController.MESSAGE_KEY_STATUS;
import static net.opendasharchive.openarchive.MainActivity.INTENT_FILTER_NAME;
import static net.opendasharchive.openarchive.util.Constants.EMPTY_ID;
import static net.opendasharchive.openarchive.util.Constants.PROJECT_ID;

import java.util.List;


public class UploadManagerActivity extends AppCompatActivity {

    private MediaListFragment mFrag;
    private MenuItem mMenuEdit;

    private boolean isEditMode = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_manager);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
        updateTitle();

        MediaListFragment fragment = (MediaListFragment) getSupportFragmentManager().findFragmentById(R.id.fragUploadManager);
        if (fragment != null) {
            fragment.setProjectId(getIntent().getLongExtra(PROJECT_ID, EMPTY_ID));
            mFrag = fragment;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mFrag.refresh();

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(INTENT_FILTER_NAME));
    }

    @Override
    protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    /**
     * Our handler for received Intents. This will be called whenever an Intent
     * with an action named "custom-event-name" is broadcasted.
     */
    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            Log.d("receiver", "Updating media");

            int status = intent.getIntExtra(MESSAGE_KEY_STATUS, -1);

            switch (status) {
                case Media.STATUS_UPLOADED:
                    mFrag.refresh();

                case Media.STATUS_UPLOADING:
                    long mediaId = intent.getLongExtra(MESSAGE_KEY_MEDIA_ID, -1);
                    long progress = intent.getLongExtra(MESSAGE_KEY_PROGRESS, -1);

                    if (mediaId != -1) mFrag.updateItem(mediaId, progress);

                case Media.STATUS_ERROR:
                    OpenArchiveApp app = (OpenArchiveApp) getApplication();
                    if (!app.hasCleanInsightsConsent()) app.showCleanInsightsConsent(UploadManagerActivity.this);
            }

            updateTitle();
        }
    };

    public void toggleEditMode() {
        isEditMode = !isEditMode;
        mFrag.setEditMode(isEditMode);
        mFrag.refresh();

        if (isEditMode) {
            mMenuEdit.setTitle(R.string.menu_done);
            stopService(new Intent(this, PublishService.class));

        } else {
            mMenuEdit.setTitle(R.string.menu_edit);
            startService(new Intent(this, PublishService.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_upload, menu);
        mMenuEdit = menu.findItem(R.id.menu_edit);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        if (id == R.id.menu_edit) {
            toggleEditMode();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateTitle() {
        int count = 0;
        List<Media> media = Media.Companion.getMediaByStatus(
                new long[] { Media.STATUS_UPLOADING, Media.STATUS_QUEUED, Media.STATUS_ERROR },
                Media.ORDER_PRIORITY);
        if (media != null) count = media.size();

        String title = getString(R.string.title_uploading_done);
        if (count > 0) title = getString(R.string.title_uploading, count);

        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setTitle(title);
    }
}
