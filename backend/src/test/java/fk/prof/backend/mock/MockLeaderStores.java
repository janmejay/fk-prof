package fk.prof.backend.mock;

import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.model.election.LeaderWriteContext;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;

import java.util.concurrent.CountDownLatch;

public class MockLeaderStores {

  public static class TestLeaderStore implements LeaderReadContext, LeaderWriteContext {
    private String address = null;
    private boolean self = false;
    private final CountDownLatch latch;
    private final String ipAddress;

    public TestLeaderStore(String ipAddress, CountDownLatch latch) {
      this.ipAddress = ipAddress;
      this.latch = latch;
    }

    @Override
    public void setLeaderIPAddress(String ipAddress) {
      address = ipAddress;
      self = ipAddress != null && ipAddress.equals(ipAddress);
      if (address != null) {
        latch.countDown();
      }
    }

    @Override
    public String getLeaderIPAddress() {
      return address;
    }

    @Override
    public boolean isLeader() {
      return self;
    }
  }


  public static class WrappedLeaderStore implements LeaderReadContext, LeaderWriteContext {
    private final InMemoryLeaderStore toWrap;
    private final CountDownLatch latch;

    public WrappedLeaderStore(InMemoryLeaderStore toWrap, CountDownLatch latch) {
      this.toWrap = toWrap;
      this.latch = latch;
    }

    @Override
    public void setLeaderIPAddress(String ipAddress) {
      toWrap.setLeaderIPAddress(ipAddress);
      if (ipAddress != null) {
        latch.countDown();
      }
    }

    @Override
    public String getLeaderIPAddress() {
      return toWrap.getLeaderIPAddress();
    }

    @Override
    public boolean isLeader() {
      return toWrap.isLeader();
    }

  }

}
