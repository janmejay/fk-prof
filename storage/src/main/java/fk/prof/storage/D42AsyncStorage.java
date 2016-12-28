package fk.prof.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * AsyncStorage impl backed by S3 Object store. Uses an executorService to offload the task.
 * @author gaurav.ashok
 */
public class D42AsyncStorage implements AsyncStorage {

    Logger logger = LoggerFactory.getLogger(D42AsyncStorage.class);

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
    public void store(String path, InputStream content) {
        CompletableFuture.runAsync(() -> {
            try {
                client.putObject(bucket, path, content, new ObjectMetadata());
            }
            catch(AmazonClientException e) {
                logger.error("S3 PutObject failed: {}", path, e);
                //TODO expose metric
            }
        }, executorService);
    }

    @Override
    public Future<InputStream> fetch(String path) {
        return CompletableFuture.supplyAsync(() -> client.getObject(bucket, path).getObjectContent(), executorService);
    }
}
