package fk.prof.storage;

import java.io.InputStream;
import java.util.concurrent.Future;

/**
 * Interface for a simple async load/store storage.
 * @author gaurav.ashok
 */
public interface AsyncStorage {

    /**
     * Store the content to the specified path. IOException while storing
     * needs to be taken care of by the implementation.
     * @param path
     * @param content
     */
    void store(String path, InputStream content);

    /**
     * Retrieves the content from the specified path.
     * @param path
     * @return Future object for the content. Waiting on this future object may
     * throw ExecutionException in case of errors.
     */
    Future<InputStream> fetch(String path);
}
