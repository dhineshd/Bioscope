package com.trioscope.chameleon.types;

import java.net.InetAddress;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Created by dhinesh.dharman on 7/2/15.
 */
@Builder
@Getter
@ToString
public class PeerInfo {
    public enum Role{
        DIRECTOR, CREW_MEMBER
    }
    @NonNull
    private InetAddress ipAddress;
    @NonNull
    private Integer port;
    @NonNull
    private Role role;
}
