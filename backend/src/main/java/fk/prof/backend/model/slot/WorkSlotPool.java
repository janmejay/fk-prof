package fk.prof.backend.model.slot;

import fk.prof.backend.exception.WorkSlotException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * No operation of this class is thread-safe
 * Right now, only invoked from backend daemon thread, so we are good
 */
public class WorkSlotPool {
  private final int capacity;
  private int available;

  public WorkSlotPool(int capacity) {
    this.capacity = capacity;
    this.available = capacity;
  }

  public List<WorkSlot> acquire(int required) throws WorkSlotException {
    if(required < 0) {
      throw new WorkSlotException("Required slots should be a positive quantity, requested=" + required);
    }
    if(required > available) {
      throw new WorkSlotException("Not enough slots available, configured capacity=" + capacity + ", available=" + available, true);
    }
    List<WorkSlot> acquired = IntStream.range(0, required)
        .mapToObj(i -> new WorkSlot())
        .collect(Collectors.toList());
    available -= required;
    return acquired;
  }

  public int release(List<WorkSlot> slots) {
    if(slots == null) {
      return 0;
    }

    int releasedSlots = slots.stream().map(WorkSlot::release)
        .filter(Boolean::booleanValue).collect(Collectors.toList()).size();
    available += releasedSlots;
    return releasedSlots;
  }

  public static class WorkSlot {
    private boolean released = false;

    private WorkSlot() {}

    private boolean release() {
      if(released) {
        return false;
      }
      this.released = true;
      return true;
    }
  }
}
