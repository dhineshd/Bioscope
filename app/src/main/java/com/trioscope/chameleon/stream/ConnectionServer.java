package com.trioscope.chameleon.stream;

import com.google.gson.Gson;
import com.trioscope.chameleon.types.PeerMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Server to handle interaction with peers.
 */
@Slf4j
public class ConnectionServer {
    @NonNull
    private Thread serverThread;
    @NonNull
    private SSLServerSocket serverSocket;
    private Gson gson = new Gson();

    public ConnectionServer(
            final int port,
            final ServerEventListenerManager serverEventListenerManager,
            final SSLServerSocketFactory sslServerSocketFactory) {

        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(port);
                    serverSocket.setEnabledProtocols(new String[]{"TLSv1.2"});
                    while (!Thread.currentThread().isInterrupted()) {
                        log.info("ServerSocket Created, awaiting connection");
                        SSLSocket socket = (SSLSocket) serverSocket.accept();
                        log.info("Received new client request");
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String recvMsg = bufferedReader.readLine();
                        if (recvMsg != null){
                            log.info("Message received = {}", recvMsg);
                            PeerMessage messageFromClient = gson.fromJson(recvMsg, PeerMessage.class);
                            serverEventListenerManager.onClientRequestReceived(socket, messageFromClient);
                        }
                    }
                } catch (IOException e) {
                    // Calling serverSocket.close to terminate the thread will throw exception
                    // which is expected. Ignore that.
                    if (serverSocket.isClosed()){
                        log.warn("ConnectionServer terminated as expected due to server socket being closed", e);
                    } else {
                        log.error("ConnectionServer terminated unexpectedly", e);
                    }
                }
                log.info("ServerThread ending now..");
            }
        });
        serverThread.setName("ServerThread");

    }

    public void start() {
        serverThread.start();
    }

    public void stop() {
        // Calling serverSocket.close is the only wait
        // to exit the blocking accept() call in the server
        // thread since Thread.interrupt does not work.
        log.info("Stopping ConnectionServer...");
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.warn("Error while closing serverSocket", e);
        }
    }
}