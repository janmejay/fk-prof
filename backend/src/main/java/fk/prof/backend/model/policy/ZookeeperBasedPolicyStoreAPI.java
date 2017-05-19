package fk.prof.backend.model.policy;

import com.google.common.io.BaseEncoding;
import fk.prof.backend.exception.PolicyException;
import fk.prof.backend.proto.PolicyDTO;
import fk.prof.backend.util.ZookeeperUtil;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import recording.Recorder;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Zookeeper based implementation of the policy store
 * Created by rohit.patiyal on 18/05/17.
 */
public class ZookeeperBasedPolicyStoreAPI implements PolicyStoreAPI {

    private static final String POLICY_NODE_PREFIX = "v";
    private static final String DELIMITER = "/";
    private final CuratorFramework curatorClient;
    private final Semaphore setPolicySemaphore = new Semaphore(1);
    private Logger logger = LoggerFactory.getLogger(ZookeeperBasedPolicyStoreAPI.class);
    private boolean initialized;
    private String policyPath;
    private InMemoryPolicyCache inMemoryPolicyCache = new InMemoryPolicyCache();

    ZookeeperBasedPolicyStoreAPI(CuratorFramework curatorClient, String policyPath) {
        if (curatorClient == null) {
            throw new IllegalStateException("Curator client is required");
        }
        if (policyPath == null) {
            throw new IllegalStateException("Backend association path in zookeeper hierarchy is required");
        }
        this.curatorClient = curatorClient;
        this.policyPath = policyPath;
        this.initialized = false;
    }

    private static String decode32(String str) {
        return new String(BaseEncoding.base32().decode(str), Charset.forName("utf-8"));
    }

    private static String encode(String str) {
        return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));

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
                    inMemoryPolicyCache.put(decode32(appId), decode32(clusterId), decode32(procName), policyDetails);
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

    Set<String> getAppIds(String prefix) {
        if (prefix == null) return new HashSet<>();
        Set<String> appIds = inMemoryPolicyCache.getAppId();
        return appIds.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());

    }

    Set<String> getClusterIds(String appId, String prefix) {
        if (prefix == null) return new HashSet<>();
        Set<String> clusterIds = inMemoryPolicyCache.getClusterIds(appId);
        return clusterIds.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
    }

    Set<String> getProcNames(String appId, String clusterId, String prefix) {
        if (prefix == null) return new HashSet<>();
        Set<String> procNames = inMemoryPolicyCache.getProcNames(appId, clusterId);
        return procNames.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
    }

    @Override
    public PolicyDTO.PolicyDetails getPolicy(Recorder.ProcessGroup processGroup) {
        if (processGroup == null) {
            logger.info("Process Group is null");
            return null;
        }
        return inMemoryPolicyCache.get(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
    }

    @Override
    public Future<Void> createPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails) {
        Future<Void> future = Future.future();
        if (processGroup == null || policyDetails == null) {
            future.fail("One of processGroup(" + processGroup + ") or policyDetails(" + policyDetails + ") is null");
            return future;
        }
        if (getPolicy(processGroup) != null) {
            future.fail("Policy already exists");
            return future;
        }
        setPolicy(future, processGroup, policyDetails);
        return future;
    }

    @Override
    public Future<Void> updatePolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails) {
        Future<Void> future = Future.future();
        if (processGroup == null || policyDetails == null) {
            future.fail("One of processGroup(" + processGroup + ") or policyDetails(" + policyDetails + ") is null");
            return future;
        }
        if (getPolicy(processGroup) == null) {
            future.fail("Policy does not exists ");
            return future;
        }
        if (getPolicy(processGroup).equals(policyDetails)) {
            future.fail("Nothing to change in policy");
            return future;
        }
        setPolicy(future, processGroup, policyDetails);
        return future;
    }

    private void setPolicy(Future<Void> future, Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails
            policyDetails) {
        String zNodePath = policyPath + DELIMITER + encode(processGroup.getAppId()) + DELIMITER + encode(processGroup.getCluster()) + DELIMITER + encode(processGroup.getProcName()) + DELIMITER + POLICY_NODE_PREFIX;
        boolean acquired;
        try {
            acquired = setPolicySemaphore.tryAcquire(1, TimeUnit.SECONDS);
            if (acquired) {
                ZookeeperUtil.writeZNodeAsync(curatorClient, zNodePath, policyDetails.toByteArray(), true, CreateMode.PERSISTENT_SEQUENTIAL).setHandler(ar -> {
                    if (ar.succeeded()) {
                        inMemoryPolicyCache.put(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName(), policyDetails);
                        setPolicySemaphore.release();
                        future.complete();
                    } else {
                        setPolicySemaphore.release();
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
    }

    /**
     * In memory replica of the ZK Policy store
     * Created by rohit.patiyal on 15/05/17.
     */
    class InMemoryPolicyCache {
        private final Map<String, Map<String, Map<String, PolicyDTO.PolicyDetails>>> processGroupAsTreeToPolicyLookup = new ConcurrentHashMap<>();
        private Logger logger = LoggerFactory.getLogger(InMemoryPolicyCache.class);

        public PolicyDTO.PolicyDetails get(String appId, String clusterId, String procName) {
            PolicyDTO.PolicyDetails policyDetails = null;
            try {
                policyDetails = processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).get(procName);
            } catch (Exception ex) {
                logger.error("No policy found for ProcessGroup : " + appId + " " + clusterId + " " + procName + " in InMemoryPolicyCache");
            }
            return policyDetails;
        }

        public void put(String appId, String clusterId, String procName, PolicyDTO.PolicyDetails policyDetails) {
            processGroupAsTreeToPolicyLookup.putIfAbsent(appId, new ConcurrentHashMap<>());
            processGroupAsTreeToPolicyLookup.get(appId).putIfAbsent(clusterId, new ConcurrentHashMap<>());
            processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).put(procName, policyDetails);
        }

        Set<String> getAppId() {
            return processGroupAsTreeToPolicyLookup.keySet();
        }

        Set<String> getClusterIds(String appId) {
            if (processGroupAsTreeToPolicyLookup.containsKey(appId)) {
                return processGroupAsTreeToPolicyLookup.get(appId).keySet();
            }
            return new HashSet<>();
        }

        Set<String> getProcNames(String appId, String clusterId) {
            if (processGroupAsTreeToPolicyLookup.containsKey(appId)) {
                if (processGroupAsTreeToPolicyLookup.get(appId).containsKey(clusterId)) {
                    return processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).keySet();
                }
            }
            return new HashSet<>();
        }
    }
}
