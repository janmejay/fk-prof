package fk.prof.backend.model.policy;

import fk.prof.backend.proto.BackendDTO;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import recording.Recorder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: Liable for refactoring. Dummy impl for now
 */
public class PolicyStore {
  private static Logger logger = LoggerFactory.getLogger(PolicyStore.class);

  public static String policyStorePath = "/policy_store_temp";
  private final Map<Recorder.ProcessGroup, BackendDTO.RecordingPolicy> store = new ConcurrentHashMap<>();
  private final CuratorFramework curator;

  public PolicyStore(CuratorFramework curator) throws Exception {
    this.curator = curator;
    ensurePolicyStorePath();

    // populate all existing configs
    loadAllExistingPolicies();
  }

  public void put(Recorder.ProcessGroup processGroup, BackendDTO.RecordingPolicy recordingPolicy) {
    try {
      updateToZk(processGroup, recordingPolicy);
      this.store.put(processGroup, recordingPolicy);
    } catch (Exception e) {
      logger.error("Something went wrong while updating policy to zk", e);
    }
  }

  public BackendDTO.RecordingPolicy get(Recorder.ProcessGroup processGroup) {
    return this.store.get(processGroup);
  }

  private void updateToZk(Recorder.ProcessGroup processGroup, BackendDTO.RecordingPolicy recordingPolicy) throws Exception {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    Map<Recorder.ProcessGroup, BackendDTO.RecordingPolicy> tempStore = new HashMap<>();
    tempStore.putAll(store);
    tempStore.put(processGroup, recordingPolicy);

    for(Map.Entry<Recorder.ProcessGroup, BackendDTO.RecordingPolicy> entry : tempStore.entrySet()) {
      entry.getKey().writeDelimitedTo(bout);
      entry.getValue().writeDelimitedTo(bout);
    }

    byte[] data = bout.toByteArray();

    // write to zk
    curator.setData().forPath(policyStorePath, data);
  }

  private void loadAllExistingPolicies() throws Exception {
    byte[] data = curator.getData().forPath(policyStorePath);

    if(data == null || data.length == 0) {
      return;
    }

    ByteArrayInputStream bin = new ByteArrayInputStream(data);

    // read policies for all process groups
    while(bin.available() > 0) {
      Recorder.ProcessGroup pg = Recorder.ProcessGroup.parser().parseDelimitedFrom(bin);
      BackendDTO.RecordingPolicy policy = BackendDTO.RecordingPolicy.parser().parseDelimitedFrom(bin);

      store.put(pg, policy);
    }

    logger.info("Read " + store.size() + " policies from zk");
  }

  private void ensurePolicyStorePath() throws Exception{
    try {
      curator.create().forPath(policyStorePath, new byte[0]);
    } catch (KeeperException.NodeExistsException ex) {
      logger.warn(ex);
    }
  }
}
