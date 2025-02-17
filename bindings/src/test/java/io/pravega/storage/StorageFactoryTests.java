/**
 * Copyright Pravega Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pravega.storage;

import io.pravega.segmentstore.storage.AsyncStorageWrapper;
import io.pravega.segmentstore.storage.ConfigSetup;
import io.pravega.segmentstore.storage.Storage;
import io.pravega.segmentstore.storage.StorageFactoryCreator;
import io.pravega.segmentstore.storage.StorageFactoryInfo;
import io.pravega.segmentstore.storage.StorageLayoutType;
import io.pravega.segmentstore.storage.SyncStorage;
import io.pravega.segmentstore.storage.chunklayer.ChunkedSegmentStorage;
import io.pravega.segmentstore.storage.chunklayer.ChunkedSegmentStorageConfig;
import io.pravega.segmentstore.storage.mocks.InMemoryMetadataStore;
import io.pravega.storage.azure.AzureChunkStorage;
import io.pravega.storage.azure.AzureTestContext;
import io.pravega.storage.azure.AzureStorageFactoryCreator;
import io.pravega.storage.azure.AzureSimpleStorageFactory;
import io.pravega.storage.azure.AzureStorageConfig;
import io.pravega.storage.extendeds3.ExtendedS3ChunkStorage;
import io.pravega.storage.extendeds3.ExtendedS3SimpleStorageFactory;
import io.pravega.storage.extendeds3.ExtendedS3StorageConfig;
import io.pravega.storage.extendeds3.ExtendedS3StorageFactory;
import io.pravega.storage.extendeds3.ExtendedS3StorageFactoryCreator;
import io.pravega.storage.filesystem.FileSystemChunkStorage;
import io.pravega.storage.filesystem.FileSystemSimpleStorageFactory;
import io.pravega.storage.filesystem.FileSystemStorageConfig;
import io.pravega.storage.filesystem.FileSystemStorageFactory;
import io.pravega.storage.filesystem.FileSystemStorageFactoryCreator;
import io.pravega.storage.gcp.GCPChunkStorage;
import io.pravega.storage.gcp.GCPSimpleStorageFactory;
import io.pravega.storage.gcp.GCPStorageConfig;
import io.pravega.storage.gcp.GCPStorageFactoryCreator;
import io.pravega.storage.hdfs.HDFSChunkStorage;
import io.pravega.storage.hdfs.HDFSSimpleStorageFactory;
import io.pravega.storage.hdfs.HDFSStorageConfig;
import io.pravega.storage.hdfs.HDFSStorageFactory;
import io.pravega.storage.hdfs.HDFSStorageFactoryCreator;
import io.pravega.storage.s3.S3ChunkStorage;
import io.pravega.storage.s3.S3SimpleStorageFactory;
import io.pravega.storage.s3.S3StorageConfig;
import io.pravega.storage.s3.S3StorageFactoryCreator;
import io.pravega.test.common.AssertExtensions;
import io.pravega.test.common.ThreadPooledTestSuite;
import lombok.Cleanup;
import lombok.val;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test factories.
 */
public class StorageFactoryTests extends ThreadPooledTestSuite {
    @Override
    protected int getThreadPoolSize() {
        return 1;
    }

