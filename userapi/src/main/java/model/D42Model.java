package model;


import fk.prof.storage.AsyncStorage;
import fk.prof.storage.S3AsyncStorage;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Interacts with the AsyncStorage based on invocations from controller
 * Created by rohit.patiyal on 19/01/17.
 */
public class D42Model implements IDataModel {
    private static final String ACCESS_KEY = "66ZX9WC7ZRO6S5BSO8TG";
    private static final String SECRET_KEY = "fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+";
    private static final String S3_BACKUP_ELB_END_POINT = "http://10.47.2.3:80";
    private static final String VERSION = "v001";
    private static final String DELIMITER = "_";
    private static final String BUCKET = "bck1";
    private static final String PATH_SEPARATOR = "/";
    private static final int TIMEOUT = 2;
    private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(D42Model.class);
    private S3AsyncStorage store = null;
    private Base32 base32 = null;

    public D42Model() {
        base32 = new Base32();
        setStorage(new S3AsyncStorage(S3_BACKUP_ELB_END_POINT, ACCESS_KEY, SECRET_KEY, Executors.newCachedThreadPool()));
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

    private String getLastFromCommonPrefix(String commonPrefix) {
        String[] splits = commonPrefix.split(DELIMITER);
        return splits[splits.length - 1];
    }

    private Set<String> getListingAtLevelWithPrefix(String level, String objPrefix, boolean encoded) throws Exception {
        Set<String> commonPrefixes = store.listAsync(level, false).get(TIMEOUT, TimeUnit.SECONDS);
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
    }

    @Override
    public void setStorage(AsyncStorage store) {
        this.store = (S3AsyncStorage) store;
    }

    @Override
    public Set<String> getAppIdsWithPrefix(String appIdPrefix) throws Exception {
        return getListingAtLevelWithPrefix(BUCKET + PATH_SEPARATOR + VERSION + DELIMITER, appIdPrefix, true);
    }

    @Override
    public Set<String> getClusterIdsWithPrefix(String appId, String clusterIdPrefix) throws Exception {
        return getListingAtLevelWithPrefix(
                BUCKET + PATH_SEPARATOR + VERSION + DELIMITER + new String(base32.encode(appId.getBytes())) + DELIMITER,
                clusterIdPrefix,
                true);
    }

    @Override
    public Set<String> getProcsWithPrefix(String appId, String clusterId, String procPrefix) throws Exception {
        return getListingAtLevelWithPrefix(BUCKET + PATH_SEPARATOR + VERSION + DELIMITER + new String(base32.encode(appId.getBytes()))
                        + DELIMITER + new String(base32.encode(clusterId.getBytes())) + DELIMITER,
                procPrefix,
                false);
    }

    @Override
    public Set<Profile> getProfilesInTimeWindow(String appId, String clusterId, String proc, String startTime, String durationInSeconds) throws Exception {
        String prefix = BUCKET + PATH_SEPARATOR + VERSION + DELIMITER + new String(base32.encode(appId.getBytes()))
                + DELIMITER + new String(base32.encode(clusterId.getBytes())) + DELIMITER + proc + DELIMITER;
        Map<Profile, Set<String>> profilesMap = store.listAsync(prefix, true).get().stream().collect(Collectors.groupingBy(D42Model::getProfile, Collectors.mapping(D42Model::getWorkType, Collectors.toSet())));
        Set<Profile> profiles = profilesMap.entrySet().stream().map(profileSetEntry -> {
            profileSetEntry.getKey().setValues(profileSetEntry.getValue());
            return profileSetEntry.getKey();
        }).collect(Collectors.toSet());
        try {
            ZonedDateTime startTimeAsTime = ZonedDateTime.parse(startTime);
            Long durationInSecondsAsLong = Long.parseLong(durationInSeconds);
            profiles = profiles.stream().filter(k -> ZonedDateTime.parse(k.getEnd()).isBefore(startTimeAsTime.plusSeconds(durationInSecondsAsLong).plusNanos(1))
                    && ZonedDateTime.parse(k.getStart()).isAfter(startTimeAsTime.minusNanos(1))).collect(Collectors.toSet());
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
        }

        return profiles;
    }

}
