package fk.prof.backend.model.policy;

import fk.prof.backend.exception.PolicyException;
import fk.prof.backend.proto.PolicyDTO;
import fk.prof.backend.util.EncodingUtil;
import fk.prof.backend.util.ZookeeperUtil;
import fk.prof.backend.util.proto.PolicyProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import recording.Recorder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static fk.prof.backend.util.ZookeeperUtil.DELIMITER;

/**
 * Zookeeper based implementation of the policy store
 * Created by rohit.patiyal on 18/05/17.
 */
public class ZookeeperBasedPolicyStoreAPI implements PolicyStoreAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperBasedPolicyStoreAPI.class);
    private static final String POLICY_NODE_PREFIX = "0";
    private static final String VERSION = "v0001";
    private final CuratorFramework curatorClient;
    private final String policyPath;
    private final Vertx vertx;
    private final Map<Recorder.ProcessGroup, PolicyDTO.VersionedPolicyDetails> policyLookup = new ConcurrentHashMap<>();
    private boolean initialized;


    ZookeeperBasedPolicyStoreAPI(Vertx vertx, CuratorFramework curatorClient, String policyPath) {
        if (vertx == null) {
            throw new IllegalArgumentException("Vertx instance is required");
        }
        if (curatorClient == null) {
            throw new IllegalArgumentException("Curator client is required");
        }
        if (policyPath == null) {
            throw new IllegalArgumentException("Policy path in zookeeper hierarchy is required");
        }
        this.vertx = vertx;
        this.curatorClient = curatorClient;
        this.policyPath = policyPath;
        this.initialized = false;
    }

    private void populateCacheFromZK() throws Exception {
        CountDownLatch syncLatch = new CountDownLatch(1);
        RuntimeException syncException = new RuntimeException();

        //ZK Sync operation always happens async, since this is essential for us to proceed further, using a latch here
        ZookeeperUtil.sync(curatorClient, policyPath).setHandler(ar -> {
            if (ar.failed()) {
                syncException.initCause(ar.cause());
            }
            syncLatch.countDown();
        });

        boolean syncCompleted = syncLatch.await(10, TimeUnit.SECONDS);
        if (!syncCompleted) {
            throw new PolicyException("ZK sync operation taking longer than expected", true);
        }
        if (syncException.getCause() != null) {
            throw new PolicyException(syncException, true);
        }
        for (String appId : curatorClient.getChildren().forPath(policyPath + DELIMITER + VERSION)) {
            for (String clusterId : curatorClient.getChildren().forPath(policyPath + DELIMITER + VERSION + DELIMITER + appId)) {
                for (String procName : curatorClient.getChildren().forPath(policyPath + DELIMITER + VERSION + DELIMITER + appId + DELIMITER + clusterId)) {
                    String zNodePath = policyPath + DELIMITER + VERSION + DELIMITER + appId + DELIMITER + clusterId + DELIMITER + procName;
                    Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(EncodingUtil.decode32(appId))
                            .setCluster(EncodingUtil.decode32(clusterId))
                            .setProcName(EncodingUtil.decode32(procName)).build();
                    PolicyDTO.PolicyDetails policyDetails = PolicyDTO.PolicyDetails.parseFrom(ZookeeperUtil.readLatestSeqZNodeChild(curatorClient, zNodePath));
                    int version = Integer.parseInt(ZookeeperUtil.getLatestSeqZNodeChildName(curatorClient, zNodePath));
                    PolicyDTO.VersionedPolicyDetails versionedPolicyDetails = PolicyDTO.VersionedPolicyDetails.newBuilder().setPolicyDetails(policyDetails).setVersion(version).build();
                    policyLookup.put(processGroup, versionedPolicyDetails);
                }
            }
        }
    }

    void init() throws Exception {
        synchronized (this) {
            if (!initialized) {
                populateCacheFromZK();
                initialized = true;
            }
        }
    }

    public Set<String> getAppIds(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix is required");
        }
        return policyLookup.keySet().stream().map(Recorder.ProcessGroup::getAppId)
                .filter(appIds -> appIds.startsWith(prefix)).collect(Collectors.toSet());
    }

    public Set<String> getClusterIds(String appId, String prefix) {
        if (appId == null) {
            throw new IllegalArgumentException("AppId is required");
        }
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix is required");
        }

        return policyLookup.keySet().stream().filter(pG -> (pG.getAppId().equals(appId) && pG.getCluster().startsWith(prefix)))
                .map(Recorder.ProcessGroup::getCluster).collect(Collectors.toSet());
    }

    public Set<String> getProcNames(String appId, String clusterId, String prefix) {
        if (appId == null) {
            throw new IllegalArgumentException("AppId is required");
        }
        if (clusterId == null) {
            throw new IllegalArgumentException("ClusterId is required");
        }
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix is required");
        }
        return policyLookup.keySet().stream().filter(pG -> (pG.getAppId().equals(appId) && pG.getCluster().equals(clusterId) && pG.getProcName().startsWith(prefix)))
                .map(Recorder.ProcessGroup::getProcName).collect(Collectors.toSet());
    }

    @Override
    public PolicyDTO.VersionedPolicyDetails getVersionedPolicy(Recorder.ProcessGroup processGroup) {
        if (processGroup == null) {
            throw new IllegalArgumentException("Process group is required");
        }
        return policyLookup.get(processGroup);
    }

    @Override
    public Future<Void> createVersionedPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.VersionedPolicyDetails versionedPolicyDetails) {
        return putVersionedPolicy(processGroup, versionedPolicyDetails, true);
    }

    @Override
    public Future<Void> updateVersionedPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.VersionedPolicyDetails versionedPolicyDetails) {
        return putVersionedPolicy(processGroup, versionedPolicyDetails, false);
    }

    private Future<Void> putVersionedPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.VersionedPolicyDetails requestedVersionedPolicyDetails, boolean create) {
        if (processGroup == null) {
            throw new IllegalArgumentException("Process group is required");
        }
        if (requestedVersionedPolicyDetails == null) {
            throw new IllegalArgumentException("PolicyDetails is required");
        }
        Future<Void> future = Future.future();
        String zNodePath = policyPath + DELIMITER + VERSION + DELIMITER + EncodingUtil.encode32(processGroup.getAppId()) + DELIMITER + EncodingUtil.encode32(processGroup.getCluster()) + DELIMITER + EncodingUtil.encode32(processGroup.getProcName()) + DELIMITER + POLICY_NODE_PREFIX;

        vertx.executeBlocking(fut -> {
            try {
                policyLookup.compute(processGroup, (k, v) -> {
                    if (v != null && create) {
                        throw new PolicyException(String.format("Policy for ProcessGroup = %s already exists, policyDetails = %s", RecorderProtoUtil.processGroupCompactRepr(processGroup), PolicyProtoUtil.policyDetailsCompactRepr(getVersionedPolicy(processGroup).getPolicyDetails())), true);
                    }
                    if (v == null && !create) {
                        throw new PolicyException(String.format("Policy for ProcessGroup = %s does not exist", RecorderProtoUtil.processGroupCompactRepr(processGroup)), true);
                    }
                    if (v != null && v.getVersion() != requestedVersionedPolicyDetails.getVersion()) {
                        throw new PolicyException("Policy Version mismatch, currentVersion = " + v.getVersion() + ", your version = " + requestedVersionedPolicyDetails.getVersion() + ", for ProcessGroup = " + RecorderProtoUtil.processGroupCompactRepr(processGroup), true);
                    }
                    try {
                        String res = curatorClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT_SEQUENTIAL).
                                forPath(zNodePath, requestedVersionedPolicyDetails.getPolicyDetails().toByteArray());
                        res = ZKPaths.getNodeFromPath(res);
                        Integer newVersion = Integer.parseInt(res);
                        return requestedVersionedPolicyDetails.toBuilder().setVersion(newVersion).build();
                    } catch (Exception e) {
                        throw new PolicyException("Exception thrown by ZK while Writing policy for ProcessGroup = " + RecorderProtoUtil.processGroupCompactRepr(processGroup) + ", error = " + e.getMessage(), true);
                    }
                });
                fut.complete();
            } catch (Exception e) {
                fut.fail(e);
            }
        }, true, future.completer());
        return future;
    }
}
