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
package io.kujava.cwg.artifacts;

import io.kujava.cwg.bpmn.BpmnAnalysis;
import io.kujava.cwg.config.WorkloadConfig;
import io.kujava.cwg.resources.ResourceFile;
import io.kujava.cwg.resources.WorkloadResourceAnalysis;
import java.util.List;

public record WorkloadManifest(
    String schemaVersion,
    String generatedAt,
    RuntimeMetadata runtime,
    WorkloadMetadata workload,
    ResourceMetadata resources,
    ArtifactPaths artifacts) {

  public WorkloadManifest {
    schemaVersion = requireNonBlank("schemaVersion", schemaVersion);
    generatedAt = requireNonBlank("generatedAt", generatedAt);
  }

  public static WorkloadManifest from(
      final WorkloadConfig config,
      final WorkloadResourceAnalysis resourceAnalysis,
      final String generatedAt) {
    return from(config, resourceAnalysis, generatedAt, ArtifactPaths.defaults());
  }

  public static WorkloadManifest from(
      final WorkloadConfig config,
      final WorkloadResourceAnalysis resourceAnalysis,
      final String generatedAt,
      final ArtifactPaths artifacts) {
    return new WorkloadManifest(
        "1",
        generatedAt,
        new RuntimeMetadata(config.getRuntime().image()),
        new WorkloadMetadata(
            config.getResources().rootProcessId(),
            config.getWorkload().startInstances(),
            config.getWorkload().completeInstances()),
        ResourceMetadata.from(config, resourceAnalysis),
        artifacts);
  }

  public record RuntimeMetadata(String image) {}

  public record WorkloadMetadata(String rootProcessId, int startInstances, int completeInstances) {}

  public record ResourceMetadata(
      String directory,
      String payload,
      List<ResourceEntry> deployableResources,
      List<ResourceEntry> payloadOrConfigResources,
      List<String> processIds,
      List<JobTypeEntry> staticJobTypes,
      List<UserTaskEntry> userTasks,
      List<CallActivityEntry> callActivities,
      List<MessageReferenceEntry> messageReferences,
      List<DmnReferenceEntry> dmnReferences) {

    public ResourceMetadata {
      deployableResources = List.copyOf(deployableResources);
      payloadOrConfigResources = List.copyOf(payloadOrConfigResources);
      processIds = List.copyOf(processIds);
      staticJobTypes = List.copyOf(staticJobTypes);
      userTasks = List.copyOf(userTasks);
      callActivities = List.copyOf(callActivities);
      messageReferences = List.copyOf(messageReferences);
      dmnReferences = List.copyOf(dmnReferences);
    }

    static ResourceMetadata from(
        final WorkloadConfig config, final WorkloadResourceAnalysis resourceAnalysis) {
      return new ResourceMetadata(
          config.getResources().directory(),
          normalized(config.getResources().payload()),
          resourceAnalysis.scan().deployableResources().stream().map(ResourceEntry::from).toList(),
          resourceAnalysis.scan().payloadOrConfigResources().stream()
              .map(ResourceEntry::from)
              .toList(),
          resourceAnalysis.processIds(),
          resourceAnalysis.staticJobTypes().stream().map(JobTypeEntry::from).toList(),
          resourceAnalysis.userTasks().stream().map(UserTaskEntry::from).toList(),
          resourceAnalysis.callActivities().stream().map(CallActivityEntry::from).toList(),
          resourceAnalysis.messageReferences().stream().map(MessageReferenceEntry::from).toList(),
          resourceAnalysis.dmnReferences().stream().map(DmnReferenceEntry::from).toList());
    }
  }

  public record ResourceEntry(String type, String path) {
    static ResourceEntry from(final ResourceFile resource) {
      return new ResourceEntry(
          resource.type().name(), resource.relativePath().toString().replace('\\', '/'));
    }
  }

  public record JobTypeEntry(String elementId, String elementName, String type) {
    static JobTypeEntry from(final BpmnAnalysis.StaticJobType jobType) {
      return new JobTypeEntry(jobType.elementId(), jobType.elementName(), jobType.type());
    }
  }

  public record UserTaskEntry(String elementId, String elementName) {
    static UserTaskEntry from(final BpmnAnalysis.UserTask userTask) {
      return new UserTaskEntry(userTask.elementId(), userTask.elementName());
    }
  }

  public record CallActivityEntry(String elementId, String elementName, String calledProcessId) {
    static CallActivityEntry from(final BpmnAnalysis.CallActivity callActivity) {
      return new CallActivityEntry(
          callActivity.elementId(), callActivity.elementName(), callActivity.calledProcessId());
    }
  }

  public record MessageReferenceEntry(String elementId, String messageRef) {
    static MessageReferenceEntry from(final BpmnAnalysis.MessageReference messageReference) {
      return new MessageReferenceEntry(messageReference.elementId(), messageReference.messageRef());
    }
  }

  public record DmnReferenceEntry(String elementId, String decisionId) {
    static DmnReferenceEntry from(final BpmnAnalysis.DmnReference dmnReference) {
      return new DmnReferenceEntry(dmnReference.elementId(), dmnReference.decisionId());
    }
  }

  public record ArtifactPaths(
      String zeebeData,
      String zeebeDataZip,
      String manifest,
      String report,
      String secondaryStorage) {
    static ArtifactPaths defaults() {
      return new ArtifactPaths("zeebe-data/", null, "manifest.json", "report.json", null);
    }

    public static ArtifactPaths from(final ZeebeDataArtifacts zeebeDataArtifacts) {
      return new ArtifactPaths(
          zeebeDataArtifacts.directory(),
          zeebeDataArtifacts.zip(),
          "manifest.json",
          "report.json",
          null);
    }
  }

  private static String requireNonBlank(final String name, final String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("%s must not be blank".formatted(name));
    }
    return value;
  }

  private static String normalized(final String path) {
    return path == null
        ? null
        : java.nio.file.Path.of(path).normalize().toString().replace('\\', '/');
  }
}
