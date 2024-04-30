package com.oracle.pic.networking.lvv.service.kiev;

import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder(builderClassName = "Builder")
@Getter
@Setter
public class ScanResult<T> {

    private Optional<String> paginationToken;

    private List<T> results;
}
