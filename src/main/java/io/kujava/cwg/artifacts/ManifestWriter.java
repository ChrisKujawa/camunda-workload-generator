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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ManifestWriter {

  public Path write(final Path outputDirectory, final WorkloadManifest manifest)
      throws IOException {
    Files.createDirectories(outputDirectory);
    final var manifestFile = outputDirectory.resolve("manifest.json");
    ArtifactJson.MAPPER.writeValue(manifestFile.toFile(), manifest);
    return manifestFile;
  }
}
