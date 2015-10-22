package com.trioscope.chameleon.types;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Created by rohitraghunathan on 6/28/15.
 */

@ToString
@Getter
@Builder
public class WiFiNetworkConnectionInfo {
    @NonNull
    private String SSID;
    @NonNull
    private String passPhrase;
    @NonNull
    private String serverIpAddress;
    @NonNull
    private Integer serverPort;
    @NonNull
    private String userName;
    @NonNull
    private byte[] certificate;
}