    @Test
    public void testHDFSStorageFactoryCreator() {
        StorageFactoryCreator factoryCreator = new HDFSStorageFactoryCreator();
        val expected = new StorageFactoryInfo[]{
                StorageFactoryInfo.builder()
                        .name("HDFS")
                        .storageLayoutType(StorageLayoutType.CHUNKED_STORAGE)
                        .build(),
                StorageFactoryInfo.builder()
                        .name("HDFS")
                        .storageLayoutType(StorageLayoutType.ROLLING_STORAGE)
                        .build()
        };
        val factoryInfoList = factoryCreator.getStorageFactories();
        Assert.assertEquals(2, factoryInfoList.length);
        Assert.assertArrayEquals(expected, factoryInfoList);

        // Simple Storage
        ConfigSetup configSetup1 = mock(ConfigSetup.class);
        when(configSetup1.getConfig(any())).thenReturn(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, HDFSStorageConfig.builder().build());
        val factory1 = factoryCreator.createFactory(expected[0], configSetup1, executorService());
        Assert.assertTrue(factory1 instanceof HDFSSimpleStorageFactory);

        @Cleanup
        Storage storage1 = ((HDFSSimpleStorageFactory) factory1).createStorageAdapter(42, new InMemoryMetadataStore(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, executorService()));
        Assert.assertTrue(storage1 instanceof ChunkedSegmentStorage);
        Assert.assertTrue(((ChunkedSegmentStorage) storage1).getChunkStorage() instanceof HDFSChunkStorage);
        // Legacy Storage
        ConfigSetup configSetup2 = mock(ConfigSetup.class);
        when(configSetup2.getConfig(any())).thenReturn(HDFSStorageConfig.builder().build());
        val factory2 = factoryCreator.createFactory(expected[1], configSetup2, executorService());
        Assert.assertTrue(factory2 instanceof HDFSStorageFactory);

        @Cleanup
        Storage storage2 = factory2.createStorageAdapter();
        Assert.assertTrue(storage2 instanceof AsyncStorageWrapper);

        SyncStorage syncStorage = factory2.createSyncStorage();
        Assert.assertNotNull(syncStorage);

        AssertExtensions.assertThrows(
                "createStorageAdapter should throw UnsupportedOperationException.",
                () -> factory1.createStorageAdapter(),
                ex -> ex instanceof UnsupportedOperationException);
    }

    @Test
    public void testExtendedS3StorageFactoryCreator() {
        StorageFactoryCreator factoryCreator = new ExtendedS3StorageFactoryCreator();
        val expected = new StorageFactoryInfo[]{
                StorageFactoryInfo.builder()
                        .name("EXTENDEDS3")
                        .storageLayoutType(StorageLayoutType.CHUNKED_STORAGE)
                        .build(),
                StorageFactoryInfo.builder()
                        .name("EXTENDEDS3")
                        .storageLayoutType(StorageLayoutType.ROLLING_STORAGE)
                        .build()
        };

        val factoryInfoList = factoryCreator.getStorageFactories();
        Assert.assertEquals(2, factoryInfoList.length);
        Assert.assertArrayEquals(expected, factoryInfoList);

        // Simple Storage
        ConfigSetup configSetup1 = mock(ConfigSetup.class);
        val config = ExtendedS3StorageConfig.builder()
                .with(ExtendedS3StorageConfig.CONFIGURI, "http://127.0.0.1?identity=x&secretKey=x")
                .with(ExtendedS3StorageConfig.BUCKET, "bucket")
                .with(ExtendedS3StorageConfig.PREFIX, "samplePrefix")
                .build();
        when(configSetup1.getConfig(any())).thenReturn(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, config);
        val factory1 = factoryCreator.createFactory(expected[0], configSetup1, executorService());
        Assert.assertTrue(factory1 instanceof ExtendedS3SimpleStorageFactory);

        @Cleanup
        Storage storage1 = ((ExtendedS3SimpleStorageFactory) factory1).createStorageAdapter(42, new InMemoryMetadataStore(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, executorService()));
        Assert.assertTrue(storage1 instanceof ChunkedSegmentStorage);
        Assert.assertTrue(((ChunkedSegmentStorage) storage1).getChunkStorage() instanceof ExtendedS3ChunkStorage);

        // Legacy Storage
        ConfigSetup configSetup2 = mock(ConfigSetup.class);
        when(configSetup2.getConfig(any())).thenReturn(config);
        val factory2 = factoryCreator.createFactory(expected[1], configSetup2, executorService());

        Assert.assertTrue(factory2 instanceof ExtendedS3StorageFactory);
        @Cleanup
        Storage storage2 = factory2.createStorageAdapter();
        Assert.assertTrue(storage2 instanceof AsyncStorageWrapper);

        @Cleanup
        SyncStorage syncStorage = factory2.createSyncStorage();
        Assert.assertNotNull(syncStorage);

        AssertExtensions.assertThrows(
                "createStorageAdapter should throw UnsupportedOperationException.",
                () -> factory1.createStorageAdapter(),
                ex -> ex instanceof UnsupportedOperationException);
    }

