package fk.prof.backend.model.assignment;

import recording.Recorder;

import java.util.function.BiConsumer;

public interface AssociatedProcessGroups extends ProcessGroupDiscoveryContext {
  void updateProcessGroupAssociations(Recorder.ProcessGroups processGroups, BiConsumer<ProcessGroupContextForScheduling, ProcessGroupAssociationResult> postUpdateAction);
}
