package controller;

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
import model.IDataModel;
import model.Profile;
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
import java.util.Random;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RouterVerticle} RouterVerticle using mocked behaviour of D42Model
 * Created by rohit.patiyal on 27/01/17.
 */
@RunWith(VertxUnitRunner.class)
public class RouterVerticleTest {
    private static final String URL_SEPARATOR = "/";
    private static final String APP_ID = "app1";
    private static final String CLUSTER_ID = "cluster1";
    private static final String PROC = "process1";
    private static final String TIME_STAMP = "2017-01-20T12:37:00.376%2B05:30";
    private static final String DECODED_TIME_STAMP = "2017-01-20T12:37:00.376+05:30";
    private static final String DURATION = "1500";
    private static final long MORE_THAN_TIMEOUT = 3000;
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    private Vertx vertx;
    private HttpClient client;
    private int port = 8083;
    @InjectMocks
    private RouterVerticle routerVerticle;

    @Mock
    private IDataModel dataModel;

    @Before
    public void setUp(TestContext testContext) throws Exception {
        vertx = Vertx.vertx();
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();
        DeploymentOptions deploymentOptions = new DeploymentOptions()
                .setConfig(new JsonObject().put("http.port", port));
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
        when(dataModel.getAppIdsWithPrefix(ArgumentMatchers.matches("(^$|a|ap|app|app1)"))).thenAnswer(invocation -> {
            Thread.sleep(MORE_THAN_TIMEOUT);
            return Sets.newSet("app1");
        });
        client.getNow(port, "localhost", "/apps?prefix=a", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.SERVICE_UNAVAILABLE.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(""));
                async.complete();
            });
        });
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
    public void TestGetAppsRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        when(dataModel.getAppIdsWithPrefix(ArgumentMatchers.matches("(^$|a|ap|app|app1)"))).thenAnswer(invocation -> Sets.newSet("app1"));
        Future<Void> correct = Future.future();
        Future<Void> incorrect = Future.future();
        client.getNow(port, "localhost", "/apps?prefix=" + APP_ID.substring(0, new Random().nextInt(APP_ID.length())), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains("app1"));
                correct.complete();
            });
        });
        client.getNow(port, "localhost", "/apps?prefix=f", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains("app1"));
                incorrect.complete();
            });
        });

        CompositeFuture.all(correct, incorrect).setHandler(compositeFutureAsyncResult -> async.complete());

    }

    @Test
    public void TestGetClustersRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        when(dataModel.getClusterIdsWithPrefix(eq(APP_ID), ArgumentMatchers.matches("(^$|c|cl|clu|clus|clust|cluste|cluster|cluster1)"))).thenAnswer(invocation -> Sets.newSet("cluster1"));

        Future<Void> correct = Future.future();
        Future<Void> incorrect = Future.future();
        Future<Void> badRequest = Future.future();

        client.getNow(port, "localhost", "/cluster/" + APP_ID + "?prefix=" + CLUSTER_ID.substring(0, new Random().nextInt(CLUSTER_ID.length())), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains("cluster1"));
                correct.complete();
            });

        });
        client.getNow(port, "localhost", "/cluster/foo?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains("cluster1"));
                incorrect.complete();
            });
        });
        client.getNow(port, "localhost", "/cluster/?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.NOT_FOUND.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains("cluster1"));
                badRequest.complete();
            });
        });

        CompositeFuture.all(correct, incorrect, badRequest).setHandler(compositeFutureAsyncResult -> async.complete());
    }

    @Test
    public void TestGetProcRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        when(dataModel.getProcsWithPrefix(eq(APP_ID), eq(CLUSTER_ID), ArgumentMatchers.matches("(^$|p|pr|pro|proc|proce|proces|process|process1)"))).thenAnswer(invocation -> Sets.newSet("process1"));

        Future<Void> correct = Future.future();
        Future<Void> incorrect = Future.future();
        Future<Void> badRequest = Future.future();

        client.getNow(port, "localhost", "/proc/" + APP_ID + URL_SEPARATOR + CLUSTER_ID + "?prefix=" + PROC.substring(0, new Random().nextInt(PROC.length())), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains("process1"));
                correct.complete();
            });

        });
        client.getNow(port, "localhost", "/proc/foo/bar?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains("process1"));
                incorrect.complete();
            });
        });
        client.getNow(port, "localhost", "/proc/?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.NOT_FOUND.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains("process1"));
                badRequest.complete();
            });
        });

        CompositeFuture.all(correct, incorrect, badRequest).setHandler(compositeFutureAsyncResult -> async.complete());
    }

    @Test
    public void TestGetProfilesRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();

        Profile profile1 = new Profile("2017-01-20T12:37:20.551+05:30", ZonedDateTime.parse("2017-01-20T12:37:20.551+05:30").plusSeconds(1800).toString());
        profile1.setValues(Sets.newSet("worktype1"));

        Profile profile2 = new Profile("2017-01-20T12:37:20.551+05:30", ZonedDateTime.parse("2017-01-20T12:37:20.551+05:30").plusSeconds(1500).toString());
        profile2.setValues(Sets.newSet("iosamples", "cpusamples"));

        when(dataModel.getProfilesInTimeWindow(eq(APP_ID), eq(CLUSTER_ID), eq(PROC), eq(DECODED_TIME_STAMP), eq(DURATION))).thenAnswer(invocation -> Sets.newSet(profile2));
        when(dataModel.getProfilesInTimeWindow(eq(APP_ID), eq(CLUSTER_ID), eq(PROC), ArgumentMatchers.matches("(?!.*" + DECODED_TIME_STAMP + ").*"), ArgumentMatchers.matches("(?!.*" + DURATION + ").*"))).thenAnswer(invocation -> Sets.newSet(profile1, profile2));

        Future<Void> correct = Future.future();
        Future<Void> graceFail = Future.future();
        Future<Void> incorrect = Future.future();
        Future<Void> badRequest = Future.future();
        client.getNow(port, "localhost", "/profiles/" + APP_ID + URL_SEPARATOR + CLUSTER_ID + URL_SEPARATOR + PROC + "?start=" + TIME_STAMP + "&duration=" + DURATION, httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains("iosamples"));
                correct.complete();
            });
        });
        client.getNow(port, "localhost", "/profiles/" + APP_ID + URL_SEPARATOR + CLUSTER_ID + URL_SEPARATOR + PROC, httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains("iosamples") && buffer.toString().contains("worktype1"));
                graceFail.complete();
            });

        });
        client.getNow(port, "localhost", "/profiles/foo/bar/proc?start=2017-01-20T12:37:00.376%2B05:30&duration=" + DURATION, httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains("iosamples"));
                incorrect.complete();
            });
        });
        client.getNow(port, "localhost", "/profiles///?prof=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.NOT_FOUND.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains("iosamples"));
                badRequest.complete();
            });
        });

        CompositeFuture.all(correct, graceFail, incorrect, badRequest).setHandler(compositeFutureAsyncResult -> async.complete());
    }


}
