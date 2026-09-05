package io.zell.cwg.bpmn;

import java.nio.file.Path;
import java.util.List;

public record BpmnAnalysis(
    Path source,
    List<String> processIds,
    List<StaticJobType> staticJobTypes,
    List<CallActivity> callActivities,
    List<MessageReference> messageReferences,
    List<DmnReference> dmnReferences) {

  public BpmnAnalysis {
    processIds = List.copyOf(processIds);
    staticJobTypes = List.copyOf(staticJobTypes);
    callActivities = List.copyOf(callActivities);
    messageReferences = List.copyOf(messageReferences);
    dmnReferences = List.copyOf(dmnReferences);
  }

  public record StaticJobType(String elementId, String elementName, String type) {}

  public record CallActivity(String elementId, String elementName, String calledProcessId) {}

  public record MessageReference(String elementId, String messageRef) {}

  public record DmnReference(String elementId, String decisionId) {}
}
