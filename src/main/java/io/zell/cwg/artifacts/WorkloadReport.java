package io.zell.cwg.artifacts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WorkloadReport(
    String schemaVersion,
    String generatedAt,
    RunSummary workload,
    List<String> detectedJobTypes,
    Map<String, Long> completedJobs,
    Map<String, Long> appliedWorkerOutputs,
    Map<String, Long> publishedMessages,
    Map<String, Long> completedUserTasks,
    ZeebeDataReport zeebeData,
    SecondaryStorageReport secondaryStorage) {

  public WorkloadReport {
    schemaVersion = requireNonBlank("schemaVersion", schemaVersion);
    generatedAt = requireNonBlank("generatedAt", generatedAt);
    detectedJobTypes = List.copyOf(detectedJobTypes);
    completedJobs = new LinkedHashMap<>(completedJobs);
    appliedWorkerOutputs = new LinkedHashMap<>(appliedWorkerOutputs);
    publishedMessages = new LinkedHashMap<>(publishedMessages);
    completedUserTasks = new LinkedHashMap<>(completedUserTasks);
  }

  public record RunSummary(
      long startedInstances, long completedInstances, long activeInstances, long createdIncidents) {}

  public record SecondaryStorageReport(
      boolean ingestionWaited,
      String type,
      String status,
      String mode,
      String endpoint,
      long indexes,
      long documents,
      long storeSizeBytes) {
    public static SecondaryStorageReport skipped() {
      return new SecondaryStorageReport(false, null, "skipped", "disabled", null, 0, 0, 0);
    }
  }

  public record ZeebeDataReport(String directory, String zip, long files, long bytes) {
    public static ZeebeDataReport from(final ZeebeDataArtifacts artifacts) {
      return new ZeebeDataReport(
          artifacts.directory(), artifacts.zip(), artifacts.files(), artifacts.bytes());
    }
  }

  private static String requireNonBlank(final String name, final String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("%s must not be blank".formatted(name));
    }
    return value;
  }
}
