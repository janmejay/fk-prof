package fk.prof.storage.test;

import com.amazonaws.util.IOUtils;
import fk.prof.storage.AsyncStorage;
import fk.prof.storage.FileNamingStrategy;
import fk.prof.storage.ObjectNotFoundException;
import fk.prof.storage.StorageException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author gaurav.ashok
 */
class Util {

    public static class TrivialFileNameStrategy implements FileNamingStrategy {
        @Override
        public String getFileName(int part) {
            return String.valueOf(part);
        }
    }

    public static class StringStorage implements AsyncStorage {

        Map<String, String> writtenContent = new HashMap<>();

        // sync impl
        @Override
        public void storeAsync(String path, InputStream content, long length) {
            try {
                writtenContent.put(path, IOUtils.toString(content));
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            finally {
                try {
                    content.close();
                } catch (Exception ignored) {
                }
            }
        }

        InputStream fetch(String path) throws StorageException {
            if(writtenContent.containsKey(path)) {
                return new ByteArrayInputStream(writtenContent.get(path).getBytes());
            }
            // simulate ObjectNotFound case
            throw new ObjectNotFoundException("object not found");
        }

        @Override
        public CompletableFuture<InputStream> fetchAsync(String path) {
            return CompletableFuture.supplyAsync(() -> fetch(path));
        }

        @Override
        public CompletableFuture<Set<String>> listAsync(String prefixPath, boolean recursive) {
            return null;
        }
    }
}
