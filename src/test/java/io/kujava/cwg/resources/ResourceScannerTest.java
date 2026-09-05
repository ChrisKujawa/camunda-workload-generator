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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResourceScannerTest {

  @TempDir private Path tempDir;

  @Test
  void shouldClassifyDeployableResourcesSeparatelyFromPayloadAndConfigFiles() throws Exception {
    // given
    write("processes/invoice.bpmn");
    write("decisions/invoice.dmn");
    write("forms/invoice.form");
    write("payloads/invoice.json");
    write("payloads/workload.yaml");
    write("notes/readme.txt");

    // when
    final var result = new ResourceScanner().scan(tempDir);

    // then
    assertThat(result.deployableResources())
        .extracting(
            resource -> resource.relativePath().toString().replace('\\', '/'), ResourceFile::type)
        .containsExactly(
            tuple("decisions/invoice.dmn", ResourceType.DMN),
            tuple("forms/invoice.form", ResourceType.FORM),
            tuple("processes/invoice.bpmn", ResourceType.BPMN));
    assertThat(result.payloadOrConfigResources())
        .extracting(
            resource -> resource.relativePath().toString().replace('\\', '/'), ResourceFile::type)
        .containsExactly(
            tuple("payloads/invoice.json", ResourceType.JSON),
            tuple("payloads/workload.yaml", ResourceType.YAML));
    assertThat(result.otherResources())
        .extracting(
            resource -> resource.relativePath().toString().replace('\\', '/'), ResourceFile::type)
        .containsExactly(tuple("notes/readme.txt", ResourceType.OTHER));
  }

  private void write(final String path) throws Exception {
    final var file = tempDir.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "test");
  }
}
