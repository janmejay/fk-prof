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

/**
 * AsyncStorage impl backed by S3 Object storeAsync. It uses an executorService to offload the task.
 * @author gaurav.ashok
 */
public class D42AsyncStorage implements AsyncStorage {

    private static Logger LOGGER = LoggerFactory.getLogger(D42AsyncStorage.class);
    private static String NO_SUCH_KEY = "NoSuchKey";
    private static String NO_SUCH_BUKCET = "NoSuchBucket";

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
    public void storeAsync(String path, InputStream content) {
        CompletableFuture.runAsync(() -> {
            try {
                client.putObject(bucket, path, content, new ObjectMetadata());
            }
            catch(AmazonClientException e) {
                LOGGER.error("S3 PutObject failed: {}", path, e);
                //TODO expose metric
            }
        }, executorService);
    }

    @Override
    public InputStream fetch(String path) throws StorageException {
        try {
            return client.getObject(bucket, path).getObjectContent();
        }
        catch (AmazonServiceException svcEx) {
            LOGGER.error("S3 getObject failed: {}/{}", bucket, path, svcEx);
            throw mapServiceException(svcEx);
        }
        catch (AmazonClientException clientEx) {
            LOGGER.error("S3 getObject failed: {}/{}", bucket, path, clientEx);
            throw mapClientException(clientEx);
        }
    }

    @Override
    public CompletableFuture<InputStream> fetchAsync(String path) {
        return CompletableFuture.supplyAsync(() -> fetch(path), executorService);
    }

    private StorageException mapClientException(AmazonClientException ex) {
        return new StorageException(ex.getMessage(), ex, ex.isRetryable());
    }

    private StorageException mapServiceException(AmazonServiceException ex) {
        // map non existent bucket and non existent path to PathNotFound exception specifically
        if(NO_SUCH_BUKCET.equals(ex.getErrorCode())) {
            return new ObjectNotFoundException(NO_SUCH_BUKCET + ": " + ex.getMessage(), ex);
        }
        else if(NO_SUCH_KEY.equals(ex.getErrorCode())) {
            return new ObjectNotFoundException(NO_SUCH_KEY + ": " + ex.getMessage(), ex);
        }

        return mapClientException(ex);
    }
}
