package fk.prof.storage;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for a async load/store storage.
 * @author gaurav.ashok
 */
public interface AsyncStorage {

    /**
     * Store the content to the specified path. StorageException while storing
     * needs to be taken care of by the implementation.
     * @param path path where the content is to stored
     * @param content the content as inputstream
     * @return Future to indicate completion
     */
    CompletableFuture<Void> storeAsync(String path, InputStream content, long length);

    /**
     * Retrieves the content from the specified path.
     * @param path path from where  content is to fetched
     * @return Future object for the content.
     */
    CompletableFuture<InputStream> fetchAsync(String path);


    /**
     * Lists all objects from the specified prefix of the path with options to list recursively
     *
     * @param prefixPath prefix of path from where objects are to be listed
     * @param recursive  true if listing is to be recursive
     * @return Future object for the set containing the object names
     */
    CompletableFuture<Set<String>> listAsync(String prefixPath, boolean recursive);
}
