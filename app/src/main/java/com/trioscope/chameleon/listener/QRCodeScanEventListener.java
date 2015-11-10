package com.trioscope.chameleon.listener;

/**
 * Created by dhinesh.dharman on 11/9/15.
 */
public interface QRCodeScanEventListener {
    void onTextDecoded(String decodedText);
}
