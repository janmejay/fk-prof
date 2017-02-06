package fk.prof.userapi.model;

import fk.prof.storage.AsyncStorage;
import fk.prof.userapi.discovery.ProfileDiscoveryAPIImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


/**
 * Tests for {@link ProfileDiscoveryAPIImpl} using mocked behaviour of listAysnc {@link AsyncStorage} API
 * Created by rohit.patiyal on 24/01/17.
 */

@RunWith(MockitoJUnitRunner.class)
public class ProfileDiscoveryAPITest {

    private static final String DELIMITER = "_";

    @InjectMocks
    private ProfileDiscoveryAPIImpl profileDiscoveryAPI;

    @Mock
    private AsyncStorage asyncStorage;

    private Set<String> getObjList(String prefix, boolean recursive) {
        String objects[] = {
                "v001_MZXW6===_MJQXE===_main_2017-01-20T12:37:20.551+05:30_1500_iosamples_0001",
                "v001_MZXW6===_MJQXE===_main_2017-01-20T12:37:20.551+05:30_1500_cpusamples_0001",
                "v001_MFYHAMI=_MNWHK43UMVZDC===_process1_2017-01-20T12:37:20.551+05:30_1500_worktype1_0001",
                "v001_MFYHAMI=_MNWHK43UMVZDC===_process1_2017-01-20T12:37:20.551+05:30_1800_worktype2_0001",
        };
        Set<String> resultObjects = new HashSet<>();
        for (String obj : objects) {
            if (obj.indexOf(prefix) == 0) {
                if (recursive) {
                    resultObjects.add(obj);
                } else {
                    resultObjects.add(obj.substring(0, prefix.length() + obj.substring(prefix.length()).indexOf(DELIMITER)));
                }
            }
        }
        return resultObjects;
    }

    @Before
    public void setUp() throws Exception {
        when(asyncStorage.listAsync(anyString(), anyBoolean())).thenAnswer(invocation -> {
            String path1 = invocation.getArgument(0);
            String fileName = path1.split("/")[1];
            Boolean recursive = invocation.getArgument(1);
            return CompletableFuture.supplyAsync(() -> getObjList(fileName, recursive));
        });

    }

    @Test
    public void TestGetAppIdsWithPrefix() throws Exception {
        Map<String, Collection<Object>> appIdTestPairs = new HashMap<String, Collection<Object>>() {
            {
                put("app", Sets.newSet("app1"));
                put("", Sets.newSet("app1", "foo"));
            }
        };

        for (Map.Entry<String, Collection<Object>> entry : appIdTestPairs.entrySet()) {
            assertThat(profileDiscoveryAPI.getAppIdsWithPrefix(entry.getKey()).get(), is(entry.getValue()));
        }
    }

    @Test
    public void TestGetClusterIdsWithPrefix() throws Exception {

        Map<List<String>, Collection<?>> appIdTestPairs = new HashMap<List<String>, Collection<?>>() {
            {
                put(Arrays.asList("app1", "cl"), Sets.newSet("cluster1"));
                put(Arrays.asList("app1", ""), Sets.newSet("cluster1"));
                put(Arrays.asList("foo", "b"), Sets.newSet("bar"));
                put(Arrays.asList("np", "np"), Sets.newSet());
                put(Arrays.asList("app1", "b"), Sets.newSet());
                put(Arrays.asList("", ""), Sets.newSet());
            }
        };

        for (Map.Entry<List<String>, Collection<?>> entry : appIdTestPairs.entrySet()) {
            Set<String> got = profileDiscoveryAPI.getClusterIdsWithPrefix(entry.getKey().get(0), entry.getKey().get(1)).get();
            assertThat(got, is(entry.getValue()));
        }
    }

    @Test
    public void TestGetProcsWithPrefix() throws Exception {
        Map<List<String>, Collection<?>> appIdTestPairs = new HashMap<List<String>, Collection<?>>() {
            {
                put(Arrays.asList("app1", "cluster1", "pr"), Sets.newSet("process1"));
                put(Arrays.asList("app1", "", ""), Sets.newSet());
                put(Arrays.asList("foo", "bar", ""), Sets.newSet("main"));
                put(Arrays.asList("", "", ""), Sets.newSet());
            }
        };

        for (Map.Entry<List<String>, Collection<?>> entry : appIdTestPairs.entrySet()) {
            Set<String> got = profileDiscoveryAPI.getProcsWithPrefix(entry.getKey().get(0), entry.getKey().get(1), entry.getKey().get(2)).get();
            assertThat(got, is(entry.getValue()));
        }
    }

    @Test
    public void TestGetProfilesInTimeWindow() throws Exception {
        Profile profile1 = new Profile("2017-01-20T12:37:20.551+05:30", ZonedDateTime.parse("2017-01-20T12:37:20.551+05:30").plusSeconds(1500).toString());
        profile1.setValues(Sets.newSet("worktype1"));

        Profile profile2 = new Profile("2017-01-20T12:37:20.551+05:30", ZonedDateTime.parse("2017-01-20T12:37:20.551+05:30").plusSeconds(1800).toString());
        profile2.setValues(Sets.newSet("worktype2"));

        Profile profile3 = new Profile("2017-01-20T12:37:20.551+05:30", ZonedDateTime.parse("2017-01-20T12:37:20.551+05:30").plusSeconds(1500).toString());
        profile3.setValues(Sets.newSet("iosamples", "cpusamples"));

        Map<List<String>, Collection<?>> appIdTestPairs = new HashMap<List<String>, Collection<?>>() {
            {
                put(Arrays.asList("app1", "cluster1", "process1", "2017-01-20T12:37:20.551+05:30", "1600"),
                        Sets.newSet(profile1, profile2));
                put(Arrays.asList("app1", "cluster1", "process1", "2017-01-20T12:37:20.551+05:30", "1900"),
                        Sets.newSet(profile1, profile2));
                put(Arrays.asList("foo", "bar", "main", "2017-01-20T12:37:20.551+05:30", "1900"),
                        Sets.newSet(profile3));
            }
        };

        for (Map.Entry<List<String>, Collection<?>> entry : appIdTestPairs.entrySet()) {
            Set<Profile> got = profileDiscoveryAPI.getProfilesInTimeWindow(entry.getKey().get(0), entry.getKey().get(1), entry.getKey().get(2), entry.getKey().get(3), entry.getKey().get(4)).get();
            assertEquals(entry.getValue(), got);
        }
    }

}

