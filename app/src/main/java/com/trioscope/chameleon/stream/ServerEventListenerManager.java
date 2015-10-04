package com.trioscope.chameleon.stream;

import com.trioscope.chameleon.stream.messages.PeerMessage;

import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by dhinesh.dharman on 9/28/15.
 */
public class ServerEventListenerManager {
    private volatile ConcurrentMap<ServerEventListener, Boolean> listeners = new ConcurrentHashMap<>();

    public void onClientRequestReceived(Socket clientSocket, PeerMessage messageFromClient) {
        for (ServerEventListener listener : listeners.keySet())
            listener.onClientRequest(clientSocket, messageFromClient);
    }

    public void addListener(ServerEventListener listener) {
        listeners.putIfAbsent(listener, false);
    }

    public void removeListener(ServerEventListener listener) {
        listeners.remove(listener);
    }
}
