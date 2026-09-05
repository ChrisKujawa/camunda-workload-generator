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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ZeebeDataArtifactWriter {

  private static final String ZEEBE_DATA_DIRECTORY = "zeebe-data";
  private static final String ZEEBE_DATA_ZIP = "zeebe-data.zip";

  public ZeebeDataArtifacts write(
      final Path outputDirectory, final DataDirectoryCopier copier, final boolean zip)
      throws IOException {
    Files.createDirectories(outputDirectory);
    final var zeebeDataDirectory = outputDirectory.resolve(ZEEBE_DATA_DIRECTORY);
    final var zeebeDataZip = outputDirectory.resolve(ZEEBE_DATA_ZIP);

    deleteIfExists(zeebeDataDirectory);
    Files.deleteIfExists(zeebeDataZip);

    copier.copyTo(zeebeDataDirectory);
    if (!Files.isDirectory(zeebeDataDirectory)) {
      throw new IOException("Zeebe data copy did not create directory: " + zeebeDataDirectory);
    }

    final var zipPath = zip ? zipDirectory(zeebeDataDirectory, zeebeDataZip) : null;
    return ZeebeDataArtifacts.from(outputDirectory, zeebeDataDirectory, zipPath);
  }

  private static Path zipDirectory(final Path sourceDirectory, final Path zipPath)
      throws IOException {
    Files.createDirectories(zipPath.getParent());
    try (final var zipOutput = new ZipOutputStream(Files.newOutputStream(zipPath))) {
      writeZipEntries(zipOutput, sourceDirectory, sourceDirectory);
    }
    return zipPath;
  }

  private static void writeZipEntries(
      final ZipOutputStream zipOutput, final Path rootDirectory, final Path currentDirectory)
      throws IOException {
    final var entries = new ArrayList<Path>();
    try (final var paths = Files.list(currentDirectory)) {
      final var iterator = paths.iterator();
      while (iterator.hasNext()) {
        entries.add(iterator.next());
      }
    }

    entries.sort(Comparator.comparing(path -> path.getFileName().toString()));
    for (final var entry : entries) {
      if (Files.isDirectory(entry)) {
        writeZipEntries(zipOutput, rootDirectory, entry);
      } else if (Files.isRegularFile(entry)) {
        final var relativePath = rootDirectory.relativize(entry).toString().replace('\\', '/');
        zipOutput.putNextEntry(new ZipEntry(ZEEBE_DATA_DIRECTORY + "/" + relativePath));
        Files.copy(entry, zipOutput);
        zipOutput.closeEntry();
      }
    }
  }

  private static void deleteIfExists(final Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    Files.walkFileTree(
        path,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(final Path directory, final IOException error)
              throws IOException {
            if (error != null) {
              throw error;
            }
            Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  @FunctionalInterface
  public interface DataDirectoryCopier {
    void copyTo(Path targetDirectory) throws IOException;
  }
}
