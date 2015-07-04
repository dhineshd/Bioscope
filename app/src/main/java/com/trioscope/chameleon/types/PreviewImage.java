package com.trioscope.chameleon.types;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by dhinesh.dharman on 7/2/15.
 */
@Builder
@Getter
@Setter
public class PreviewImage{
    private byte[] bytes;
}
