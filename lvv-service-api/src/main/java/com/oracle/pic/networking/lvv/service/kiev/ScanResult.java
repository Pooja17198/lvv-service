package com.oracle.pic.networking.lvv.service.kiev;

import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Builder(builderClassName = "Builder")
@Getter
@Setter
@ToString
public class ScanResult<T> {

    private Optional<String> paginationToken;

    private List<T> results;
}
