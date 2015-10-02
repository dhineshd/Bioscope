package com.trioscope.chameleon.util.network;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Created by dhinesh.dharman on 10/2/15.
 */
public class IpUtil {
    private static final int MAX_WAIT_TIME_MSEC_FOR_IP_TO_BE_REACHABLE = 10000;

    /**
     * Check if IP is reachable.
     *
     * @param ipAddress
     * @return true if reachable, false otherwise
     * @throws IOException
     */
    public static boolean isIpReachable(final InetAddress ipAddress)
            throws IOException {
        // Wait till we can reach the remote host. May take time to refresh ARP cache
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < MAX_WAIT_TIME_MSEC_FOR_IP_TO_BE_REACHABLE) {
            if (ipAddress.isReachable(1000)) {
                return true;
            }
        }
        return false;
    }
}
