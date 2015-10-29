package com.trioscope.chameleon.util;

import lombok.Builder;
import lombok.Data;

/**
 * Created by phand on 10/29/15.
 */
@Builder
@Data
public class Asset {
    private String expectedMd5, url, expectedZippedMd5, outputName;
}