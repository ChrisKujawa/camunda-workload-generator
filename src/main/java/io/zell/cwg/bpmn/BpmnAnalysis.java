package io.zell.cwg.bpmn;

import java.nio.file.Path;
import java.util.List;

public record BpmnAnalysis(
    Path source,
    List<String> processIds,
    List<ProcessPath> processPaths,
    List<StaticJobType> staticJobTypes,
    List<CallActivity> callActivities,
    List<MessageReference> messageReferences,
    List<DmnReference> dmnReferences) {

  public BpmnAnalysis {
    processIds = List.copyOf(processIds);
    processPaths = List.copyOf(processPaths);
    staticJobTypes = List.copyOf(staticJobTypes);
    callActivities = List.copyOf(callActivities);
    messageReferences = List.copyOf(messageReferences);
    dmnReferences = List.copyOf(dmnReferences);
  }

  public record ProcessPath(String processId, List<HappyPathNode> happyPath, boolean completePath) {

    public ProcessPath {
      happyPath = List.copyOf(happyPath);
    }

    public int flowNodeInstances() {
      return happyPath.size();
    }
  }

  public record HappyPathNode(
      String elementId, String elementName, String elementType, String jobType) {}

  public record StaticJobType(String elementId, String elementName, String type, String processId) {}

  public record CallActivity(
      String elementId, String elementName, String calledProcessId, String processId) {}

  public record MessageReference(String elementId, String messageRef, String processId) {}

  public record DmnReference(String elementId, String decisionId, String processId) {}
}
