package fk.prof.userapi;

import fk.prof.storage.AsyncStorage;
import fk.prof.storage.S3AsyncStorage;
import fk.prof.storage.S3ClientFactory;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.ExecutorService;

/**
 * Returns instance of the appropriate child of {@link AsyncStorage} based on the config provided with the following structure:
 * <pre>
 * {@code
 * {
 * "storage":"S3",
 * "S3":{
 * "end.point":"http://endpoint:port",
 * "access.key":"access_key",
 * "secret.key":"secret_key"
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
  private static final String LIST_OBJECTS_TIMEOUT_MS = "list.objects.timeout.ms";

  private static S3AsyncStorage s3AsyncStorage = null;

  synchronized public static AsyncStorage getAsyncStorage(JsonObject storageConfig, ExecutorService storageExecSvc) {
    for (String key : storageConfig.fieldNames()) {
      switch (key) {
        case S3:
          return getS3AsyncInstance(storageConfig.getJsonObject(S3), storageExecSvc);
      }
    }
    throw new RuntimeException("No storage configured");
  }

  private static AsyncStorage getS3AsyncInstance(JsonObject jsonObject, ExecutorService storageExecSvc) {
    if (s3AsyncStorage == null) {
      s3AsyncStorage = new S3AsyncStorage(S3ClientFactory.create(jsonObject.getString(END_POINT), jsonObject.getString(ACCESS_KEY), jsonObject.getString(SECRET_KEY)),
          storageExecSvc, jsonObject.getLong(LIST_OBJECTS_TIMEOUT_MS, 5000L));
    }
    return s3AsyncStorage;
  }
}
