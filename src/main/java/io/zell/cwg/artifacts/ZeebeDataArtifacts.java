package io.zell.cwg.artifacts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ZeebeDataArtifacts(String directory, String zip, long files, long bytes) {

  public static ZeebeDataArtifacts from(
      final Path outputDirectory, final Path directory, final Path zip) throws IOException {
    return new ZeebeDataArtifacts(
        relative(outputDirectory, directory, true),
        zip == null ? null : relative(outputDirectory, zip, false),
        countRegularFiles(directory),
        totalRegularFileBytes(directory));
  }

  private static long countRegularFiles(final Path directory) throws IOException {
    try (final var paths = Files.walk(directory)) {
      return paths.filter(Files::isRegularFile).count();
    }
  }

  private static long totalRegularFileBytes(final Path directory) throws IOException {
    long bytes = 0;
    try (final var paths = Files.walk(directory)) {
      final var files = paths.filter(Files::isRegularFile).iterator();
      while (files.hasNext()) {
        final var file = files.next();
        bytes += Files.size(file);
      }
    }
    return bytes;
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
}
