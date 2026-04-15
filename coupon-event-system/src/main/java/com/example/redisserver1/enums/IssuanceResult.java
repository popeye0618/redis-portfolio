package com.example.redisserver1.enums;

public enum IssuanceResult {

    SUCCESS, SOLD_OUT, DUPLICATE, ERROR;

    public static IssuanceResult of(Long code) {
        return switch (code.intValue()) {
            case  1  -> SUCCESS;
            case -1  -> SOLD_OUT;
            case -2  -> DUPLICATE;
            default  -> ERROR;
        };
    }
}
