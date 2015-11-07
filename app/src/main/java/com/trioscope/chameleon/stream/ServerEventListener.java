package com.trioscope.chameleon.stream;

import com.trioscope.chameleon.types.PeerMessage;

import java.net.Socket;

/**
 * Created by dhinesh.dharman on 7/2/15.
 */
public interface ServerEventListener {
    void onClientRequest(Socket clientSocket, PeerMessage messageFromClient);
}
