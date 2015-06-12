package com.trioscope.chameleon.network;

import java.net.InetAddress;

import lombok.Getter;

/**
 * Created by dhinesh.dharman on 6/10/15.
 */
@Getter
public class RtspClientConnection {
    private InetAddress address;
    private int rtpPort;
    private int rtcpPort;


    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getRtpPort() {
        return rtpPort;
    }

    public void setRtpPort(int rtpPort) {
        this.rtpPort = rtpPort;
    }

    public int getRtcpPort() {
        return rtcpPort;
    }

    public void setRtcpPort(int rtcpPort) {
        this.rtcpPort = rtcpPort;
    }
}
