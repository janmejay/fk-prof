package fk.prof.userapi.api;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.BaseEncoding;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.storage.AsyncStorage;
import fk.prof.userapi.model.AggregatedProfileInfo;
import fk.prof.userapi.model.AggregationWindowSummary;
import fk.prof.userapi.model.FilteredProfiles;
import io.vertx.core.*;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Interacts with the {@link AsyncStorage} based on invocations from controller
 * Created by rohit.patiyal on 19/01/17.
 */
public class ProfileStoreAPIImpl implements ProfileStoreAPI {

    private static final String VERSION = "v0001";
    private static final String DELIMITER = "/";

    public static final String WORKER_POOL_NAME = "aggregation.loader.pool";
    public static final int WORKER_POOL_SIZE = 50;
    public static final int DEFAULT_LOAD_TIMEOUT = 10000;   // in ms
    private int loadTimeout = DEFAULT_LOAD_TIMEOUT;

    private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ProfileStoreAPIImpl.class);

    private Vertx vertx;
    private AsyncStorage asyncStorage;
    private AggregatedProfileLoader profileLoader;
    private int maxIdleRetentionInMin;

    private WorkerExecutor workerExecutor;

    private Cache<String, AggregatedProfileInfo> cache;
    private Cache<String, AggregationWindowSummary> summaryCache;

    /* stores all requested futures that are waiting on file to be loaded from S3. If a file loadFromInputStream
    * is in progress, this map will contain its corresponding key */
    private Map<String, FuturesList<Object>> futuresForLoadingFiles;

    public ProfileStoreAPIImpl(Vertx vertx, AsyncStorage asyncStorage, int maxIdleRetentionInMin) {
        this.vertx = vertx;
        this.asyncStorage = asyncStorage;
        this.profileLoader = new AggregatedProfileLoader(this.asyncStorage);
        this.maxIdleRetentionInMin = maxIdleRetentionInMin;

        this.workerExecutor = vertx.createSharedWorkerExecutor(WORKER_POOL_NAME, WORKER_POOL_SIZE);

        this.cache = CacheBuilder.newBuilder()
                .maximumSize(50)
                .expireAfterAccess(this.maxIdleRetentionInMin, TimeUnit.MINUTES)
                .build();

        this.summaryCache = CacheBuilder.newBuilder()
                .maximumSize(500)
                .expireAfterAccess(this.maxIdleRetentionInMin, TimeUnit.MINUTES)
                .build();

        this.futuresForLoadingFiles = new HashMap<>();
    }

    private String getLastFromCommonPrefix(String commonPrefix) {
        String[] splits = commonPrefix.split(DELIMITER);
        return splits[splits.length - 1];
    }

    private void getListingAtLevelWithPrefix(Future<Set<String>> listings, String level, String objPrefix, boolean encoded) {
        CompletableFuture<Set<String>> commonPrefixesFuture = asyncStorage.listAsync(level, false);
        commonPrefixesFuture.thenApply(commonPrefixes -> {
            Set<String> objs = new HashSet<>();
            for (String commonPrefix : commonPrefixes) {
                String objName = getLastFromCommonPrefix(commonPrefix);
                objName = encoded ? decode(objName) : objName;

                if (objName.startsWith(objPrefix)) {
                    objs.add(objName);
                }
            }
            return objs;
        }).whenComplete((result, error) -> completeFuture(result, error, listings));
    }

    @Override
    public void getAppIdsWithPrefix(Future<Set<String>> appIds, String baseDir, String appIdPrefix) {
        getListingAtLevelWithPrefix(appIds, baseDir + DELIMITER + VERSION + DELIMITER, appIdPrefix, true);
    }

    @Override
    public void getClusterIdsWithPrefix(Future<Set<String>> clusterIds, String baseDir, String appId, String clusterIdPrefix) {
        getListingAtLevelWithPrefix(clusterIds, baseDir + DELIMITER + VERSION + DELIMITER + encode(appId) + DELIMITER,
                clusterIdPrefix, true);
    }

    @Override
    public void getProcsWithPrefix(Future<Set<String>> procIds, String baseDir, String appId, String clusterId, String procPrefix) {
        getListingAtLevelWithPrefix(procIds, baseDir + DELIMITER + VERSION + DELIMITER + encode(appId) + DELIMITER + encode(clusterId) + DELIMITER,
                procPrefix, false);
    }

    @Override
    public void getProfilesInTimeWindow(Future<List<AggregatedProfileNamingStrategy>> profiles, String baseDir, String appId, String clusterId, String proc, ZonedDateTime startTime, int durationInSeconds) {
        String prefix = baseDir + DELIMITER + VERSION + DELIMITER + encode(appId)
                + DELIMITER + encode(clusterId) + DELIMITER + proc + DELIMITER;

        asyncStorage.listAsync(prefix, true).thenApply(allObjects -> {
            ZonedDateTime start = startTime.minusSeconds(1);
            ZonedDateTime end = startTime.plusSeconds(durationInSeconds);

            List<AggregatedProfileNamingStrategy> summaryFilenames = allObjects.stream()
                    .map(AggregatedProfileNamingStrategy::fromFileName)
                    // filter by time and isSummary
                    .filter(s -> s.isSummaryFile && s.startTime.isAfter(start) && s.startTime.isBefore(end)).collect(Collectors.toList());

            return summaryFilenames;
        }).whenComplete((result, error) -> completeFuture(result, error, profiles));
    }

    @Override
    synchronized public void load(Future<AggregatedProfileInfo> future, AggregatedProfileNamingStrategy filename) {
        String fileNameKey = filename.getFileName(0);

        AggregatedProfileInfo cachedProfileInfo = cache.getIfPresent(fileNameKey);
        if(cachedProfileInfo == null) {
            boolean fileLoadInProgress = futuresForLoadingFiles.containsKey(fileNameKey);
            // save the future, so that it can be notified when the loading visit finishes
            saveRequestedFuture(fileNameKey, future);
            // set the timeout for this future
            vertx.setTimer(loadTimeout, timerId -> timeoutRequestedFuture(fileNameKey, future));

            if(!fileLoadInProgress) {
                workerExecutor.executeBlocking((Future<AggregatedProfileInfo> f) -> profileLoader.load(f, filename),
                        true,
                        result -> completeAggregatedProfileLoading(cache, result, fileNameKey));
            }
        }
        else {
            future.complete(cachedProfileInfo);
        }
    }

    @Override
    synchronized public void loadSummary(Future<AggregationWindowSummary> future, AggregatedProfileNamingStrategy filename) {

        if(!filename.isSummaryFile) {
            future.fail(new IllegalArgumentException(filename.getFileName(0) + " is not a summaryFile"));
            return;
        }

        String fileNameKey = filename.getFileName(0);

        AggregationWindowSummary cachedProfileInfo = summaryCache.getIfPresent(fileNameKey);
        if(cachedProfileInfo == null) {
            boolean fileLoadInProgress = futuresForLoadingFiles.containsKey(fileNameKey);
            // save the future, so that it can be notified when the loading visit finishes
            saveRequestedFuture(fileNameKey, future);
            // set the timeout for this future
            vertx.setTimer(loadTimeout, timerId -> timeoutRequestedFuture(fileNameKey, future));

            if(!fileLoadInProgress) {
                workerExecutor.executeBlocking((Future<AggregationWindowSummary> f) -> profileLoader.loadSummary(f, filename),
                        true,
                        result -> completeAggregatedProfileLoading(summaryCache, result, fileNameKey));
            }
        }
        else {
            future.complete(cachedProfileInfo);
        }
    }


    synchronized private <T> void saveRequestedFuture(String filename, Future<T> future) {
        FuturesList futures = futuresForLoadingFiles.get(filename);
        if(futures == null) {
            futures = new FuturesList();
            futuresForLoadingFiles.put(filename, futures);
        }
        futures.addFuture(future);
    }

    synchronized private <T> void timeoutRequestedFuture(String filename, Future<T> future) {
        if(future.isComplete()) {
            return;
        }
        FuturesList futures = futuresForLoadingFiles.get(filename);
        futures.removeFuture(future);

        future.fail(new TimeoutException("timeout while waiting for file to loadFromInputStream from store: " + filename));
    }

    synchronized private <T> void completeAggregatedProfileLoading(Cache<String, T> cache, AsyncResult<T> result, String filename) {
        if(result.succeeded()) {
            cache.put(filename, result.result());
        }

        // complete all dependent futures
        FuturesList futures = futuresForLoadingFiles.get(filename);
        futures.complete(result);

        futuresForLoadingFiles.remove(filename);
    }

    private <T> void completeFuture(T result, Throwable error, Future<T> future) {
        if(error == null) {
            future.complete(result);
        }
        else {
            future.fail(error);
        }
    }

    private String encode(String str) {
        return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));
    }

    private String decode(String str) {
        return new String(BaseEncoding.base32().decode(str), Charset.forName("utf-8"));
    }

    private static Pair<ZonedDateTime, ZonedDateTime> getInterval(AggregatedProfileNamingStrategy fileName) {
        return new Pair(fileName.startTime, fileName.startTime.plusSeconds(fileName.duration));
    }

    private static class Pair<T,U> {
        public T first;
        public U second;
        Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int hashCode() {
            int hash = 59;
            if (first != null) {
                hash = 31 * hash + first.hashCode();
            }
            if(second != null) {
                hash = 31 * hash + second.hashCode();
            }
            return hash;
        }

        @Override
        public boolean equals(Object that) {
            if(that == null || !(that instanceof Pair)) {
                return false;
            }

            return Objects.equals(this.first, ((Pair) that).first) && Objects.equals(this.second, ((Pair) that).second);
        }
    }

    private static class FuturesList<T> {
        List<Future<T>> futures = new ArrayList<>(2);

        synchronized public void addFuture(Future<T> future) {
            if(!exists(future)) {
                futures.add(future);
            }
        }

        synchronized public void removeFuture(Future<T> future) {
            futures.removeIf(f -> f == future);
        }

        synchronized public void complete(AsyncResult<T> result) {
            if(result.succeeded()) {
                futures.forEach(f -> f.complete(result.result()));
            }
            else {
                futures.forEach(f -> f.fail(result.cause()));
            }

            futures.clear();
        }

        private boolean exists(Future<T> future) {
            return futures.stream().anyMatch(f -> f == future);
        }
    }
}
