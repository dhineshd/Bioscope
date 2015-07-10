package com.trioscope.chameleon.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

/**
 * Created by rohitraghunathan on 7/9/15.
 */
public class EnableNfcAndAndroidBeamDialogFragment extends DialogFragment {

    private static final String NFC_ENABLED = "nfcEnabled";
    private static final String ANDROID_BEAM_ENABLED = "androidBeamEnabled";

    public static EnableNfcAndAndroidBeamDialogFragment newInstance(boolean nfcEnabled, boolean androidBeamEnabled) {

        EnableNfcAndAndroidBeamDialogFragment frag = new EnableNfcAndAndroidBeamDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(NFC_ENABLED, nfcEnabled);
        args.putBoolean(ANDROID_BEAM_ENABLED, androidBeamEnabled);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final boolean nfcEnabled = getArguments().getBoolean(NFC_ENABLED);
        final boolean androidBeamEnabled = getArguments().getBoolean(ANDROID_BEAM_ENABLED);

        StringBuilder msg = new StringBuilder();

        if(!nfcEnabled && !androidBeamEnabled) {
            msg.append("Please Enable NFC and Andorid Beam");
        } else if(!androidBeamEnabled) {
            msg.append("Please Enable Andorid Beam");
        } else if(!nfcEnabled) {
            msg.append("Please Enable NFC");
        }

        return new AlertDialog.Builder(getActivity())
                        //TODO: Choose appropriate message & move it to strings.xml
                .setMessage(msg.toString())
                .setPositiveButton("Open Settings",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (!nfcEnabled) {
                                    startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                                } else if (!androidBeamEnabled) {
                                    startActivity(new Intent(Settings.ACTION_NFCSHARING_SETTINGS));
                                }
                            }
                        }
                )
                .create();
    }
}
