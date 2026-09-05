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

public record ZeebeDataArtifacts(String directory, String zip, long files, long bytes) {

  public static ZeebeDataArtifacts from(
      final Path outputDirectory, final Path directory, final Path zip) throws IOException {
    final var stats = stats(directory);
    return new ZeebeDataArtifacts(
        relative(outputDirectory, directory, true),
        zip == null ? null : relative(outputDirectory, zip, false),
        stats.files(),
        stats.bytes());
  }

  private static FileStats stats(final Path directory) throws IOException {
    long files = 0;
    long bytes = 0;
    try (final var paths = Files.walk(directory)) {
      final var regularFiles = paths.filter(Files::isRegularFile).iterator();
      while (regularFiles.hasNext()) {
        final var file = regularFiles.next();
        files++;
        bytes += Files.size(file);
      }
    }
    return new FileStats(files, bytes);
  }

  private static String relative(
      final Path outputDirectory, final Path path, final boolean directory) {
    var relative =
        outputDirectory
            .toAbsolutePath()
            .normalize()
            .relativize(path.toAbsolutePath().normalize())
            .toString()
            .replace('\\', '/');
    if (directory && !relative.endsWith("/")) {
      relative += "/";
    }
    return relative;
  }

  private record FileStats(long files, long bytes) {}
}
