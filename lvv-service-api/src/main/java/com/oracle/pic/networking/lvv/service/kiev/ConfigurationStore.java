package com.oracle.pic.networking.lvv.service.kiev;

import com.oracle.pic.kiev.Bucket;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.PaginationDirection;
import com.oracle.pic.kiev.mapping.PaginationToken;
import java.util.Optional;
import lombok.NonNull;

/**
 * defines the interface between the Config Store Service API system and the underlynig system whild
 * stores the configuration data*
 *
 * @param <H> The Hash key class
 * @param <V> The entity key class
 */
public interface ConfigurationStore<H, V> {
    Transaction beginTransaction(String name);

    V createItem(@NonNull Transaction txn, @NonNull V entity);

    V updateItem(@NonNull Transaction txn, @NonNull V entity);

    V getItem(@NonNull H key) throws RuntimeException;

    boolean deleteItem(@NonNull Transaction txn, @NonNull H key);

    ScanResult<V> scanBucket(
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection);
}