    @Test
    public void testS3StorageFactoryCreatorWithoutRole() {
        val config = S3StorageConfig.builder()
                .with(S3StorageConfig.CONFIGURI, "http://127.0.0.1")
                .with(S3StorageConfig.BUCKET, "bucket")
                .with(S3StorageConfig.PREFIX, "samplePrefix")
                .with(S3StorageConfig.ACCESS_KEY, "user")
                .with(S3StorageConfig.SECRET_KEY, "secret")
                .build();

        testS3StorageFactoryCreator(config);
    }

    @Test
    public void testS3StorageFactoryCreatorWithRole() {
        val config = S3StorageConfig.builder()
                .with(S3StorageConfig.CONFIGURI, "http://127.0.0.1")
                .with(S3StorageConfig.ASSUME_ROLE, true)
                .with(S3StorageConfig.BUCKET, "bucket")
                .with(S3StorageConfig.PREFIX, "samplePrefix")
                .with(S3StorageConfig.ACCESS_KEY, "user")
                .with(S3StorageConfig.SECRET_KEY, "secret")
                .with(S3StorageConfig.USER_ROLE, "role")
                .build();

        testS3StorageFactoryCreator(config);
    }

    private void testS3StorageFactoryCreator(S3StorageConfig config) {
        StorageFactoryCreator factoryCreator = new S3StorageFactoryCreator();
        val expected = new StorageFactoryInfo[]{
                StorageFactoryInfo.builder()
                        .name("S3")
                        .storageLayoutType(StorageLayoutType.CHUNKED_STORAGE)
                        .build()
        };

        val factoryInfoList = factoryCreator.getStorageFactories();
        Assert.assertEquals(1, factoryInfoList.length);
        Assert.assertArrayEquals(expected, factoryInfoList);

        // Simple Storage
        ConfigSetup configSetup1 = mock(ConfigSetup.class);
        when(configSetup1.getConfig(any())).thenReturn(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, config);
        val factory1 = factoryCreator.createFactory(expected[0], configSetup1, executorService());
        Assert.assertTrue(factory1 instanceof S3SimpleStorageFactory);

        @Cleanup
        Storage storage1 = ((S3SimpleStorageFactory) factory1).createStorageAdapter(42, new InMemoryMetadataStore(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, executorService()));
        Assert.assertTrue(storage1 instanceof ChunkedSegmentStorage);
        Assert.assertTrue(((ChunkedSegmentStorage) storage1).getChunkStorage() instanceof S3ChunkStorage);

        AssertExtensions.assertThrows(
                "createStorageAdapter should throw UnsupportedOperationException.",
                () -> factory1.createStorageAdapter(),
                ex -> ex instanceof UnsupportedOperationException);
    }

    @Test
    public void testAzureStorageFactoryCreator() {
        StorageFactoryCreator factoryCreator = new AzureStorageFactoryCreator();
        val expected = new StorageFactoryInfo[]{
                StorageFactoryInfo.builder()
                        .name("AZURE")
                        .storageLayoutType(StorageLayoutType.CHUNKED_STORAGE)
                        .build(),
        };

        val factoryInfoList = factoryCreator.getStorageFactories();
        Assert.assertEquals(1, factoryInfoList.length);
        Assert.assertArrayEquals(expected, factoryInfoList);

        // Simple Storage
        ConfigSetup configSetup1 = mock(ConfigSetup.class);
        val config = AzureTestContext.getLocalAzureStorageConfig("sampleprefix");
        when(configSetup1.getConfig(any())).thenReturn(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, config);
        val factory1 = factoryCreator.createFactory(expected[0], configSetup1, executorService());
        Assert.assertTrue(factory1 instanceof AzureSimpleStorageFactory);

        @Cleanup
        Storage storage1 = ((AzureSimpleStorageFactory) factory1).createStorageAdapter(42, new InMemoryMetadataStore(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, executorService()));
        Assert.assertTrue(storage1 instanceof ChunkedSegmentStorage);
        Assert.assertTrue(((ChunkedSegmentStorage) storage1).getChunkStorage() instanceof AzureChunkStorage);

        AssertExtensions.assertThrows(
                "createStorageAdapter should throw UnsupportedOperationException.",
                () -> factory1.createStorageAdapter(),
                ex -> ex instanceof UnsupportedOperationException);
    }

