package com.trioscope.chameleon.stream;

import android.content.Context;
import android.content.Intent;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.activity.ConnectionEstablishedActivity;
import com.trioscope.chameleon.types.PeerInfo;

import java.net.Socket;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 7/2/15.
 */
@Slf4j
public class ServerEventListener {
    @Setter
    private Context context;
    @Setter
    private boolean isStreamingSessionStarted;

    public void onClientConnectionRequest(Socket clientSocket) {
        log.info("onClientConnectionRequest invoked isStreamingStarted = {}", isStreamingSessionStarted);
        if (context != null && !isStreamingSessionStarted){
            //TODO Don't start activity if we get second request
            Intent intent = new Intent(context, ConnectionEstablishedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PeerInfo peerInfo = PeerInfo.builder()
                    .ipAddress(clientSocket.getInetAddress())
                    .port(ChameleonApplication.SERVER_PORT).build();
            intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, new Gson().toJson(peerInfo));
            context.startActivity(intent);
            isStreamingSessionStarted = true;
            //PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        }
    }
}
