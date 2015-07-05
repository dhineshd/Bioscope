package com.trioscope.chameleon.stream;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
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
    private ServerSocket serverSocket;

    public ConnectionServer(
            final int port,
            final ServerEventListener serverEventListener,
            final SSLContext sslContext) {

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create server socket", e);
        }

        final int CLIENT_CONNECTION_REQUEST_RECEIVED = 1;
        final Handler handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                if (CLIENT_CONNECTION_REQUEST_RECEIVED == msg.what){
                    Socket socket = (Socket) msg.obj;
                    log.info("Received connection request from " + socket.getInetAddress().getHostAddress());
                    serverEventListener.onClientConnectionRequest(socket);
                } else {
                    super.handleMessage(msg);
                }
            }
        };

        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SSLServerSocket serverSocket = (SSLServerSocket)(sslContext.getServerSocketFactory()).createServerSocket(port);
                    while (!Thread.currentThread().isInterrupted()) {
                        log.info("ServerSocket Created, awaiting connection");
                        SSLSocket socket = (SSLSocket) serverSocket.accept();
                        log.info("Received new client request");
                        handler.sendMessage(handler.obtainMessage(CLIENT_CONNECTION_REQUEST_RECEIVED, socket));
                    }
                } catch (IOException e) {
                    // Calling serverSocket.close to terminate the thread wil throw exception
                    // which is expected. Ignore that.
                    if (!serverSocket.isClosed()){
                        throw new RuntimeException(e);
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