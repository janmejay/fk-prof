package fk.prof.common.store;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author gaurav.ashok
 */
public class D42AsyncStorage implements AsyncStorage {

    private AmazonS3 client;
    private String bucket;
    private ExecutorService executorService;

    public D42AsyncStorage(AmazonS3 client, String bucket, ExecutorService executorService) {
        assert client != null : "S3 client cannot be null";
        assert bucket != null && bucket.length() > 0 : "S3 bucket cannot be null";
        assert executorService != null : "D42AsyncStorage.executorService cannot be null";

        this.client = client;
        this.bucket = bucket;
        this.executorService = executorService;
    }

    @Override
    public Future<Void> store(String path, InputStream content) {
        return CompletableFuture.runAsync(() -> client.putObject(bucket, path, content, new ObjectMetadata()), executorService);
    }

    @Override
    public Future<InputStream> fetch(String path) {
        return CompletableFuture.supplyAsync(() -> client.getObject(bucket, path).getObjectContent(), executorService);
    }
}
