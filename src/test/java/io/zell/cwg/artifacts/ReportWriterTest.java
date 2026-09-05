package io.zell.cwg.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zell.cwg.artifacts.WorkloadReport.RunSummary;
import io.zell.cwg.artifacts.WorkloadReport.SecondaryStorageReport;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReportWriterTest {

  @TempDir private Path tempDir;

  @Test
  void shouldWriteReportJsonWithoutStartingRuntime() throws Exception {
    // given
    final var completedJobs = new LinkedHashMap<String, Long>();
    completedJobs.put("charge-card", 4L);
    final var report =
        new WorkloadReport(
            "1",
            "2026-09-05T05:30:00Z",
            new RunSummary(10, 4, 6, 0),
            List.of("charge-card"),
            completedJobs,
            Map.of("charge-card", 4L),
            SecondaryStorageReport.skipped());

    // when
    final var reportFile = new ReportWriter().write(tempDir, report);

    // then
    final JsonNode json = new ObjectMapper().readTree(reportFile.toFile());
    assertThat(json.get("schemaVersion").asText()).isEqualTo("1");
    assertThat(json.get("workload").get("startedInstances").asLong()).isEqualTo(10);
    assertThat(json.get("workload").get("completedInstances").asLong()).isEqualTo(4);
    assertThat(json.get("detectedJobTypes").get(0).asText()).isEqualTo("charge-card");
    assertThat(json.get("completedJobs").get("charge-card").asLong()).isEqualTo(4);
    assertThat(json.get("appliedWorkerOutputs").get("charge-card").asLong()).isEqualTo(4);
    assertThat(json.get("secondaryStorage").get("status").asText()).isEqualTo("skipped");
  }
}
