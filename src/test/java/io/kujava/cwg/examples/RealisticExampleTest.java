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
package io.kujava.cwg.examples;

import static org.assertj.core.api.Assertions.assertThat;

import io.kujava.cwg.config.ConfigLoader;
import io.kujava.cwg.config.ConfigOverrides;
import io.kujava.cwg.resources.WorkloadResourceAnalyzer;
import io.kujava.cwg.workload.PayloadVariablesLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RealisticExampleTest {

  private static final Path CONFIG = Path.of("examples/realistic/workload.yaml");

  @Test
  void shouldLoadAndAnalyzeRealisticExampleResources() throws Exception {
    // given
    final var config = ConfigLoader.load(CONFIG, ConfigOverrides.none());

    // when
    final var analysis =
        new WorkloadResourceAnalyzer().analyze(Path.of(config.getResources().directory()));
    final var payload = new PayloadVariablesLoader().load(config);

    // then
    assertThat(config.getResources().rootProcessId()).isEqualTo("bankDisputeHandling");
    assertThat(config.getWorkload().completeInstances()).isEqualTo(1);
    assertThat(config.getResources().payload()).isEqualTo("payload.json");
    assertThat(analysis.processIds()).contains("bankDisputeHandling", "refundingProcess");
    assertThat(analysis.staticJobTypes())
        .extracting("type")
        .containsExactlyInAnyOrder(
            "customer_notification",
            "extract_data_from_document",
            "inform_about_failed_claim",
            "refunding");
    assertThat(analysis.dmnReferences())
        .extracting("decisionId")
        .contains("determine-fraud-rating-1dmmwcu");
    assertThat(analysis.userTasks())
        .extracting("elementName")
        .containsExactly("Decide on fraud case");
    assertThat(analysis.callActivities())
        .extracting("calledProcessId")
        .contains("refundingProcess");
    assertThat(payload).containsEntry("customer_claim_frequency", 1);
    assertThat(payload).containsEntry("needsManualReview", false);
    assertThat(payload).containsEntry("vendor_claim_frequency", 5);
  }
}
