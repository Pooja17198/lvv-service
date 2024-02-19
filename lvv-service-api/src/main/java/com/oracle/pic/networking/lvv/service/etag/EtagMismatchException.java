package com.oracle.pic.networking.lvv.service.etag;

import lombok.Getter;

public class EtagMismatchException extends Exception {

    @Getter private final String etag;

    public EtagMismatchException(String etag) {
        this.etag = etag;
    }
}
