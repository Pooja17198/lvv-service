package com.oracle.pic.networking.lvv.service.utils;

import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaginationToken {
    Optional<String> token;
}
