package fk.prof.common.store;

import java.io.InputStream;
import java.util.concurrent.Future;

/**
 * @author gaurav.ashok
 */
public interface AsyncStorage {

    Future<Void> store(String path, InputStream content);

    Future<InputStream> fetch(String path);
}
