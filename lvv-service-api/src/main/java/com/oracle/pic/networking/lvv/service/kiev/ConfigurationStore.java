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

    boolean deleteItem(@NonNull Transaction txn, @NonNull H key);

    V getItem(@NonNull H key) throws Exception;

    V getItem(@NonNull Transaction txn, @NonNull H key) throws Exception;

    ScanResult<V> scanBucket(
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection);

    ScanResult<V> scanBucketByIndex(
            String indexName,
            String indexValue,
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection);

    ScanResult<V> scanBucketByIndex(
            String indexName,
            Long indexValue,
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection);

    ScanResult<V> prefixScanBucket(
            H key,
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection);
}
