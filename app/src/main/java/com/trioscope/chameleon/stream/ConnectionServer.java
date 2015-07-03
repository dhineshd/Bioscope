package com.trioscope.chameleon.stream;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;

import com.trioscope.chameleon.types.PreviewImage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Server to handle interaction with peers.
 */
@Slf4j
public class ConnectionServer {
    public static final String CONNECTION_REQUEST_MSG = "CONNECTION_REQUEST";
    private Thread serverThread;
    private Thread previewConsumerThread;
    private Socket clientSocket;

    public ConnectionServer(
            final int port,
            final ParcelFileDescriptor readStreamFd,
            final ServerEventListener serverEventListener) {
        final ServerEventThreadHandler serverEventThreadHandler =
                new ServerEventThreadHandler(serverEventListener);
        serverThread = new Thread(new Runnable(){

            @Override
            public void run() {

                HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
                handlerThread.start();
                final int PREVIEW_IMAGE_AVAILABLE = 1;

                class PreviewConsumer implements Runnable{
                    private final Handler serverThreadHandler;
                    public PreviewConsumer(Handler serverThreadHandler){
                        this.serverThreadHandler = serverThreadHandler;
                    }

                    @Override
                    public void run() {
                        try {
                            InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(readStreamFd);
                            byte[] buffer = new byte[512];
                            int bytesRead = 0;
                            while(true){
                                bytesRead = inputStream.read(buffer);
                                //log.info("Read preview image of size = " + bytesRead);
                                PreviewImage previewImage = PreviewImage.builder().bytes(buffer).build();
                                serverThreadHandler.sendMessage(
                                        serverThreadHandler.obtainMessage(PREVIEW_IMAGE_AVAILABLE, previewImage));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                Handler serverThreadHandler = new Handler(handlerThread.getLooper()){
                    @Override
                    public void handleMessage(Message msg) {
                        if (msg.what == PREVIEW_IMAGE_AVAILABLE){
                            PreviewImage previewImage = (PreviewImage) msg.obj;
                            //log.info("Server thread received preview image");
                            if (clientSocket != null){
                                try {
                                    OutputStream os = clientSocket.getOutputStream();
                                    os.write(previewImage.getBytes());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        } else{
                            super.handleMessage(msg);
                        }
                    }
                };
                Runnable r = new PreviewConsumer(serverThreadHandler);
                previewConsumerThread = new Thread(r);
                previewConsumerThread.start();
                //TODO Stop previewConsumerThread

                try {
                    ServerSocket serverSocket = new ServerSocket(port);
                    while (!Thread.currentThread().isInterrupted()) {
                        log.info("ServerSocket Created, awaiting connection");
                        clientSocket = serverSocket.accept();
                        log.info("Received new client request");
                        //BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        //String messageFromClient = br.readLine();
                        //if (CONNECTION_REQUEST_MSG.equalsIgnoreCase(messageFromClient)){
                            //log.info("Received CONNECTION_REQUEST message from client {}", clientSocket.getInetAddress().getHostAddress());
                            serverEventThreadHandler.sendMessage(
                                    serverEventThreadHandler.obtainMessage(
                                            ServerEventThreadHandler.CLIENT_CONNECTION_REQUEST_RECEIVED, clientSocket));
                        //}
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

        });
    }

    public void start() {
        serverThread.start();
    }

    public void stop() {
        if (serverThread != null){
            serverThread.interrupt();
        }
        if (previewConsumerThread != null){
            previewConsumerThread.interrupt();
        }
    }

    class ServerEventThreadHandler extends Handler{
        @NonNull
        private ServerEventListener serverEventListener;
        public ServerEventThreadHandler(final ServerEventListener listener){
            this.serverEventListener = listener;
        }
        public static final int CLIENT_CONNECTION_REQUEST_RECEIVED = 1;
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == CLIENT_CONNECTION_REQUEST_RECEIVED){
                Socket clientSocket = (Socket) msg.obj;
                //context.startActivity(new Intent(context, ConnectionEstablishedActivity.class));
                log.info("Received connection request from " + clientSocket.getInetAddress().getHostAddress());
                serverEventListener.onClientConnectionRequest(clientSocket);
            } else {
                super.handleMessage(msg);
            }
        }
    }
}