package com.trioscope.chameleon.types;

import java.io.File;
import java.net.Socket;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Created by rohitraghunathan on 9/17/15.
 */
@Builder
@Getter
public class SendVideoToPeerMetadata {
    @NonNull
    private final Socket clientSocket;
    @NonNull
    private final File videoFile;
    @NonNull
    private final Long recordingStartTimeMillis;
    private final boolean recordingHorizontallyFlipped;
}
