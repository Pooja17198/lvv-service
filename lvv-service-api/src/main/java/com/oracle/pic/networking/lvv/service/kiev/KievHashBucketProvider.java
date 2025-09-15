package com.oracle.pic.networking.lvv.service.kiev;

import com.google.inject.Provider;
import com.oracle.pic.kiev.mapping.MappedDataStore;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import javax.inject.Inject;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Implements a provider for the Kiev MappedHash Bucket
 *
 * @param <H> The hashkey class
 * @param <V> The entity class for object stored in kiev
 */
@Slf4j
@ToString
public class KievHashBucketProvider<H, V> implements Provider<MappedHashBucket<H, V>> {

    private MappedDataStore dataStore;

    private final Class<H> hashKeyClass;

    private final Class<V> valueClass;

    private final String bucketName;

    private final String bucketComment;

    public KievHashBucketProvider(
            String bucketName, String bucketComment, Class<H> hashKeyClass, Class<V> valueClass) {
        this.hashKeyClass = hashKeyClass;
        this.valueClass = valueClass;
        this.bucketName = bucketName;
        this.bucketComment = bucketComment;
    }

    @Override
    public MappedHashBucket<H, V> get() {
        try {
            log.info("Get Bucket {}", bucketName);
            return this.dataStore.getOrCreateBucket(
                    bucketName, bucketComment, hashKeyClass, valueClass);
        } catch (Exception exception) {
            log.error("Stack Trace: {}", ExceptionUtils.getStackTrace(exception));
            throw exception;
        }
    }

    @Inject
    public void setDataStore(MappedDataStore dataStore) {
        this.dataStore = dataStore;
    }
}
