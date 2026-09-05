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
    SecondaryStorageReport secondaryStorage) {

  public WorkloadReport {
    schemaVersion = requireNonBlank("schemaVersion", schemaVersion);
    generatedAt = requireNonBlank("generatedAt", generatedAt);
    detectedJobTypes = List.copyOf(detectedJobTypes);
    completedJobs = new LinkedHashMap<>(completedJobs);
    appliedWorkerOutputs = new LinkedHashMap<>(appliedWorkerOutputs);
    publishedMessages = new LinkedHashMap<>(publishedMessages);
  }

  public record RunSummary(
      long startedInstances, long completedInstances, long activeInstances, long createdIncidents) {}

  public record SecondaryStorageReport(boolean ingestionWaited, String type, String status) {
    public static SecondaryStorageReport skipped() {
      return new SecondaryStorageReport(false, null, "skipped");
    }
  }

  private static String requireNonBlank(final String name, final String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("%s must not be blank".formatted(name));
    }
    return value;
  }
}
