package com.oracle.pic.networking.lvv.service.kiev;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.oracle.pic.kiev.DataStoreConfig;
import com.oracle.pic.kiev.mapping.MappedDataStore;
import java.io.Closeable;
import java.io.IOException;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@Singleton
public class DataStoreProvider implements Closeable, Provider<MappedDataStore> {

    private MappedDataStore mappedDataStore;

    @Inject
    public DataStoreProvider(DataStoreConfig storeConfig) throws Exception {
        storeConfig.initialize();

        this.mappedDataStore = new MappedDataStore(storeConfig.connect());
    }

    @Override
    public void close() throws IOException {
        mappedDataStore.close();
    }

    @Override
    public MappedDataStore get() {
        return mappedDataStore;
    }
}