    @Test
    public void testFileSystemStorageFactoryCreator() {
        StorageFactoryCreator factoryCreator = new FileSystemStorageFactoryCreator();
        val expected = new StorageFactoryInfo[]{
                StorageFactoryInfo.builder()
                        .name("FILESYSTEM")
                        .storageLayoutType(StorageLayoutType.CHUNKED_STORAGE)
                        .build(),
                StorageFactoryInfo.builder()
                        .name("FILESYSTEM")
                        .storageLayoutType(StorageLayoutType.ROLLING_STORAGE)
                        .build()
        };

        val factoryInfoList = factoryCreator.getStorageFactories();
        Assert.assertEquals(2, factoryInfoList.length);
        Assert.assertArrayEquals(expected, factoryInfoList);

        // Simple Storage
        ConfigSetup configSetup1 = mock(ConfigSetup.class);
        when(configSetup1.getConfig(any())).thenReturn(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, FileSystemStorageConfig.builder().build());
        val factory1 = factoryCreator.createFactory(expected[0], configSetup1, executorService());
        Assert.assertTrue(factory1 instanceof FileSystemSimpleStorageFactory);

        @Cleanup
        Storage storage1 = ((FileSystemSimpleStorageFactory) factory1).createStorageAdapter(42, new InMemoryMetadataStore(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, executorService()));
        Assert.assertTrue(storage1 instanceof ChunkedSegmentStorage);
        Assert.assertTrue(((ChunkedSegmentStorage) storage1).getChunkStorage() instanceof FileSystemChunkStorage);

        // Legacy Storage
        ConfigSetup configSetup2 = mock(ConfigSetup.class);
        when(configSetup2.getConfig(any())).thenReturn(FileSystemStorageConfig.builder().build());
        val factory2 = factoryCreator.createFactory(expected[1], configSetup2, executorService());

        Assert.assertTrue(factory2 instanceof FileSystemStorageFactory);
        Storage storage2 = factory2.createStorageAdapter();
        Assert.assertTrue(storage2 instanceof AsyncStorageWrapper);

        @Cleanup
        SyncStorage syncStorage = factory2.createSyncStorage();
        Assert.assertNotNull(syncStorage);

        AssertExtensions.assertThrows(
                "createStorageAdapter should throw UnsupportedOperationException.",
                () -> factory1.createStorageAdapter(),
                ex -> ex instanceof UnsupportedOperationException);
    }

    @Test
    public void testGCPStorageFactoryCreatorWithException() {
        val config = GCPStorageConfig.builder()
                .with(GCPStorageConfig.BUCKET, "bucket")
                .with(GCPStorageConfig.PREFIX, "samplePrefix")
                .with(GCPStorageConfig.ACCOUNT_TYPE, "testAccountType")
                .with(GCPStorageConfig.PROJECT_ID, "testProjectId")
                .with(GCPStorageConfig.CLIENT_EMAIL, "testClientEmail")
                .with(GCPStorageConfig.CLIENT_ID, "testClientId")
                .with(GCPStorageConfig.PRIVATE_KEY_ID, "testPrivateKeyId")
                .with(GCPStorageConfig.PRIVATE_KEY, "testPrivateKey")
                .with(GCPStorageConfig.USE_MOCK, false)
                .build();

        StorageFactoryCreator factoryCreator = new GCPStorageFactoryCreator();
        val expected = new StorageFactoryInfo[]{
                StorageFactoryInfo.builder()
                        .name("GCP")
                        .storageLayoutType(StorageLayoutType.CHUNKED_STORAGE)
                        .build()
        };

        val factoryInfoList = factoryCreator.getStorageFactories();
        Assert.assertEquals(1, factoryInfoList.length);
        Assert.assertArrayEquals(expected, factoryInfoList);

        // Simple Storage
        ConfigSetup configSetup1 = mock(ConfigSetup.class);

        when(configSetup1.getConfig(any())).thenReturn(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, config);
        val factory1 = factoryCreator.createFactory(expected[0], configSetup1, executorService());
        Assert.assertTrue(factory1 instanceof GCPSimpleStorageFactory);

        GCPSimpleStorageFactory gcpSimpleStorageFactory = (GCPSimpleStorageFactory) factory1;
        Assert.assertThrows(RuntimeException.class, () -> gcpSimpleStorageFactory.createStorageAdapter(42, new InMemoryMetadataStore(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, executorService())));
    }

