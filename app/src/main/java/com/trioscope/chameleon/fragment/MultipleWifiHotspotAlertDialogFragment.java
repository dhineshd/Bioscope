package com.trioscope.chameleon.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

/**
 * Created by rohitraghunathan on 7/5/15.
 */
public class MultipleWifiHotspotAlertDialogFragment extends DialogFragment {

    public static MultipleWifiHotspotAlertDialogFragment newInstance(WiFiNetworkConnectionInfo info) {

        MultipleWifiHotspotAlertDialogFragment frag = new MultipleWifiHotspotAlertDialogFragment();
        Bundle args = new Bundle();
        //TODO: Send User Details from source here instead of SSID
        args.putString("SourceSSID", info.getSSID());
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String sourceSSID = getArguments().getString("SourceSSID", "Chameleon");

        return new AlertDialog.Builder(getActivity())
                .setTitle(sourceSSID)
                //TODO: Choose appropriate message & move it to strings.xml
                .setMessage(sourceSSID + " is also trying to be the Director. There can only be one Director.")
                .setPositiveButton("Got it",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                               // ((FragmentAlertDialog)getActivity()).doPositiveClick();
                            }
                        }
                )
                .create();
    }

}
