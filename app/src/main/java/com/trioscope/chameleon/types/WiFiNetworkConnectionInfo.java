package com.trioscope.chameleon.types;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Created by rohitraghunathan on 6/28/15.
 */

@ToString
@Getter
@Setter
public class WiFiNetworkConnectionInfo {

    String ssid;

    String WifiP2pPassPhrase;

    String serverIpAddress;

    String serverPort;
}
