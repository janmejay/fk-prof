package fk.prof.backend.util;

import io.vertx.core.Future;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

public class ZookeeperUtil {

  public static byte[] readZNode(CuratorFramework curatorClient, String zNodePath)
      throws Exception {
    return curatorClient.getData().forPath(zNodePath);
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

  //TODO: Keeping this around in case required for policy CRUD. If not used there, remove
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

  //TODO: Keeping this around in case required for policy CRUD. If not used there, remove
  public static Future<Void> writeZNodeAsync(CuratorFramework curatorClient, String zNodePath, byte[] data, boolean create)
      throws Exception {
    Future<Void> future = Future.future();
    if(create) {
      curatorClient.create().inBackground((client, event) -> {
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
