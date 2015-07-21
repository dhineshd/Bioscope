package com.trioscope.chameleon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by rohitraghunathan on 7/16/15.
 */
@Slf4j
public class IncomingPhoneCallBroadcastReceiver extends BroadcastReceiver {

    Context context;
    public IncomingPhoneCallBroadcastReceiver(Context context) {
        this.context = context;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        String msg = "Phone state changed to " + state;

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            log.info("ringing");
        } else if(TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            log.info("call answered");
        }

        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

}
