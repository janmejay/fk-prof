package fk.prof.backend.model.assignment.impl;

import fk.prof.backend.model.assignment.*;
import recording.Recorder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class AssociatedProcessGroupsImpl implements AssociatedProcessGroups, ProcessGroupDiscoveryContext {
  private final Map<Recorder.ProcessGroup, ProcessGroupDetail> processGroupLookup = new ConcurrentHashMap<>();
  private final int thresholdForDefunctRecorderInSecs;

  public AssociatedProcessGroupsImpl(int thresholdForDefunctRecorderInSecs) {
    this.thresholdForDefunctRecorderInSecs = thresholdForDefunctRecorderInSecs;
  }


  //Called by backend daemon thread when load is reported to leader and leader responds with associations
  @Override
  public void updateProcessGroupAssociations(Recorder.ProcessGroups processGroups, BiConsumer<ProcessGroupContextForScheduling, ProcessGroupAssociationResult> postUpdateAction) {
    //Remove process group associations which are not returned by leader
    for(Recorder.ProcessGroup processGroup: this.processGroupLookup.keySet()) {
      if(!processGroups.getProcessGroupList().contains(processGroup)) {
        ProcessGroupDetail processGroupDetail = this.processGroupLookup.remove(processGroup);
        postUpdateAction.accept(processGroupDetail, ProcessGroupAssociationResult.REMOVED);
      }
    }

    //Add process group associations which are returned by leader
    for(Recorder.ProcessGroup processGroup: processGroups.getProcessGroupList()) {
      ProcessGroupDetail existingValue = this.processGroupLookup.putIfAbsent(processGroup, new ProcessGroupDetail(processGroup, thresholdForDefunctRecorderInSecs));
      if(existingValue == null) {
        postUpdateAction.accept(this.processGroupLookup.get(processGroup), ProcessGroupAssociationResult.ADDED);
      }
    }
  }

  @Override
  public ProcessGroupContextForPolling getProcessGroupContextForPolling(Recorder.ProcessGroup processGroup) {
    return this.processGroupLookup.get(processGroup);
  }
}
