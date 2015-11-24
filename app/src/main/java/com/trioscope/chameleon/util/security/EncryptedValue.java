package com.trioscope.chameleon.util.security;

import lombok.Data;

/**
 * Created by phand on 11/24/15.
 */
@Data
public class EncryptedValue {
    String encryptedStr;
    String ivAsStr;
}
