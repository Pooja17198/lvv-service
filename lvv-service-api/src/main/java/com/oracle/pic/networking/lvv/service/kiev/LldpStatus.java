package com.oracle.pic.networking.lvv.service.kiev;

// This is the LLDP test result during a Link validation on a rack. It tells whether the source and
// destination of a link cabled, match what is expected
// It maps to ValidationFailureResult Data Object
public enum LldpStatus {
    MATCH,
    MISMATCH,
    UNSUPPORTED,
    UNTESTED,
    UNKNOWN
}
