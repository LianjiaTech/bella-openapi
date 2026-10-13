package com.ke.bella.openapi.db.log;

import org.rocksdb.*;

class RocksDBResourceFactory {
    Options createOptions() { return new Options(); }
    BlockBasedTableConfig createTableConfig() { return new BlockBasedTableConfig(); }
    BloomFilter createBloomFilter() { return new BloomFilter(10, false); }
    WriteOptions createWriteOptions() { return new WriteOptions(); }
    FlushOptions createFlushOptions() { return new FlushOptions(); }
    RocksDB open(Options options, String path) throws RocksDBException { return RocksDB.open(options, path); }
    RocksDB openAsSecondary(Options options, String primaryPath, String secondaryPath) throws RocksDBException {
        return RocksDB.openAsSecondary(options, primaryPath, secondaryPath);
    }
}
