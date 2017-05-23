package fk.prof.backend.model.policy;

import fk.prof.backend.exception.PolicyException;
import fk.prof.backend.proto.PolicyDTO;
import fk.prof.backend.util.EncodingUtil;
import fk.prof.backend.util.ZookeeperUtil;
import fk.prof.backend.util.proto.PolicyProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import recording.Recorder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static fk.prof.backend.util.ZookeeperUtil.DELIMITER;

/**
 * Zookeeper based implementation of the policy store
 * Created by rohit.patiyal on 18/05/17.
 */
public class ZookeeperBasedPolicyStoreAPI implements PolicyStoreAPI {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperBasedPolicyStoreAPI.class);
    private static final String POLICY_NODE_PREFIX = "v";
    private final CuratorFramework curatorClient;
    private final Semaphore setPolicySemaphore = new Semaphore(1);
    private final String policyPath;
    private boolean initialized;
    private InMemoryPolicyCache policyCache = new InMemoryPolicyCache();

    ZookeeperBasedPolicyStoreAPI(CuratorFramework curatorClient, String policyPath) {
        if (curatorClient == null) {
            throw new IllegalArgumentException("Curator client is required");
        }
        if (policyPath == null) {
            throw new IllegalArgumentException("Policy path in zookeeper hierarchy is required");
        }
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
        for (String appId : curatorClient.getChildren().forPath(policyPath)) {
            for (String clusterId : curatorClient.getChildren().forPath(policyPath + DELIMITER + appId)) {
                for (String procName : curatorClient.getChildren().forPath(policyPath + DELIMITER + appId + DELIMITER + clusterId)) {
                    String zNodePath = policyPath + DELIMITER + appId + DELIMITER + clusterId + DELIMITER + procName;
                    PolicyDTO.PolicyDetails policyDetails = PolicyDTO.PolicyDetails.parseFrom(ZookeeperUtil.readLatestSeqZNodeChild(curatorClient, zNodePath));
                    policyCache.put(EncodingUtil.decode32(appId), EncodingUtil.decode32(clusterId), EncodingUtil.decode32(procName), policyDetails);
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
        Set<String> appIds = policyCache.getAppIds();
        return appIds.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());

    }

    public Set<String> getClusterIds(String appId, String prefix) {
        if (appId == null) {
            throw new IllegalArgumentException("AppId is required");
        }
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix is required");
        }

        Set<String> clusterIds = policyCache.getClusterIds(appId);
        return clusterIds.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
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

        Set<String> procNames = policyCache.getProcNames(appId, clusterId);
        return procNames.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
    }

    @Override
    public PolicyDTO.PolicyDetails getPolicy(Recorder.ProcessGroup processGroup) {
        if (processGroup == null) {
            throw new IllegalArgumentException("Process group is required");
        }
        return policyCache.get(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
    }

    @Override
    public Future<Void> createPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails) {
        if (processGroup == null) {
            throw new IllegalArgumentException("Process group is required");
        }
        if (policyDetails == null) {
            throw new IllegalArgumentException("PolicyDetails is required");
        }
        if (getPolicy(processGroup) != null) {
            return Future.failedFuture(String.format("Policy for ProcessGroup = %s already exists, policyDetails = %s", RecorderProtoUtil.processGroupCompactRepr(processGroup), PolicyProtoUtil.policyDetailsCompactRepr(getPolicy(processGroup))));
        }
        return setPolicy(processGroup, policyDetails);
    }

    @Override
    public Future<Void> updatePolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails) {
        if (processGroup == null) {
            throw new IllegalArgumentException("Process group is required");
        }
        if (policyDetails == null) {
            throw new IllegalArgumentException("PolicyDetails is required");
        }
        if (getPolicy(processGroup) == null) {
            return Future.failedFuture(String.format("Policy for ProcessGroup = %s does not exist", RecorderProtoUtil.processGroupCompactRepr(processGroup)));
        }
        if (getPolicy(processGroup).equals(policyDetails)) {
            return Future.failedFuture(String.format("Nothing to change in policy for ProcessGroup = %s", RecorderProtoUtil.processGroupCompactRepr(processGroup)));
        }
        return setPolicy(processGroup, policyDetails);
    }

    private Future<Void> setPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails
            policyDetails) {
        Future<Void> future = Future.future();
        String zNodePath = policyPath + DELIMITER + EncodingUtil.encode32(processGroup.getAppId()) + DELIMITER + EncodingUtil.encode32(processGroup.getCluster()) + DELIMITER + EncodingUtil.encode32(processGroup.getProcName()) + DELIMITER + POLICY_NODE_PREFIX;
        boolean acquired;
        try {
            acquired = setPolicySemaphore.tryAcquire(1, TimeUnit.SECONDS);
            if (acquired) {
                ZookeeperUtil.writeZNodeAsync(curatorClient, zNodePath, policyDetails.toByteArray(), true, CreateMode.PERSISTENT_SEQUENTIAL).setHandler(ar -> {
                    try {
                        if (ar.succeeded()) {
                            policyCache.put(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName(), policyDetails);
                        }
                    } finally {
                        setPolicySemaphore.release();
                    }
                    if (ar.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(ar.cause());
                    }
                });
            } else {
                future.fail(new PolicyException("Timeout while acquiring lock while setting policy for process_group=" + processGroup, true));
            }
        } catch (InterruptedException e) {
            future.fail(new PolicyException("Interrupted while acquiring lock for setting policy for process_group=" + processGroup, true));
        } catch (Exception e) {
            future.fail(new PolicyException("Unexpected error while setting policy for process_group=" + processGroup, true));
        }

        return future;
    }

    /**
     * In memory replica of the ZK Policy store
     * Created by rohit.patiyal on 15/05/17.
     */
    private static class InMemoryPolicyCache {
        private final static Logger logger = LoggerFactory.getLogger(InMemoryPolicyCache.class);
        private final Map<String, Map<String, Map<String, PolicyDTO.PolicyDetails>>> processGroupAsTreeToPolicyLookup = new ConcurrentHashMap<>();

        public PolicyDTO.PolicyDetails get(String appId, String clusterId, String procName) {
            PolicyDTO.PolicyDetails policyDetails = null;
            try {
                policyDetails = processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).get(procName);
            } catch (Exception ex) {
                logger.error("No policy found for ProcessGroup : " + appId + " " + clusterId + " " + procName);
            }
            return policyDetails;
        }

        public void put(String appId, String clusterId, String procName, PolicyDTO.PolicyDetails policyDetails) {
            processGroupAsTreeToPolicyLookup.putIfAbsent(appId, new ConcurrentHashMap<>());
            processGroupAsTreeToPolicyLookup.get(appId).putIfAbsent(clusterId, new ConcurrentHashMap<>());
            processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).put(procName, policyDetails);
        }

        private Set<String> getAppIds() {
            return processGroupAsTreeToPolicyLookup.keySet();
        }

        private Set<String> getClusterIds(String appId) {
            if (processGroupAsTreeToPolicyLookup.containsKey(appId)) {
                return processGroupAsTreeToPolicyLookup.get(appId).keySet();
            }
            return new HashSet<>();
        }

        private Set<String> getProcNames(String appId, String clusterId) {
            if (processGroupAsTreeToPolicyLookup.containsKey(appId)) {
                if (processGroupAsTreeToPolicyLookup.get(appId).containsKey(clusterId)) {
                    return processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).keySet();
                }
            }
            return new HashSet<>();
        }
    }
}
