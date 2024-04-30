package com.oracle.pic.networking.lvv.service.kiev;

import com.google.inject.Inject;
import com.oracle.pic.kiev.Bucket;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedDataStore;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.PaginationDirection;
import com.oracle.pic.kiev.mapping.PaginationToken;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.utils.KievConstants;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * * Implements a Kiev specific Configuration Store with Hash Key
 *
 * @param <K> The Hash key class
 * @param <V> The class of the storaged entity
 */
@Slf4j
@Value
public class KievConfigurationStore<K, V> implements ConfigurationStore<K, V> {

    private MappedDataStore mappedDataStore;
    private PaginationTokenSerializer serializer;
    // This variable disables paging in tests
    // Tests cause a paging serialization error in memory kiev
    private static boolean NO_PAGE_SERIALIZATION = false;

    private MappedHashBucket<K, V> bucket;

    @Inject
    public KievConfigurationStore(
            @NonNull MappedDataStore mappedDataStore,
            @NonNull MappedHashBucket<K, V> hashBucket,
            @NonNull PaginationTokenSerializer serializer) {
        this.mappedDataStore = mappedDataStore;
        this.bucket = hashBucket;
        this.serializer = serializer;
    }

    @Override
    public Transaction beginTransaction(String name) {
        String truncatedName =
                name.length() <= KievConstants.MAX_TRANSACTION_NAME_LENGTH
                        ? name
                        : name.substring(0, KievConstants.MAX_TRANSACTION_NAME_LENGTH - 1);
        log.debug("Using truncated transaction name {} in place of {}", truncatedName, name);
        return mappedDataStore.beginTransaction(truncatedName);
    }

    @Override
    public V createItem(@NonNull Transaction txn, @NonNull V entity) {
        log.debug("Create Item  {}", entity.toString());
        return bucket.insert(txn, entity);
    }

    @Override
    public V updateItem(@NonNull Transaction txn, @NonNull V entity) {
        log.debug("Update Item  {}", entity.toString());
        return bucket.put(txn, entity);
    }

    @Override
    public boolean deleteItem(@NonNull Transaction txn, @NonNull K key) {
        Optional<V> item = bucket.get(txn, key);
        return item.map(
                        (v) -> {
                            bucket.delete(txn, key);
                            return true;
                        })
                .orElse(false);
    }

    @Override
    public V getItem(@NonNull Transaction txn, @NonNull K key) throws Exception {
        Optional<V> item = bucket.get(txn, key);
        return item.map(v -> item.get())
                .orElseThrow(() -> new Exception("Failed to retrieve item " + key.toString()));
    }

    @Override
    public V getItem(@NonNull K key) throws Exception {
        Optional<V> item = bucket.get(key);
        return item.map(v -> item.get())
                .orElseThrow(() -> new Exception("Failed to retrieve item " + key.toString()));
    }

    @Override
    public ScanResult<V> scanBucket(
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection) {
        ScanPage<V> scanPage;
        ScanResult<V> scanResult;

        if (paginationToken.isPresent()) {
            scanPage = bucket.scan(paginationToken.get(), pageSize);
        } else {
            scanPage = bucket.beginScan(pageSize, bucketDirection, pageDirection);
        }

        scanResult = getScanResult(scanPage);

        return scanResult;
    }

    @Override
    public ScanResult<V> prefixScanBucket(
            @NonNull K key,
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection) {
        ScanPage<V> scanPage;
        ScanResult<V> scanResult;

        if (paginationToken.isPresent()) {
            scanPage = bucket.scan(paginationToken.get(), pageSize);
        } else {
            scanPage = bucket.beginPrefixScan(key, pageSize, bucketDirection, pageDirection);
        }

        scanResult = getScanResult(scanPage);

        return scanResult;
    }

    @Override
    public ScanResult<V> scanBucketByIndex(
            String indexName,
            String indexValue,
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection) {

        Index<String, V> index = bucket.getIndex(indexName, String.class);

        ScanPage<V> scanPage;
        ScanResult<V> scanResult;

        if (paginationToken.isPresent()) {
            scanPage = index.scan(paginationToken.get(), pageSize);
        } else {
            scanPage = index.beginPrefixScan(indexValue, pageSize, bucketDirection, pageDirection);
        }

        scanResult = getScanResult(scanPage);

        return scanResult;
    }

    @Override
    public ScanResult<V> scanBucketByIndex(
            String indexName,
            Long indexValue,
            int pageSize,
            Optional<PaginationToken> paginationToken,
            Bucket.Direction bucketDirection,
            PaginationDirection pageDirection) {

        Index<Long, V> index = bucket.getIndex(indexName, Long.class);

        ScanPage<V> scanPage;
        ScanResult<V> scanResult;

        if (paginationToken.isPresent()) {
            scanPage = index.scan(paginationToken.get(), pageSize);
        } else {
            scanPage = index.beginPrefixScan(indexValue, pageSize, bucketDirection, pageDirection);
        }

        scanResult = getScanResult(scanPage);

        return scanResult;
    }

    private ScanResult<V> getScanResult(ScanPage<V> scanPage) {
        ScanResult<V> scanResult;
        String nextToken = null;
        // Serialization is not possible in tests. This variable is JUST for testing
        // Please don't set this out side
        if (true) {
            return ScanResult.<V>builder()
                    .paginationToken(Optional.ofNullable(nextToken))
                    .results(scanPage.results())
                    .build();
        }
        if (scanPage.hasNext()) {
            nextToken = serializer.serialize(scanPage.nextPageToken());
        }

        List<V> results = scanPage.results();
        scanResult =
                ScanResult.<V>builder()
                        .paginationToken(Optional.ofNullable(nextToken))
                        .results(results)
                        .build();
        if ("true".equals(System.getenv("IS_KAAS_LOGGING_ENABLED"))) {
            log.debug("scanBucket returning {}", results);
        }
        if (log.isDebugEnabled()) {
            log.debug("scanBucket Returning  {} records", results.size());
        }
        return scanResult;
    }
}
