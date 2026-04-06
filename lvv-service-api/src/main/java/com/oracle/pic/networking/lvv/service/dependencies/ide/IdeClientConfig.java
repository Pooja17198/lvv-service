package com.oracle.pic.networking.lvv.service.dependencies.ide;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Configuration for the IDE (Infrastructure Design Engineering) service client. */
@Getter
@Setter
@ToString
public class IdeClientConfig {

    /** Base URL of the IDE service, e.g. https://lvv.us-phoenix-1.oci.oc-test.com */
    private String endpoint;
}
