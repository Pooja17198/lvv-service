package com.oracle.pic.networking.lvv.service.dependencies.storekeeper;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder(builderClassName = "Builder")
@ToString
public class Rack {
    private String building;
    private String block;
    private String rackLocation;
    private String rackSerial;
    private String rackState;
    private String platformName;
}
