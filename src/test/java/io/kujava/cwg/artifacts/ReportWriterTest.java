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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kujava.cwg.artifacts.WorkloadReport.RunSummary;
import io.kujava.cwg.artifacts.WorkloadReport.SecondaryStorageReport;
import io.kujava.cwg.artifacts.WorkloadReport.ZeebeDataReport;
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
            Map.of("payment-received", 4L),
            Map.of("approve_invoice", 4L),
            new ZeebeDataReport("zeebe-data/", "zeebe-data.zip", 3, 128),
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
    assertThat(json.get("publishedMessages").get("payment-received").asLong()).isEqualTo(4);
    assertThat(json.get("completedUserTasks").get("approve_invoice").asLong()).isEqualTo(4);
    assertThat(json.get("zeebeData").get("directory").asText()).isEqualTo("zeebe-data/");
    assertThat(json.get("zeebeData").get("zip").asText()).isEqualTo("zeebe-data.zip");
    assertThat(json.get("zeebeData").get("files").asLong()).isEqualTo(3);
    assertThat(json.get("zeebeData").get("bytes").asLong()).isEqualTo(128);
    assertThat(json.get("secondaryStorage").get("status").asText()).isEqualTo("skipped");
  }
}
