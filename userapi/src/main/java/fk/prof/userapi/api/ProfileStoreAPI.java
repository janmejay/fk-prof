package fk.prof.userapi.api;

import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.userapi.model.AggregatedProfileInfo;
import fk.prof.userapi.model.FilteredProfiles;
import io.vertx.core.Future;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Interface for DataStores containing aggregated profile data
 * Created by rohit.patiyal on 23/01/17.
 */
public interface ProfileStoreAPI {
    /**
     * Returns completable future which returns set of appIds from the DataStore filtered by the specified prefix
     *
     * @param appIdPrefix prefix to filter the appIds
     * @return completable future which returns set containing app ids
     */
    void getAppIdsWithPrefix(Future<Set<String>> appIds, String baseDir, String appIdPrefix);

    /**
     * Returns set of clusterIds of specified appId from the DataStore filtered by the specified prefix
     *
     * @param appId           appId of which the clusterIds are required
     * @param clusterIdPrefix prefix to filter the clusterIds
     * @return completable future which returns set containing cluster ids
     */
    void getClusterIdsWithPrefix(Future<Set<String>> clusterIds, String baseDir, String appId, String clusterIdPrefix);

    /**
     * Returns set of processes of specified appId and clusterId from the DataStore filtered by the specified prefix
     *
     * @param appId      appId of which the processes are required
     * @param clusterId  clusterId of which the processes are required
     * @param procPrefix prefix to filter the processes
     * @return completable future which returns set containing process names
     */
    void getProcsWithPrefix(Future<Set<String>> procIds, String baseDir, String appId, String clusterId, String procPrefix);

    /**
     * Returns set of profiles of specified appId, clusterId and process from the DataStore filtered by the specified time interval and duration
     *
     * @param appId             appId of which the profiles are required
     * @param clusterId         clusterId of which the profiles are required
     * @param proc              process of which the profiles are required
     * @param startTime         startTime to filter the profiles
     * @param durationInSeconds duration from startTime to filter the profiles
     * @return completable future which returns set containing profiles
     */
    void getProfilesInTimeWindow(Future<Set<FilteredProfiles>> profiles, String baseDir, String appId, String clusterId, String proc, ZonedDateTime startTime, int durationInSeconds);

    /**
     * Returns aggregated profile for the provided header
     * @param future
     * @param header
     */
    void load(Future<AggregatedProfileInfo> future, AggregatedProfileModel.Header header);
}
