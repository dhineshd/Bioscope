package com.trioscope.chameleon.stream;

import com.trioscope.chameleon.stream.messages.PeerMessage;

import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by dhinesh.dharman on 9/28/15.
 */
public class ServerEventListenerManager {
    private volatile Set<ServerEventListener> listeners = new HashSet<>();

    public void onClientRequestReceived(Socket clientSocket, PeerMessage messageFromClient) {
        for (ServerEventListener listener : listeners)
            listener.onClientRequest(clientSocket, messageFromClient);
    }

    public synchronized void addListener(ServerEventListener listener) {
        listeners.add(listener);
    }

    public synchronized void removeListener(ServerEventListener listener) {
        listeners.remove(listener);
    }
}