    @Test
    public void testGCPStorageFactoryCreator() {
        val config = GCPStorageConfig.builder()
                .with(GCPStorageConfig.BUCKET, "bucket")
                .with(GCPStorageConfig.PREFIX, "samplePrefix")
                .with(GCPStorageConfig.USE_MOCK, true)
                .build();

        StorageFactoryCreator factoryCreator = new GCPStorageFactoryCreator();
        val expected = new StorageFactoryInfo[]{
                StorageFactoryInfo.builder()
                        .name("GCP")
                        .storageLayoutType(StorageLayoutType.CHUNKED_STORAGE)
                        .build()
        };

        val factoryInfoList = factoryCreator.getStorageFactories();
        Assert.assertEquals(1, factoryInfoList.length);
        Assert.assertArrayEquals(expected, factoryInfoList);

        // Simple Storage
        ConfigSetup configSetup1 = mock(ConfigSetup.class);

        when(configSetup1.getConfig(any())).thenReturn(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, config);
        val factory1 = factoryCreator.createFactory(expected[0], configSetup1, executorService());
        Assert.assertTrue(factory1 instanceof GCPSimpleStorageFactory);

        @Cleanup
        Storage storage1 = ((GCPSimpleStorageFactory) factory1).createStorageAdapter(42, new InMemoryMetadataStore(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, executorService()));
        Assert.assertTrue(storage1 instanceof ChunkedSegmentStorage);
        Assert.assertTrue(((ChunkedSegmentStorage) storage1).getChunkStorage() instanceof GCPChunkStorage);

        AssertExtensions.assertThrows(
                "createStorageAdapter should throw UnsupportedOperationException.",
                () -> factory1.createStorageAdapter(),
                ex -> ex instanceof UnsupportedOperationException);
    }

    @Test
    public void testNull() {
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new FileSystemSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, null, executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new ExtendedS3SimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, null, executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new HDFSSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, null, executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new AzureSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, null, executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new FileSystemSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, FileSystemStorageConfig.builder().build(), null),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new ExtendedS3SimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, ExtendedS3StorageConfig.builder().build(), null),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new HDFSSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, HDFSStorageConfig.builder().build(), null),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new AzureSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, AzureStorageConfig.builder().build(), null),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new FileSystemSimpleStorageFactory(null, FileSystemStorageConfig.builder().build(), executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new ExtendedS3SimpleStorageFactory(null, ExtendedS3StorageConfig.builder().build(), executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new HDFSSimpleStorageFactory(null, HDFSStorageConfig.builder().build(), executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new AzureSimpleStorageFactory(null, AzureStorageConfig.builder().build(), executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new GCPSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, null, executorService()),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new GCPSimpleStorageFactory(ChunkedSegmentStorageConfig.DEFAULT_CONFIG, GCPStorageConfig.builder().build(), null),
                ex -> ex instanceof NullPointerException);
        AssertExtensions.assertThrows(
                " should throw exception.",
                () -> new GCPSimpleStorageFactory(null, GCPStorageConfig.builder().build(), executorService()),
                ex -> ex instanceof NullPointerException);
    }
}
