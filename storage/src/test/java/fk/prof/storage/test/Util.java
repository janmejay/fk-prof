package fk.prof.storage.test;

import com.amazonaws.util.IOUtils;
import fk.prof.storage.AsyncStorage;
import fk.prof.storage.FileNamingStrategy;


import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author gaurav.ashok
 */
public class Util {

    public static class TrivialFileNameStrategy implements FileNamingStrategy {
        @Override
        public String getFileName(int part) {
            return String.valueOf(part);
        }
    }

    public static class StringStorage implements AsyncStorage {

        public Map<String, String> writtenContent = new HashMap<>();

        // sync impl
        @Override
        public void store(String path, InputStream content) {
            try {
                writtenContent.put(path, IOUtils.toString(content));
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Future<InputStream> fetch(String path) {
            return CompletableFuture.supplyAsync(() -> {
                if(writtenContent.containsKey(path)) {
                    return new ByteArrayInputStream(writtenContent.get(path).getBytes());
                }
                // simulate FileNotFound case
                throw new UncheckedIOException(new FileNotFoundException());
            });
        }
    }
}
