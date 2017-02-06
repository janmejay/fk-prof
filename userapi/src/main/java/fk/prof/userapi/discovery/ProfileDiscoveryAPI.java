package fk.prof.userapi.discovery;

import fk.prof.userapi.model.Profile;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for DataStores containing aggregated profile data
 * Created by rohit.patiyal on 23/01/17.
 */
public interface ProfileDiscoveryAPI {
    /**
     * Returns completable future which returns set of appIds from the DataStore filtered by the specified prefix
     *
     * @param appIdPrefix prefix to filter the appIds
     * @return completable future which returns set containing app ids
     */
    CompletableFuture<Set<String>> getAppIdsWithPrefix(String appIdPrefix);

    /**
     * Returns set of clusterIds of specified appId from the DataStore filtered by the specified prefix
     *
     * @param appId           appId of which the clusterIds are required
     * @param clusterIdPrefix prefix to filter the clusterIds
     * @return completable future which returns set containing cluster ids
     */
    CompletableFuture<Set<String>> getClusterIdsWithPrefix(String appId, String clusterIdPrefix);

    /**
     * Returns set of processes of specified appId and clusterId from the DataStore filtered by the specified prefix
     *
     * @param appId      appId of which the processes are required
     * @param clusterId  clusterId of which the processes are required
     * @param procPrefix prefix to filter the processes
     * @return completable future which returns set containing process names
     */
    CompletableFuture<Set<String>> getProcsWithPrefix(String appId, String clusterId, String procPrefix);

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
    CompletableFuture<Set<Profile>> getProfilesInTimeWindow(String appId, String clusterId, String proc, String startTime, String durationInSeconds);
}
