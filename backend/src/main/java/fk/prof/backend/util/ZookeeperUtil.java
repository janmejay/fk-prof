package fk.prof.backend.util;

import io.vertx.core.Future;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.List;

public class ZookeeperUtil {

  private static final String DELIMITER = "/";

  public static byte[] readZNode(CuratorFramework curatorClient, String zNodePath)
      throws Exception {
    return curatorClient.getData().forPath(zNodePath);
  }

  public static byte[] readLatestSeqZNodeChild(CuratorFramework curatorClient, String zNodePath) throws Exception {
    List<String> sequentialPolicies = ZKPaths.getSortedChildren(curatorClient.getZookeeperClient().getZooKeeper(), zNodePath);
    zNodePath = zNodePath + DELIMITER + sequentialPolicies.get(sequentialPolicies.size() - 1);
    return readZNode(curatorClient, zNodePath);
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

  public static Future<Void> writeZNodeAsync(CuratorFramework curatorClient, String zNodePath, byte[] data, boolean create, CreateMode mode)
      throws Exception {
    Future<Void> future = Future.future();
    if(create) {
      curatorClient.create().creatingParentsIfNeeded().withMode(mode).inBackground((client, event) -> {
        if (KeeperException.Code.OK.intValue() == event.getResultCode()) {
          future.complete(null);
        } else {
          future.fail(new RuntimeException("Error writing association data to backend znode. result_code=" + event.getResultCode()));
        }
      }).forPath(zNodePath, data);
    } else {
      curatorClient.setData().forPath(zNodePath, data);
    }
    return future;
  }

}
