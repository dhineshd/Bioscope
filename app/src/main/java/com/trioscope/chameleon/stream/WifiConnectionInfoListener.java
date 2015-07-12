package com.trioscope.chameleon.stream;

import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

/**
 * Created by dhinesh.dharman on 7/11/15.
 */
public interface WifiConnectionInfoListener {
    void onWifiNetworkCreated(WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo);
}
