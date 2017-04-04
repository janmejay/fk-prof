package fk.prof.userapi;

import fk.prof.storage.AsyncStorage;
import fk.prof.storage.S3AsyncStorage;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.ExecutorService;

/**
 * Returns instance of the appropriate child of {@link AsyncStorage} based on the config provided with the following structure:
 * <pre>
 * {@code
 * {
 * "storage":"S3",
 * "S3":{
 * "end.point":"http://10.47.2.3:80",
 * "access.key":"66ZX9WC7ZRO6S5BSO8TG",
 * "secret.key":"fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+"
 * }
 * }
 * }
 * </pre>
 * Created by rohit.patiyal on 02/02/17.
 */
public class StorageFactory {

  private static final String S3 = "s3";
    private static final String ACCESS_KEY = "access.key";
    private static final String SECRET_KEY = "secret.key";
    private static final String END_POINT = "endpoint";

    private static S3AsyncStorage s3AsyncStorage = null;

  synchronized public static AsyncStorage getAsyncStorage(JsonObject storageConfig, ExecutorService storageExecSvc) {
    if (storageConfig.fieldNames().isEmpty())
      return getS3AsyncInstance(storageConfig.getJsonObject(S3), storageExecSvc);
    switch (storageConfig.fieldNames().iterator().next()) {
            case S3:
              return getS3AsyncInstance(storageConfig.getJsonObject(S3), storageExecSvc);
            default:
              return getS3AsyncInstance(storageConfig.getJsonObject(S3), storageExecSvc);
        }
    }

  private static AsyncStorage getS3AsyncInstance(JsonObject jsonObject, ExecutorService storageExecSvc) {
        if (s3AsyncStorage == null) {
          s3AsyncStorage = new S3AsyncStorage(jsonObject.getString(END_POINT), jsonObject.getString(ACCESS_KEY), jsonObject.getString(SECRET_KEY), storageExecSvc);
        }
        return s3AsyncStorage;
    }
}
