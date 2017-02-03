package fk.prof.userapi.discovery;


import fk.prof.storage.AsyncStorage;
import fk.prof.userapi.model.Profile;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Interacts with the {@link AsyncStorage} based on invocations from controller
 * Created by rohit.patiyal on 19/01/17.
 */
public class ProfileDiscoveryAPIImpl implements ProfileDiscoveryAPI {

    private static final String VERSION = "v001";
    private static final String DELIMITER = "_";
    private static final String BUCKET = "bck1";
    private static final String PATH_SEPARATOR = "/";
    private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ProfileDiscoveryAPIImpl.class);
    private AsyncStorage asyncStorage = null;
    private Base32 base32 = null;


    public ProfileDiscoveryAPIImpl(AsyncStorage asyncStorage) {
        this.base32 = new Base32();
        this.asyncStorage = asyncStorage;
    }

    private static String getWorkType(String key) {
        String[] splits = key.split(DELIMITER);
        return splits[6];
    }

    private static Profile getProfile(String key) {
        String[] splits = key.split(DELIMITER);
        String start = splits[4];
        return new Profile(start, ZonedDateTime.parse(start).plusSeconds(Long.parseLong(splits[5])).toString());
    }

    private ZonedDateTime getStartTime(String key) {
        String[] splits = key.split(DELIMITER);
        return ZonedDateTime.parse(splits[4]);
    }

    private String getLastFromCommonPrefix(String commonPrefix) {
        String[] splits = commonPrefix.split(DELIMITER);
        return splits[splits.length - 1];
    }

    private CompletableFuture<Set<String>> getListingAtLevelWithPrefix(String level, String objPrefix, boolean encoded) {
        CompletableFuture<Set<String>> commonPrefixesFuture = asyncStorage.listAsync(level, false);
        return commonPrefixesFuture.thenApply(commonPrefixes -> {
            Set<String> objs = new HashSet<>();
            for (String commonPrefix : commonPrefixes) {
                String objName = getLastFromCommonPrefix(commonPrefix);
                if (encoded) {
                    objName = new String(base32.decode(objName.getBytes()));
                }
                if (objName.indexOf(objPrefix) == 0) {
                    objs.add(objName);
                }
            }
            return objs;
        });
    }

    @Override
    public CompletableFuture<Set<String>> getAppIdsWithPrefix(String appIdPrefix) {
        return getListingAtLevelWithPrefix(BUCKET + PATH_SEPARATOR + VERSION + DELIMITER, appIdPrefix, true);
    }

    @Override
    public CompletableFuture<Set<String>> getClusterIdsWithPrefix(String appId, String clusterIdPrefix) {
        return getListingAtLevelWithPrefix(
                BUCKET + PATH_SEPARATOR + VERSION + DELIMITER + new String(base32.encode(appId.getBytes())) + DELIMITER,
                clusterIdPrefix,
                true);
    }

    @Override
    public CompletableFuture<Set<String>> getProcsWithPrefix(String appId, String clusterId, String procPrefix) {
        return getListingAtLevelWithPrefix(BUCKET + PATH_SEPARATOR + VERSION + DELIMITER + new String(base32.encode(appId.getBytes()))
                        + DELIMITER + new String(base32.encode(clusterId.getBytes())) + DELIMITER,
                procPrefix,
                false);
    }

    @Override
    public CompletableFuture<Set<Profile>> getProfilesInTimeWindow(String appId, String clusterId, String proc, String startTime, String durationInSeconds) {

        String prefix = BUCKET + PATH_SEPARATOR + VERSION + DELIMITER + new String(base32.encode(appId.getBytes()))
                + DELIMITER + new String(base32.encode(clusterId.getBytes())) + DELIMITER + proc + DELIMITER;

        CompletableFuture<Set<String>> allObjectsFuture = asyncStorage.listAsync(prefix, true);
        return allObjectsFuture.thenApply(allObjects -> {
            Stream<String> streamObjects = allObjects.stream();
            try {
                ZonedDateTime startTimeAsTime = ZonedDateTime.parse(startTime);
                Long durationInSecondsAsLong = Long.parseLong(durationInSeconds);
                streamObjects = streamObjects.filter(s -> (getStartTime(s).isAfter(startTimeAsTime.minusNanos(1)) && getStartTime(s).isBefore(startTimeAsTime.plusSeconds(durationInSecondsAsLong).plusNanos(1))));
            } catch (Exception e) {
                LOGGER.error(e.getLocalizedMessage());
            }
            //Group all objects by their time interval (profile object) and collect all the worktypes as a set
            //This is map with key is a timeinterval and value corresponding to it is the set of worktypes collected
            Map<Profile, Set<String>> profilesMap = streamObjects.collect(Collectors.groupingBy(ProfileDiscoveryAPIImpl::getProfile, Collectors.mapping(ProfileDiscoveryAPIImpl::getWorkType, Collectors.toSet())));

            //Converting the map to a set by moving the map's value inside the key object and then taking the keySet
            return profilesMap.entrySet().stream().map(profileSetEntry -> {
                profileSetEntry.getKey().setValues(profileSetEntry.getValue());
                return profileSetEntry.getKey();
            }).collect(Collectors.toSet());
        });
    }

}
