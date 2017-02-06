package fk.prof.userapi.model;

import fk.prof.storage.AsyncStorage;
import fk.prof.storage.S3AsyncStorage;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.Executors;

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

    private static final String ZK = "ZK";
    private static final String S3 = "S3";
    private static final String ACCESS_KEY = "access.key";
    private static final String SECRET_KEY = "secret.key";
    private static final String END_POINT = "end.point";

    private static S3AsyncStorage s3AsyncStorage = null;

    public static AsyncStorage getAsyncStorage(JsonObject config) {
        switch (config.getString("storage")) {
            case S3:
                return getS3AsyncInstance(config.getJsonObject(S3));
            default:
                return getS3AsyncInstance(config.getJsonObject(S3));
        }
    }

    private static AsyncStorage getS3AsyncInstance(JsonObject jsonObject) {
        if (s3AsyncStorage == null) {
            s3AsyncStorage = new S3AsyncStorage(jsonObject.getString(END_POINT), jsonObject.getString(ACCESS_KEY), jsonObject.getString(SECRET_KEY), Executors.newCachedThreadPool());
        }
        return s3AsyncStorage;
    }
}
