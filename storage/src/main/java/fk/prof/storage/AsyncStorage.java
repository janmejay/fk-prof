package fk.prof.storage;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for a async load/store storage.
 * @author gaurav.ashok
 */
public interface AsyncStorage {

    /**
     * Store the content to the specified path. StorageException while storing
     * needs to be taken care of by the implementation.
     * @param path
     * @param content
     */
    void storeAsync(String path, InputStream content);

    /**
     * Synchronous method to fetch content from the specified path.
     * May throw {@link StorageException }
     * @param path
     * @return
     */
    InputStream fetch(String path) throws StorageException;

    /**
     * Retrieves the content from the specified path.
     * @param path
     * @return Future object for the content.
     */
    CompletableFuture<InputStream> fetchAsync(String path);
}
