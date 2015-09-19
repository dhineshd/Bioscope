package com.trioscope.chameleon.activity;

import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.ReceiveConnectionInfoFragment;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveConnectionInfoNFCActivity extends EnableForegroundDispatchForNFCMessageActivity {

    private Gson gson = new Gson();
    private TextView mTextViewConnectionStatus;
    private TextView mTextViewNfcInstructions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_connection_info_nfc);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        // Tear down Wifi hotspot since we are going to join
        // the peer's hotspot.
        ((ChameleonApplication)getApplication()).tearDownWifiHotspot();

        mTextViewNfcInstructions = (TextView) findViewById(R.id.textView_nfc_instructions);
        mTextViewConnectionStatus = (TextView) findViewById(R.id.textView_receiver_connection_status);

        log.debug("ReceiveConnectionInfoNFCActivity {}", this);

        ((ChameleonApplication)getApplication()).startConnectionServerIfNotRunning();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_connection_receive_nfc, menu);
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
    public void onResume() {
        super.onResume();

        String connectionInfoAsJson = getIntent().getStringExtra(ConnectionEstablishedActivity.CONNECTION_INFO_AS_JSON_EXTRA);

        if(connectionInfoAsJson != null) {

            if(mTextViewConnectionStatus.getVisibility() == TextView.INVISIBLE) {
                mTextViewConnectionStatus.setVisibility(TextView.VISIBLE);
            }

            if(mTextViewNfcInstructions.getVisibility() == TextView.VISIBLE) {
                mTextViewNfcInstructions.setVisibility(TextView.INVISIBLE);
            }

            final WiFiNetworkConnectionInfo connectionInfo =
                gson.fromJson(connectionInfoAsJson, WiFiNetworkConnectionInfo.class);

            // find the fragment from previous instance of activity (if any)
            FragmentManager fm = getFragmentManager();
            ReceiveConnectionInfoFragment fragment = (ReceiveConnectionInfoFragment) fm.findFragmentById(R.id.fragment_receive_connection_info);
            fragment.enableWifiAndEstablishConnection(connectionInfo);
        } else {
            log.warn("connectionInfoAsJson is null");
        }

    }

    @Override
    public void onBackPressed() {

        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity= new Intent(this, MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
        super.onBackPressed();
    }

}
