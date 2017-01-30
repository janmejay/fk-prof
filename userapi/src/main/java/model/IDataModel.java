package model;

import fk.prof.storage.AsyncStorage;

import java.util.Set;

/**
 * Interface for DataStores containing aggregated profile data
 * Created by rohit.patiyal on 23/01/17.
 */
public interface IDataModel {
    /**
     * Initializes the DataModel with DataStorage {@link AsyncStorage}
     *
     * @param store datastore object
     */
    void setStorage(AsyncStorage store);

    /**
     * Returns set of appIds from the DataStore filtered by the specified prefix
     *
     * @param appIdPrefix prefix to filter the appIds
     * @return set containing app ids
     */
    Set<String> getAppIdsWithPrefix(String appIdPrefix) throws Exception;

    /**
     * Returns set of clusterIds of specified appId from the DataStore filtered by the specified prefix
     *
     * @param appId           appId of which the clusterIds are required
     * @param clusterIdPrefix prefix to filter the clusterIds
     * @return set containing cluster ids
     */
    Set<String> getClusterIdsWithPrefix(String appId, String clusterIdPrefix) throws Exception;

    /**
     * Returns set of processes of specified appId and clusterId from the DataStore filtered by the specified prefix
     *
     * @param appId      appId of which the processes are required
     * @param clusterId  clusterId of which the processes are required
     * @param procPrefix prefix to filter the processes
     * @return set containing process names
     */
    Set<String> getProcsWithPrefix(String appId, String clusterId, String procPrefix) throws Exception;

    /**
     * Returns set of profiles of specified appId, clusterId and process from the DataStore filtered by the specified prefix
     *
     * @param appId             appId of which the profiles are required
     * @param clusterId         clusterId of which the profiles are required
     * @param proc              process of which the profiles are required
     * @param startTime         startTime to filter the profiles
     * @param durationInSeconds duration from startTime to filter the profiles
     * @return set containing profiles
     */
    Set<Profile> getProfilesInTimeWindow(String appId, String clusterId, String proc, String startTime, String durationInSeconds) throws Exception;
}
