package fk.prof.userapi.verticles;

import fk.prof.userapi.api.ProfileStoreAPIImpl;
import fk.prof.userapi.model.FilteredProfiles;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.ServerSocket;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * Tests for {@link HttpVerticle} using mocked behaviour of ProfileStoreAPIImpl
 * Created by rohit.patiyal on 27/01/17.
 */
@RunWith(VertxUnitRunner.class)
public class RouterVerticleTest {
    private static final String URL_SEPARATOR = "/";
    private static final String P_APP_ID = "app1";
    private static final String NP_APP_ID = "foo";

    private static final String P_CLUSTER_ID = "cluster1";
    private static final String NP_CLUSTER_ID = "bar";
    private static final String P_PROC = "process1";
    private static final String NP_PROC = "main";

    private static final String P_ENCODED_TIME_STAMP = "2017-01-20T12:37:00.376%2B05:30";
    private static final String NP_ENCODED_TIME_STAMP = "2017-01-21T12:37:00.376%2B05:30";
    private static final ZonedDateTime P_TIME_STAMP = ZonedDateTime.parse("2017-01-20T12:37:00.376+05:30", DateTimeFormatter.ISO_ZONED_DATE_TIME);
    private static final ZonedDateTime NP_TIME_STAMP = ZonedDateTime.parse("2017-01-21T12:37:00.376+05:30", DateTimeFormatter.ISO_ZONED_DATE_TIME);

    private static final String P_WORKTYPE = "thread_sample_work";
    private static final String NP_WORKTYPE = "cpu_sample_work";


    private static final int DURATION = 1500;
    private static final long MORE_THAN_TIMEOUT = 3000;
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    private Vertx vertx;
    private HttpClient client;
    private int port = 8082;
    @InjectMocks
    private HttpVerticle routerVerticle;

    @Mock
    private ProfileStoreAPIImpl profileDiscoveryAPI;

    @Before
    public void setUp(TestContext testContext) throws Exception {
        vertx = Vertx.vertx();
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(new JsonObject("{\n" +
                "  \"http.port\": " + String.valueOf(port) + ",\n" +
                "  \"http.instances\": 1,\n" +
                "  \"req.timeout\": 2500,\n" +
                "  \"storage\":\"S3\",\n" +
                "  \"S3\" : {\n" +
                "    \"end.point\" : \"http://10.47.2.3:80\",\n" +
                "    \"access.key\": \"66ZX9WC7ZRO6S5BSO8TG\",\n" +
                "    \"secret.key\": \"fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+\"\n" +
                "  }\n" +
                "}\n"));
        client = vertx.createHttpClient();
        vertx.deployVerticle(routerVerticle, deploymentOptions, testContext.asyncAssertSuccess());
    }

    @After
    public void tearDown() throws Exception {
        vertx.close();
        client.close();
    }

