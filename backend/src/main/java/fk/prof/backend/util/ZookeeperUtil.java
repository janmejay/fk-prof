package fk.prof.backend.util;

import io.vertx.core.Future;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.List;

public class ZookeeperUtil {

    public static final String DELIMITER = "/";

  public static byte[] readZNode(CuratorFramework curatorClient, String zNodePath)
      throws Exception {
    return curatorClient.getData().forPath(zNodePath);
  }

  public static byte[] readLatestSeqZNodeChild(CuratorFramework curatorClient, String zNodePath) throws Exception {
      List<String> sortedPolicyNodes = ZKPaths.getSortedChildren(curatorClient.getZookeeperClient().getZooKeeper(), zNodePath);
      if (sortedPolicyNodes.isEmpty()) {
          return null;
      } else {
          zNodePath = zNodePath + DELIMITER + sortedPolicyNodes.get(sortedPolicyNodes.size() - 1);
      }
    return readZNode(curatorClient, zNodePath);
  }

    public static String getLatestSeqZNodeChildName(CuratorFramework curatorClient, String zNodePath) throws Exception {
        List<String> sortedPolicyNodes = ZKPaths.getSortedChildren(curatorClient.getZookeeperClient().getZooKeeper(), zNodePath);
        if (sortedPolicyNodes.isEmpty()) {
            return null;
        } else {
            zNodePath = zNodePath + DELIMITER + sortedPolicyNodes.get(sortedPolicyNodes.size() - 1);
        }
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

  public static Future<byte[]> readZNodeAsync(CuratorFramework curatorClient, String zNodePath)
      throws Exception {
    Future<byte[]> future = Future.future();
    curatorClient.getData().inBackground((client, event) -> {
      if (KeeperException.Code.OK.intValue() == event.getResultCode()) {
        future.complete(event.getData());
      } else {
        future.fail(new RuntimeException("Error reading association data from backend znode. result_code=" + event.getResultCode()));
      }
    }).forPath(zNodePath);
    return future;
  }

    /**
     * Write data to a ZK node path asynchronously
     * Note: This method creates parents if needed given create is true
     *
     * @param curatorClient curator client
     * @param zNodePath     path where data is to be stored
     * @param data          data to be stored
     * @param create        whether node is to be created or it exists already
     * @param mode          ZK Create mode to be used
     * @return a void future with exception if write fails
     * @throws Exception
     */
    public static Future<Void> writeZNodeAsync(CuratorFramework curatorClient, String zNodePath, byte[] data, boolean create, CreateMode mode)
            throws Exception {
        Future<Void> future = Future.future();
        if(create) {
            curatorClient.create().creatingParentsIfNeeded().withMode(mode).inBackground((client, event) -> {
                if (KeeperException.Code.OK.intValue() == event.getResultCode()) {
                    future.complete(null);
                } else {
                    future.fail(new RuntimeException("Error writing data to backend znode. result_code=" + event.getResultCode()));
                }
            }).forPath(zNodePath, data);
        } else {
            curatorClient.setData().forPath(zNodePath, data);
        }
        return future;
    }

}
