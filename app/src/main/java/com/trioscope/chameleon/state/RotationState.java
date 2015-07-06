package com.trioscope.chameleon.state;

import lombok.Data;

/**
 * Created by phand on 6/23/15.
 */
@Data
public class RotationState {
    private boolean isLandscape;

    public boolean isPortrait() {
        return !isLandscape;
    }
}
