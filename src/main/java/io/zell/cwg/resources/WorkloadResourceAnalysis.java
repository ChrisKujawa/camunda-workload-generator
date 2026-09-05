package io.zell.cwg.resources;

import io.zell.cwg.bpmn.BpmnAnalysis;
import io.zell.cwg.bpmn.BpmnAnalysis.CallActivity;
import io.zell.cwg.bpmn.BpmnAnalysis.DmnReference;
import io.zell.cwg.bpmn.BpmnAnalysis.MessageReference;
import io.zell.cwg.bpmn.BpmnAnalysis.StaticJobType;
import io.zell.cwg.bpmn.BpmnAnalysis.UserTask;
import java.util.List;

public record WorkloadResourceAnalysis(ResourceScanResult scan, List<BpmnAnalysis> bpmnAnalyses) {

  public WorkloadResourceAnalysis {
    bpmnAnalyses = List.copyOf(bpmnAnalyses);
  }

  public List<String> processIds() {
    return bpmnAnalyses.stream().flatMap(analysis -> analysis.processIds().stream()).distinct().toList();
  }

  public List<StaticJobType> staticJobTypes() {
    return bpmnAnalyses.stream().flatMap(analysis -> analysis.staticJobTypes().stream()).toList();
  }

  public List<UserTask> userTasks() {
    return bpmnAnalyses.stream().flatMap(analysis -> analysis.userTasks().stream()).toList();
  }

  public List<CallActivity> callActivities() {
    return bpmnAnalyses.stream().flatMap(analysis -> analysis.callActivities().stream()).toList();
  }

  public List<MessageReference> messageReferences() {
    return bpmnAnalyses.stream().flatMap(analysis -> analysis.messageReferences().stream()).toList();
  }

  public List<DmnReference> dmnReferences() {
    return bpmnAnalyses.stream().flatMap(analysis -> analysis.dmnReferences().stream()).toList();
  }
}
