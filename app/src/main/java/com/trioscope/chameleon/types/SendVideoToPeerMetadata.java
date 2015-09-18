package com.trioscope.chameleon.types;

import java.io.File;
import java.net.Socket;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by rohitraghunathan on 9/17/15.
 */
@AllArgsConstructor
@Getter
public class SendVideoToPeerMetadata {

    final Socket clientSocket;

    final File videoFile;

    final Long recordingStartTimeMillis;
}
