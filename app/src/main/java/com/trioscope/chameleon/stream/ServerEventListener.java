package com.trioscope.chameleon.stream;

import java.net.Socket;

/**
 * Created by dhinesh.dharman on 7/2/15.
 */
public interface ServerEventListener {
    void onClientConnectionRequest(Socket clientSocket);
    void onClientConnectionTerminated();
}
