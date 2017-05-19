package fk.prof.backend.model.policy;

import com.google.common.io.BaseEncoding;
import fk.prof.backend.mock.MockPolicyData;
import fk.prof.backend.proto.PolicyDTO;
import fk.prof.backend.util.ZookeeperUtil;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.ZKPaths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import recording.Recorder;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test for ZKWithCacheBasedPolicyStore
 * Created by rohit.patiyal on 09/03/17.
 */
@RunWith(VertxUnitRunner.class)
public class ZookeeperBasedPolicyStoreAPITest {

    private static final String POLICY_PATH = "/policy";
    private static final String DELIMITER = "/";

    private TestingServer testingServer;
    private CuratorFramework curatorClient;
    private ZookeeperBasedPolicyStoreAPI policyStoreAPI;

    private static String encode(String str) {
        return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));
    }

    @Before
    public void setUp() throws Exception {
        testingServer = new TestingServer();
        Timing timing = new Timing();
        curatorClient = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
        curatorClient.start();
        curatorClient.blockUntilConnected(10, TimeUnit.SECONDS);
        curatorClient.create().forPath(POLICY_PATH);
        policyStoreAPI = new ZookeeperBasedPolicyStoreAPI(curatorClient, POLICY_PATH);
    }

    @After
    public void tearDown() throws Exception {
        curatorClient.close();
        testingServer.close();
    }

    @Test(timeout = 4000)
    public void testGetPolicy(TestContext context) throws Exception {
        final Async async = context.async();
        //PRE
        policyStoreAPI.createPolicy(MockPolicyData.mockProcessGroups.get(0), MockPolicyData.mockPolicyDetails.get(0)).setHandler(ar -> {
            if (ar.succeeded()) {
                PolicyDTO.PolicyDetails got = policyStoreAPI.getPolicy(MockPolicyData.mockProcessGroups.get(0));
                //GET GIVES THE JUST CREATED POLICY
                context.assertEquals(got, MockPolicyData.mockPolicyDetails.get(0));
            } else {
                context.fail(ar.cause());
            }
            async.complete();
        });
    }

    @Test(timeout = 4000)
    public void testCreatePolicy(TestContext context) throws Exception {
        final Async async = context.async();
        Recorder.ProcessGroup pG = MockPolicyData.mockProcessGroups.get(0);
        policyStoreAPI.createPolicy(pG, MockPolicyData.mockPolicyDetails.get(0)).setHandler(ar -> {
            //SUCCESS ON CREATION OF A NEW POLICY
            context.assertTrue(ar.succeeded());
            PolicyDTO.PolicyDetails got = policyStoreAPI.getPolicy(MockPolicyData.mockProcessGroups.get(0));
            String zNodePath = POLICY_PATH + DELIMITER + encode(pG.getAppId()) + DELIMITER + encode(pG.getCluster()) + DELIMITER + encode(pG.getProcName());
            try {
                //DATA IS ACTUALLY WRITTEN TO ZK
                context.assertEquals(PolicyDTO.PolicyDetails.parseFrom(ZookeeperUtil.readLatestSeqZNodeChild(curatorClient, zNodePath)), MockPolicyData.mockPolicyDetails.get(0));
                //GET GIVES THE CREATED POLICY
                context.assertEquals(got, MockPolicyData.mockPolicyDetails.get(0));
                policyStoreAPI.createPolicy(MockPolicyData.mockProcessGroups.get(0), MockPolicyData.mockPolicyDetails.get(1)).setHandler(ar2 -> {
                    //FAILURE ON RECREATING AN EXISTING POLICY
                    context.assertTrue(ar2.failed());
                    PolicyDTO.PolicyDetails got2 = policyStoreAPI.getPolicy(MockPolicyData.mockProcessGroups.get(0));
                    //POLICY IS SAME AS ORIGINAL
                    context.assertEquals(got2, MockPolicyData.mockPolicyDetails.get(0));
                });
            } catch (Exception e) {
                context.fail(e);
            }
            async.complete();
        });
    }

    @Test(timeout = 2000)
    public void testConcurrentSetPolicy(TestContext context) throws Exception {
        final Async async = context.async();
        policyStoreAPI.createPolicy(MockPolicyData.mockProcessGroups.get(0), MockPolicyData.mockPolicyDetails.get(0)).setHandler(ar -> {
            if (ar.succeeded()) {
                int nThreads = 1000;
                //Latch to start the test thread after all the requests are served
                CountDownLatch latch = new CountDownLatch(nThreads);
                final PolicyDTO.PolicyDetails[] latestPolicyDetails = new PolicyDTO.PolicyDetails[1];
                AtomicInteger successes = new AtomicInteger(1);
                //Spawn threads mimicking simultaneous requests
                for (int t = 0; t < nThreads; t++) {
                    int finalT = t;
                    PolicyDTO.PolicyDetails policyDetails = MockPolicyData.mockPolicyDetails.get(0).toBuilder().setCreatedAt(String.valueOf(finalT)).build();
                    new Thread(() -> {
                        policyStoreAPI.updatePolicy(MockPolicyData.mockProcessGroups.get(0), policyDetails).setHandler(ar2 -> {
                            if (ar2.succeeded()) {
                                successes.incrementAndGet();
                                latestPolicyDetails[0] = policyDetails;
                            }
                            latch.countDown();
                        });
                    }).start();
                }

                //Test Thread : verifies that only the successful requests make entry to ZK
                new Thread(() -> {
                    Recorder.ProcessGroup pG = MockPolicyData.mockProcessGroups.get(0);
                    try {
                        latch.await(2, TimeUnit.SECONDS);
                        String zNodePath = POLICY_PATH + DELIMITER + encode(pG.getAppId()) + DELIMITER + encode(pG.getCluster()) + DELIMITER + encode(pG.getProcName());
                        List<String> sortedChildren = ZKPaths.getSortedChildren(curatorClient.getZookeeperClient().getZooKeeper(), zNodePath);
                        //VERSIONS IN ZK Node IS EQUAL AS SUCCESSFUL API REQUESTS (includes 1 CREATE request)
                        context.assertEquals(sortedChildren.size(), successes.get());
                        //GET RETURNS THE LAST SUCCESSFULLY WRITTEN POLICYDETAIL
                        context.assertEquals(latestPolicyDetails[0], policyStoreAPI.getPolicy(pG));
                    } catch (Exception e) {
                        context.fail(e);
                        e.printStackTrace();
                    }
                    async.complete();

                }).start();
            }
        });
    }

    @Test(timeout = 2000)
    public void testUpdatePolicy(TestContext context) throws Exception {
        final Async async = context.async();
        Future<Void> future1 = Future.future();
        Future<Void> future2 = Future.future();

        //USING PG1
        policyStoreAPI.createPolicy(MockPolicyData.mockProcessGroups.get(0), MockPolicyData.mockPolicyDetails.get(0)).setHandler(ar -> {
            if (ar.succeeded()) {
                policyStoreAPI.updatePolicy(MockPolicyData.mockProcessGroups.get(0), MockPolicyData.mockPolicyDetails.get(1)).setHandler(ar2 -> {
                    //SUCCESS ON UPDATING AN EXISTING POLICY
                    context.assertTrue(ar2.succeeded());
                    PolicyDTO.PolicyDetails got2 = policyStoreAPI.getPolicy(MockPolicyData.mockProcessGroups.get(0));
                    //POLICY IS THE NEW ONE
                    context.assertEquals(got2, MockPolicyData.mockPolicyDetails.get(1));
                    future1.complete();
                });
            } else {
                future1.fail(ar.cause());
                context.fail(ar.cause());
            }
        });

        //USING PG2
        policyStoreAPI.updatePolicy(MockPolicyData.mockProcessGroups.get(1), MockPolicyData.mockPolicyDetails.get(0)).setHandler(ar -> {
            //FAILURE ON UPDATING A NON EXISTING POLICY
            context.assertTrue(ar.failed());
            PolicyDTO.PolicyDetails got2 = policyStoreAPI.getPolicy(MockPolicyData.mockProcessGroups.get(1));
            //POLICY IS STILL NULL
            context.assertNull(got2);
            future2.complete();
        });

        CompositeFuture.all(future1, future2).setHandler(event -> async.complete());
    }

    @Test(timeout = 12000)
    public void testInit(TestContext context) throws Exception {
        final Async async = context.async();

        Future<Void> future1 = policyStoreAPI.createPolicy(MockPolicyData.mockProcessGroups.get(0), MockPolicyData.mockPolicyDetails.get(0));
        Future<Void> future2 = Future.future();
        policyStoreAPI.createPolicy(MockPolicyData.mockProcessGroups.get(1), MockPolicyData.mockPolicyDetails.get(1)).setHandler(ar -> {
            if (ar.succeeded()) {
                policyStoreAPI.updatePolicy(MockPolicyData.mockProcessGroups.get(1), MockPolicyData.mockPolicyDetails.get(2)).setHandler(ar2 -> {
                    if (ar2.succeeded()) {
                        future2.complete();
                    } else {
                        future2.fail(ar2.cause());
                    }
                });
            } else {
                future2.fail(ar.cause());
            }
        });
        CompositeFuture.all(future1, future2).setHandler(event -> {
            ZookeeperBasedPolicyStoreAPI anotherPolicyStore = new ZookeeperBasedPolicyStoreAPI(curatorClient, POLICY_PATH);
            try {
                //GET RETURNS NULL RESULTS FROM ANOTHERSTORE BEFORE INIT
                PolicyDTO.PolicyDetails got1 = anotherPolicyStore.getPolicy(MockPolicyData.mockProcessGroups.get(0));
                PolicyDTO.PolicyDetails got2 = anotherPolicyStore.getPolicy(MockPolicyData.mockProcessGroups.get(1));
                context.assertNull(got1);
                context.assertNull(got2);

                anotherPolicyStore.init();
                //GET RETURNS SAME RESULTS AS ORIGINAL STORE FROM ANOTHER STORE AFTER INIT
                got1 = anotherPolicyStore.getPolicy(MockPolicyData.mockProcessGroups.get(0));
                got2 = anotherPolicyStore.getPolicy(MockPolicyData.mockProcessGroups.get(1));
                context.assertEquals(got1, MockPolicyData.mockPolicyDetails.get(0));
                context.assertEquals(got2, MockPolicyData.mockPolicyDetails.get(2));
            } catch (Exception e) {
                context.fail(e);
            }
            async.complete();
        });
    }

    @Test(timeout = 4000)
    public void testGetAppIds(TestContext context) {
        final Async async = context.async();
        List<Future> futures = new ArrayList<>();
        for (Recorder.ProcessGroup pG : MockPolicyData.mockProcessGroups) {
            futures.add(policyStoreAPI.createPolicy(pG, MockPolicyData.mockPolicyDetails.get(0)));
        }
        Map<String, Set<String>> testPairs = new HashMap<String, Set<String>>() {{
            put("a", new HashSet<>(Arrays.asList("a1", "a2")));
            put("", new HashSet<>(Arrays.asList("a1", "a2", "b1")));
            put(null, new HashSet<>());
        }};
        CompositeFuture.all(futures).setHandler(event -> {
            if (event.succeeded()) {
                for (String pre : testPairs.keySet()) {
                    Set<String> got = policyStoreAPI.getAppIds(pre);
                    context.assertEquals(got, testPairs.get(pre));
                }
            } else {
                context.fail(event.cause());
            }
            async.complete();
        });

    }

    @Test(timeout = 4000)
    public void testGetClusterIds(TestContext context) {
        final Async async = context.async();
        List<Future> futures = new ArrayList<>();
        for (Recorder.ProcessGroup pG : MockPolicyData.mockProcessGroups) {
            futures.add(policyStoreAPI.createPolicy(pG, MockPolicyData.mockPolicyDetails.get(1)));
        }
        Map<List<String>, Set<String>> testPairs = new HashMap<List<String>, Set<String>>() {{
            put(Arrays.asList("a1", "c1"), new HashSet<>(Arrays.asList("c1")));
            put(Arrays.asList("a1", ""), new HashSet<>(Arrays.asList("c1", "c2")));
            put(Arrays.asList("", ""), new HashSet<>());
            put(Arrays.asList("a2", null), new HashSet<>());
        }};
        CompositeFuture.all(futures).setHandler(event -> {
            if (event.succeeded()) {
                for (List<String> args : testPairs.keySet()) {
                    Set<String> got = policyStoreAPI.getClusterIds(args.get(0), args.get(1));
                    context.assertEquals(got, testPairs.get(args));
                }
            } else {
                context.fail(event.cause());
            }
            async.complete();
        });

    }

    @Test(timeout = 4000)
    public void testGetProcNames(TestContext context) {
        final Async async = context.async();
        List<Future> futures = new ArrayList<>();
        for (Recorder.ProcessGroup pG : MockPolicyData.mockProcessGroups) {
            futures.add(policyStoreAPI.createPolicy(pG, MockPolicyData.mockPolicyDetails.get(2)));
        }
        Map<List<String>, Set<String>> testPairs = new HashMap<List<String>, Set<String>>() {{
            put(Arrays.asList("a1", "c1", "p1"), new HashSet<>(Arrays.asList("p1")));
            put(Arrays.asList("a1", "c1", ""), new HashSet<>(Arrays.asList("p1", "p2")));
            put(Arrays.asList("", "c1", ""), new HashSet<>());
            put(Arrays.asList("", "c1", null), new HashSet<>());
        }};
        CompositeFuture.all(futures).setHandler(event -> {
            if (event.succeeded()) {
                for (List<String> args : testPairs.keySet()) {
                    Set<String> got = policyStoreAPI.getProcNames(args.get(0), args.get(1), args.get(2));
                    context.assertEquals(got, testPairs.get(args));
                }
            } else {
                context.fail(event.cause());
            }
            async.complete();
        });
    }

}