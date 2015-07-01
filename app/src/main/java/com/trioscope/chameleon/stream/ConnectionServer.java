package com.trioscope.chameleon.stream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

/**
 * Server to handle interaction with peers.
 */
@Slf4j
public class ConnectionServer {
    private Thread mThread;

    public ConnectionServer(final int port) {
        mThread = new Thread(new Runnable(){

            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(port);
                    while (!Thread.currentThread().isInterrupted()) {
                        log.info("ServerSocket Created, awaiting connection");
                        Socket clientSocket = serverSocket.accept();
                        log.info("Received new client request");
                        BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String messageFromClient = br.readLine();
                        log.info("Received message from client {}: Msg {}", clientSocket.getInetAddress().getHostAddress(), messageFromClient);
//                        InputStreamReader inputStreamReader = new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(readFd));
//                        char[] buffer = new char[16384];
//                            OutputStreamWriter ow = new OutputStreamWriter(clientSocket.getOutputStream());
//                            while(inputStreamReader.read(buffer) != -1){
//                                LOG.info("Sending bytes = " + buffer.length);
//                                ow.write(buffer);
//                            }
//                            ow.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void start() {
        mThread.start();
    }
    public void stop() {
        mThread.interrupt();
    }
}