package io.zell.cwg.artifacts;

import io.zell.cwg.bpmn.BpmnAnalysis;
import io.zell.cwg.config.WorkloadConfig;
import io.zell.cwg.resources.ResourceFile;
import io.zell.cwg.resources.WorkloadResourceAnalysis;
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
    return new WorkloadManifest(
        "1",
        generatedAt,
        new RuntimeMetadata(config.getRuntime().image()),
        new WorkloadMetadata(
            config.getResources().rootProcessId(),
            config.getWorkload().startInstances(),
            config.getWorkload().completeInstances()),
        ResourceMetadata.from(config, resourceAnalysis),
        ArtifactPaths.defaults());
  }

  public record RuntimeMetadata(String image) {}

  public record WorkloadMetadata(String rootProcessId, int startInstances, int completeInstances) {}

  public record ResourceMetadata(
      String directory,
      List<ResourceEntry> deployableResources,
      List<ResourceEntry> payloadOrConfigResources,
      List<String> processIds,
      List<JobTypeEntry> staticJobTypes,
      List<CallActivityEntry> callActivities,
      List<MessageReferenceEntry> messageReferences,
      List<DmnReferenceEntry> dmnReferences) {

    public ResourceMetadata {
      deployableResources = List.copyOf(deployableResources);
      payloadOrConfigResources = List.copyOf(payloadOrConfigResources);
      processIds = List.copyOf(processIds);
      staticJobTypes = List.copyOf(staticJobTypes);
      callActivities = List.copyOf(callActivities);
      messageReferences = List.copyOf(messageReferences);
      dmnReferences = List.copyOf(dmnReferences);
    }

    static ResourceMetadata from(
        final WorkloadConfig config, final WorkloadResourceAnalysis resourceAnalysis) {
      return new ResourceMetadata(
          config.getResources().directory(),
          resourceAnalysis.scan().deployableResources().stream().map(ResourceEntry::from).toList(),
          resourceAnalysis.scan().payloadOrConfigResources().stream().map(ResourceEntry::from).toList(),
          resourceAnalysis.processIds(),
          resourceAnalysis.staticJobTypes().stream().map(JobTypeEntry::from).toList(),
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

  public record ArtifactPaths(String zeebeData, String manifest, String report, String secondaryStorage) {
    static ArtifactPaths defaults() {
      return new ArtifactPaths("zeebe-data/", "manifest.json", "report.json", null);
    }
  }

  private static String requireNonBlank(final String name, final String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("%s must not be blank".formatted(name));
    }
    return value;
  }
}
