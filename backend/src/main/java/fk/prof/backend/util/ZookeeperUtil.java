package fk.prof.backend.util;

import io.vertx.core.Future;
import javafx.util.Pair;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;

import java.util.List;

public class ZookeeperUtil {
    public static final String DELIMITER = "/";

  public static byte[] readZNode(CuratorFramework curatorClient, String zNodePath)
      throws Exception {
    return curatorClient.getData().forPath(zNodePath);
  }

    public static Pair<String, byte[]> readLatestSeqZNodeChild(CuratorFramework curatorClient, String zNodePath) throws Exception {
        List<String> sortedPolicyNodes = ZKPaths.getSortedChildren(curatorClient.getZookeeperClient().getZooKeeper(), zNodePath);
        if (sortedPolicyNodes.isEmpty()) {
            return null;
        }
        zNodePath = zNodePath + DELIMITER + sortedPolicyNodes.get(sortedPolicyNodes.size() - 1);
        return new Pair<>(ZKPaths.getNodeFromPath(zNodePath), readZNode(curatorClient, zNodePath));
  }

    public static String getLatestSeqZNodeChildName(CuratorFramework curatorClient, String zNodePath) throws Exception {
        List<String> sortedPolicyNodes = ZKPaths.getSortedChildren(curatorClient.getZookeeperClient().getZooKeeper(), zNodePath);
        if (sortedPolicyNodes.isEmpty()) {
            return null;
        }
        zNodePath = zNodePath + DELIMITER + sortedPolicyNodes.get(sortedPolicyNodes.size() - 1);
        return ZKPaths.getNodeFromPath(zNodePath);
    }

  public static void writeZNode(CuratorFramework curatorClient, String zNodePath, byte[] data, boolean create)
      throws Exception {
    if(create) {
      curatorClient.create().forPath(zNodePath, data);
    } else {
      curatorClient.setData().forPath(zNodePath, data);
    }
  }

  public static Future<Void> sync(CuratorFramework curatorClient, String zNodePath) {
    Future<Void> future = Future.future();
    try {
      curatorClient.sync().inBackground((client, event) -> {
        if (KeeperException.Code.OK.intValue() == event.getResultCode()) {
          future.complete();
        } else {
          future.fail(new RuntimeException("Error when zk sync issued for node path = " + zNodePath + " with result code = " + event.getResultCode()));
        }
      }).forPath(zNodePath);
    } catch (Exception ex) {
      future.fail(ex);
    }
    return future;
  }
}
