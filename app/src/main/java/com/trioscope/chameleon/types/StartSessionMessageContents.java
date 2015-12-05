package com.trioscope.chameleon.types;

import lombok.Builder;
import lombok.Data;

/**
 * Created by phand on 12/5/15.
 */
@Data
@Builder
public class StartSessionMessageContents {
    private final byte[] bytes;
    private final String password;
}
