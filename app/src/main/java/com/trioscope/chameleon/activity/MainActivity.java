package com.trioscope.chameleon.activity;

import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.RenderRequestFrameListener;
import com.trioscope.chameleon.SurfaceTextureDisplay;
import com.trioscope.chameleon.fragment.EnableNfcAndAndroidBeamDialogFragment;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;
import com.trioscope.chameleon.types.SessionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static android.view.View.OnClickListener;

public class MainActivity extends EnableForegroundDispatchForNFCMessageActivity {
    private static final Logger LOG = LoggerFactory.getLogger(MainActivity.class);
    public MainThreadHandler mainThreadHandler;
    private SurfaceTextureDisplay previewDisplay;
    private ChameleonApplication chameleonApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        chameleonApplication = (ChameleonApplication) getApplication();

        chameleonApplication.updateOrientation();

        mainThreadHandler = new MainThreadHandler(Looper.getMainLooper());

        setContentView(R.layout.activity_main);

        LOG.info("Created main activity");

        chameleonApplication.startConnectionServerIfNotRunning();

        final Button startSessionButton = (Button) findViewById(R.id.button_main_start_session);

        startSessionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                chameleonApplication.setSessionStatus(SessionStatus.STARTED);
                Intent i = new Intent(MainActivity.this, SendConnectionInfoNFCActivity.class);
                startActivity(i);
            }
        });

        final Button joinSessionButton = (Button) findViewById(R.id.button_main_join_session);

        joinSessionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                chameleonApplication.setSessionStatus(SessionStatus.STARTED);
                Intent i = new Intent(MainActivity.this, ReceiveConnectionInfoNFCActivity.class);
                startActivity(i);
            }
        });

        // Tell the application we're ready to show preview whenever
        chameleonApplication.setEglContextCallback(this);
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        LOG.info("onPause: Activity is no longer in foreground");
        if (previewDisplay != null) {
            previewDisplay.onPause();
        }
        // If we are not connected, we can release network resources
        if (SessionStatus.DISCONNECTED.equals(chameleonApplication.getSessionStatus())){
            LOG.info("Teardown initiated from MainActivity");
            ((ChameleonApplication) getApplication()).cleanup();
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        if (previewDisplay != null) {
            previewDisplay.onResume();
        }
        chameleonApplication.startConnectionServerIfNotRunning();

        if(!mNfcAdapter.isEnabled() || !mNfcAdapter.isNdefPushEnabled()) {

            DialogFragment newFragment = EnableNfcAndAndroidBeamDialogFragment.newInstance(
                    mNfcAdapter.isEnabled(), mNfcAdapter.isNdefPushEnabled());
            newFragment.show(getFragmentManager(), "dialog");
        }

        super.onResume();
    }

    @Override
    protected void onStop() {
        LOG.info("onStop: Activity is no longer visible to user");
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        // Not putting this in onDestroy since it does not seem to be called every time
        ((ChameleonApplication) getApplication()).cleanup();
        LOG.info("onBackPressed!");
        //moveTaskToBack(true);
        super.onBackPressed();
        System.exit(0);
    }

    private void createSurfaceTextureWithSharedEglContext(final EGLContextAvailableMessage contextMessage) {
        LOG.info("Creating surface texture with shared EGL Context on thread {}", Thread.currentThread());

        ChameleonApplication chameleonApplication = (ChameleonApplication) getApplication();
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_main_preview);
        previewDisplay = chameleonApplication.generatePreviewDisplay(contextMessage);
        layout.addView(previewDisplay);
        chameleonApplication.getCameraPreviewFrameListener().addFrameListener(new RenderRequestFrameListener(previewDisplay));

    }

    public void eglContextAvailable(EGLContextAvailableMessage eglContextMsg) {
        LOG.info("EGLContext is now available, going to display preview, thread {}", Thread.currentThread());
        createSurfaceTextureWithSharedEglContext(eglContextMsg);
    }

    public class MainThreadHandler extends Handler {
        public static final int EGL_CONTEXT_AVAILABLE = 1;

        public MainThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EGL_CONTEXT_AVAILABLE:
                    LOG.info("EGL Context is available, parameters {}", msg.obj);
                    createSurfaceTextureWithSharedEglContext((EGLContextAvailableMessage) msg.obj);
                    break;
                default:
                    super.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