    @Test
    public void TestRequestTimeout(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        String pPrefixSet = "(^$|a|ap|app|app1)";
        doAnswer(invocation -> {
                    Future<Set<String>> future = invocation.getArgument(0);
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(MORE_THAN_TIMEOUT);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return Sets.newSet(P_APP_ID);
                    }).whenComplete((result ,error) -> completeFuture(result, error, future));
            return null;
        }).when(profileDiscoveryAPI).getAppIdsWithPrefix(any(), any(), ArgumentMatchers.matches(pPrefixSet));

        client.getNow(port, "localhost", "/apps?prefix=a", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.SERVICE_UNAVAILABLE.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains("Service Unavailable"));
                async.complete();
            });
        });
    }

    @Test
    public void TestGetAppsRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        String pPrefixSet = "(^$|a|ap|app|app1)";
        String npPrefixSet = "(f|fo|foo)";
        doAnswer(invocation -> {
            Future<Set<String>> future = invocation.getArgument(0);
            CompletableFuture.supplyAsync(() -> Sets.newSet(P_APP_ID)).whenComplete((res, error) -> completeFuture(res, error, future));
            return null;
        }).when(profileDiscoveryAPI).getAppIdsWithPrefix(any(), any(), ArgumentMatchers.matches(pPrefixSet));
        doAnswer(invocation -> {
            Future<Set<String>> future = invocation.getArgument(0);
            CompletableFuture.supplyAsync(Sets::<String>newSet).whenComplete((res, error) -> completeFuture(res, error, future));
            return null;
        }).when(profileDiscoveryAPI).getAppIdsWithPrefix(any(), any(), ArgumentMatchers.matches(npPrefixSet));

        Future<Void> pCorrectPrefix = Future.future();
        Future<Void> pIncorrectPrefix = Future.future();
        Future<Void> pNoPrefix = Future.future();

        client.getNow(port, "localhost", "/apps?prefix=" + P_APP_ID.substring(0, 1 + new Random().nextInt(P_APP_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_APP_ID));
                testContext.assertFalse(buffer.toString().contains(NP_APP_ID));
                pCorrectPrefix.complete();
            });
        });
        client.getNow(port, "localhost", "/apps?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_APP_ID));
                testContext.assertFalse(buffer.toString().contains(NP_APP_ID));
                pIncorrectPrefix.complete();
            });
        });
        client.getNow(port, "localhost", "/apps?prefix=" + NP_APP_ID.substring(0, 1 + new Random().nextInt(NP_APP_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_APP_ID));
                testContext.assertFalse(buffer.toString().contains(NP_APP_ID));
                pNoPrefix.complete();
            });
        });

        CompositeFuture.all(pCorrectPrefix, pIncorrectPrefix, pNoPrefix).setHandler(compositeFutureAsyncResult -> async.complete());

    }

    @Test
    public void TestRootRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();

        client.getNow(port, "localhost", "/", httpClientResponse -> httpClientResponse.bodyHandler(buffer -> {
            testContext.assertTrue(buffer.toString().contains("UserAPI"));
            async.complete();
        }));
    }

    @Test
    public void TestGetClustersRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        String pPrefixSet = "(^$|c|cl|clu|clus|clust|cluste|cluster|cluster1)";
        String npPrefixSet = "(b|ba|bar)";
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(() -> Sets.newSet(P_CLUSTER_ID)).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getClusterIdsWithPrefix(any(), any(), eq(P_APP_ID), ArgumentMatchers.matches(pPrefixSet));
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(Sets::<String>newSet).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getClusterIdsWithPrefix(any(), any(), eq(P_APP_ID), ArgumentMatchers.matches(npPrefixSet));
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(Sets::<String>newSet).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getClusterIdsWithPrefix(any(), any(), eq(NP_APP_ID), ArgumentMatchers.matches(pPrefixSet));
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(Sets::<String>newSet).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getClusterIdsWithPrefix(any(), any(), eq(NP_APP_ID), ArgumentMatchers.matches(npPrefixSet));

        Future<Void> pAndCorrectPrefix = Future.future();
        Future<Void> pAndIncorrectPrefix = Future.future();
        Future<Void> pAndNoPrefix = Future.future();

        Future<Void> npAndPPrefix = Future.future();
        Future<Void> npAndNpPrefix = Future.future();
        Future<Void> npAndNoPrefix = Future.future();

        client.getNow(port, "localhost", "/cluster/" + P_APP_ID + "?prefix=" + P_CLUSTER_ID.substring(0, 1 + new Random().nextInt(P_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                pAndCorrectPrefix.complete();
            });
        });
        client.getNow(port, "localhost", "/cluster/" + P_APP_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                pAndNoPrefix.complete();
            });
        });
        client.getNow(port, "localhost", "/cluster/" + P_APP_ID + "?prefix=" + NP_CLUSTER_ID.substring(0, 1 + new Random().nextInt(NP_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                pAndIncorrectPrefix.complete();
            });
        });

        client.getNow(port, "localhost", "/cluster/" + NP_APP_ID + "?prefix=" + P_CLUSTER_ID.substring(0, 1 + new Random().nextInt(P_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                npAndPPrefix.complete();
            });
        });
        client.getNow(port, "localhost", "/cluster/" + NP_APP_ID + "?prefix=" + NP_CLUSTER_ID.substring(0, 1 + new Random().nextInt(NP_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                npAndNpPrefix.complete();
            });
        });
        client.getNow(port, "localhost", "/cluster/" + NP_APP_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                npAndNoPrefix.complete();
            });
        });

        CompositeFuture.all(pAndCorrectPrefix, pAndIncorrectPrefix, pAndNoPrefix, npAndPPrefix, npAndNpPrefix, npAndNoPrefix).setHandler(compositeFutureAsyncResult -> async.complete());
    }

    @Test
    public void TestGetProcRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        String pPrefixSet = "(^$|p|pr|pro|proc|proce|proces|process|process1)";
        String npPrefixSet = "(m|ma|mai|main)";

        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(() -> Sets.newSet(P_PROC)).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getProcsWithPrefix(any(), any(), eq(P_APP_ID), eq(P_CLUSTER_ID), ArgumentMatchers.matches(pPrefixSet));
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(Sets::<String>newSet).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getProcsWithPrefix(any(), any(), eq(P_APP_ID), eq(P_CLUSTER_ID), ArgumentMatchers.matches(npPrefixSet));
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(Sets::<String>newSet).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getProcsWithPrefix(any(), any(), eq(NP_APP_ID), eq(NP_CLUSTER_ID), ArgumentMatchers.matches(pPrefixSet));
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(Sets::<String>newSet).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getProcsWithPrefix(any(), any(), eq(NP_APP_ID), eq(NP_CLUSTER_ID), ArgumentMatchers.matches(npPrefixSet));

        Future<Void> pAndCorrectPrefix = Future.future();
        Future<Void> pAndIncorrectPrefix = Future.future();
        Future<Void> pAndNoPrefix = Future.future();

        Future<Void> npAndPPrefix = Future.future();
        Future<Void> npAndNpPrefix = Future.future();
        Future<Void> npAndNoPrefix = Future.future();

        client.getNow(port, "localhost", "/proc/" + P_APP_ID + URL_SEPARATOR + P_CLUSTER_ID + "?prefix=" + P_PROC.substring(0, 1 + new Random().nextInt(P_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                pAndCorrectPrefix.complete();
            });

        });
        client.getNow(port, "localhost", "/proc/" + P_APP_ID + URL_SEPARATOR + P_CLUSTER_ID + "?prefix=" + NP_PROC.substring(0, 1 + new Random().nextInt(NP_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                pAndIncorrectPrefix.complete();
            });

        });
        client.getNow(port, "localhost", "/proc/" + P_APP_ID + URL_SEPARATOR + P_CLUSTER_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                pAndNoPrefix.complete();
            });

        });
        client.getNow(port, "localhost", "/proc/" + NP_APP_ID + URL_SEPARATOR + NP_CLUSTER_ID + "?prefix=" + P_PROC.substring(0, 1 + new Random().nextInt(P_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                npAndPPrefix.complete();
            });

        });
        client.getNow(port, "localhost", "/proc/" + NP_APP_ID + URL_SEPARATOR + NP_CLUSTER_ID + "?prefix=" + NP_PROC.substring(0, 1 + new Random().nextInt(NP_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                npAndNpPrefix.complete();
            });

        });
        client.getNow(port, "localhost", "/proc/" + NP_APP_ID + URL_SEPARATOR + NP_CLUSTER_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                npAndNoPrefix.complete();
            });

        });

        CompositeFuture.all(pAndCorrectPrefix, pAndIncorrectPrefix, pAndNoPrefix, npAndPPrefix, npAndNpPrefix, npAndNoPrefix).setHandler(compositeFutureAsyncResult -> async.complete());

    }

    @Test
    public void TestGetProfilesRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();

        FilteredProfiles pProfile = new FilteredProfiles(P_TIME_STAMP, P_TIME_STAMP.plusSeconds(1800), Sets.newSet(P_WORKTYPE));

        FilteredProfiles nPProfile = new FilteredProfiles(P_TIME_STAMP, P_TIME_STAMP.plusSeconds(1500), Sets.newSet(NP_WORKTYPE));

        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(() -> Sets.newSet(pProfile)).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getProfilesInTimeWindow(any(), any(), eq(P_APP_ID), eq(P_CLUSTER_ID), eq(P_PROC), eq(P_TIME_STAMP), eq(DURATION));
        doAnswer(invocation -> {
            CompletableFuture.supplyAsync(Sets::<FilteredProfiles>newSet).whenComplete((res, error) -> completeFuture(res, error, invocation.getArgument(0)));
            return null;
        }).when(profileDiscoveryAPI).getProfilesInTimeWindow(any(), any(), eq(P_APP_ID), eq(P_CLUSTER_ID), eq(P_PROC), eq(NP_TIME_STAMP), eq(DURATION));

        Future<Void> pAndCorrectPrefix = Future.future();
        Future<Void> pAndIncorrectPrefix = Future.future();
        Future<Void> pAndNoPrefix = Future.future();

        client.getNow(port, "localhost", "/profiles/" + P_APP_ID + URL_SEPARATOR + P_CLUSTER_ID + URL_SEPARATOR + P_PROC + "?start=" + P_ENCODED_TIME_STAMP + "&duration=" + DURATION, httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_WORKTYPE));
                testContext.assertFalse(buffer.toString().contains(NP_WORKTYPE));
                pAndCorrectPrefix.complete();
            });

        });
        client.getNow(port, "localhost", "/profiles/" + P_APP_ID + URL_SEPARATOR + P_CLUSTER_ID + URL_SEPARATOR + P_PROC + "?start=" + NP_ENCODED_TIME_STAMP + "&duration=" + DURATION, httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_WORKTYPE));
                testContext.assertFalse(buffer.toString().contains(NP_WORKTYPE));
                pAndIncorrectPrefix.complete();
            });

        });
        client.getNow(port, "localhost", "/profiles/" + P_APP_ID + URL_SEPARATOR + P_CLUSTER_ID + URL_SEPARATOR + P_PROC, httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.BAD_REQUEST.code());
            pAndNoPrefix.complete();
            });

        CompositeFuture.all(pAndCorrectPrefix, pAndIncorrectPrefix, pAndNoPrefix).setHandler(compositeFutureAsyncResult -> async.complete());

    }

    private <T> void completeFuture(T result, Throwable error, Future<T> future) {
        if(error == null) {
            future.complete(result);
        }
        else {
            future.fail(error);
        }
    }
}
