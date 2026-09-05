/*
 * Copyright 2026 camunda-workload-generator contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kujava.cwg.resources;

import io.kujava.cwg.bpmn.BpmnAnalysis;
import io.kujava.cwg.bpmn.BpmnAnalysis.CallActivity;
import io.kujava.cwg.bpmn.BpmnAnalysis.DmnReference;
import io.kujava.cwg.bpmn.BpmnAnalysis.MessageReference;
import io.kujava.cwg.bpmn.BpmnAnalysis.StaticJobType;
import io.kujava.cwg.bpmn.BpmnAnalysis.UserTask;
import java.util.List;

public record WorkloadResourceAnalysis(ResourceScanResult scan, List<BpmnAnalysis> bpmnAnalyses) {

  public WorkloadResourceAnalysis {
    bpmnAnalyses = List.copyOf(bpmnAnalyses);
  }

  public List<String> processIds() {
    return bpmnAnalyses.stream()
        .flatMap(analysis -> analysis.processIds().stream())
        .distinct()
        .toList();
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
    return bpmnAnalyses.stream()
        .flatMap(analysis -> analysis.messageReferences().stream())
        .toList();
  }

  public List<DmnReference> dmnReferences() {
    return bpmnAnalyses.stream().flatMap(analysis -> analysis.dmnReferences().stream()).toList();
  }
}
