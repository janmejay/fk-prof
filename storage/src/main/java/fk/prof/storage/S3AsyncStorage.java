package fk.prof.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * AsyncStorage impl backed by S3 Object store. It uses an executorService to offload the request.
 * @author gaurav.ashok
 */
public class S3AsyncStorage implements AsyncStorage {
    private static Logger LOGGER = LoggerFactory.getLogger(S3AsyncStorage.class);
    private static char PATH_SEPARATOR = '/';
    private static String NO_SUCH_KEY = "NoSuchKey";
    private static String NO_SUCH_BUCKET = "NoSuchBucket";

    private AmazonS3 client;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private ExecutorService executorService;

    public S3AsyncStorage(String endpoint, String accessKey, String secretKey, ExecutorService executorService) {
        assert !StringUtils.isNullOrEmpty(endpoint) : "S3 endpoint cannot be null/empty";
        assert !StringUtils.isNullOrEmpty(accessKey) : "accessKey cannot be null/empty";
        assert !StringUtils.isNullOrEmpty(secretKey) : "secretKey cannot be null/empty";
        assert executorService != null : "S3AsyncStorage.executorService cannot be null";

        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;

        this.executorService = executorService;
    }

    public void initialize() {
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setProtocol(Protocol.HTTP);

        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        client = new AmazonS3Client(credentials,clientConfig);
        client.setEndpoint(endpoint);
        client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
    }

    @Override
    public void storeAsync(String path, InputStream content, long length) {
        S3ObjectPath objectPath = new S3ObjectPath(path);
        CompletableFuture.runAsync(() -> {
            try {
                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentLength(length);
                client.putObject(objectPath.bucket, objectPath.fileName, content, meta);
                // TODO expose metric for size written
            } catch (AmazonClientException e) {
                LOGGER.error("S3 PutObject failed: {}", path, e);
                //TODO expose metric
            }
            // content InputStream is by default closed by the S3Client, so need to close it.
        }, executorService);
    }

    @Override
    public CompletableFuture<InputStream> fetchAsync(String path) {
        S3ObjectPath objectPath = new S3ObjectPath(path);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.getObject(objectPath.bucket, objectPath.fileName).getObjectContent();
            }
            catch (AmazonServiceException svcEx) {
                LOGGER.error("S3 getObject failed: {}", path, svcEx);
                throw mapServiceException(svcEx);
            }
            catch (AmazonClientException clientEx) {
                LOGGER.error("S3 getObject failed: {}", path, clientEx);
                throw mapClientException(clientEx);
            }
        }, executorService);
    }

    private StorageException mapClientException(AmazonClientException ex) {
        return new StorageException(ex.getMessage(), ex, ex.isRetryable());
    }

    private StorageException mapServiceException(AmazonServiceException ex) {
        // map non existent bucket and non existent path to PathNotFound exception specifically
        if(NO_SUCH_BUCKET.equals(ex.getErrorCode())) {
            return new ObjectNotFoundException(NO_SUCH_BUCKET + ": " + ex.getMessage(), ex);
        }
        else if(NO_SUCH_KEY.equals(ex.getErrorCode())) {
            return new ObjectNotFoundException(NO_SUCH_KEY + ": " + ex.getMessage(), ex);
        }

        return mapClientException(ex);
    }

    private class S3ObjectPath {
        final String bucket;
        final String fileName;

        public S3ObjectPath(String fullPath) {
            int bucketEnd = fullPath.indexOf(PATH_SEPARATOR);
            if(bucketEnd == -1) {
                throw new IllegalArgumentException("expecting object path in the format of {bucket}/{fileName}");
            }

            this.bucket = fullPath.substring(0, bucketEnd);
            this.fileName = fullPath.substring(bucketEnd + 1);
        }
    }
}
