package com.trioscope.chameleon.types;

import java.net.InetAddress;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 7/2/15.
 */
@Builder
@Getter
public class PeerInfo {
    @NonNull
    private InetAddress ipAddress;
    @NonNull
    private Integer port;
}
