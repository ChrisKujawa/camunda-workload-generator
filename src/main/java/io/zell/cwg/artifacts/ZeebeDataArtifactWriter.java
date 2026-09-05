package io.zell.cwg.artifacts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    try (final var zipOutput = new ZipOutputStream(Files.newOutputStream(zipPath));
        final var paths = Files.walk(sourceDirectory)) {
      final var files = paths.filter(Files::isRegularFile).toList();
      for (final var file : files) {
        final var entryName = sourceDirectory.relativize(file).toString().replace('\\', '/');
        zipOutput.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zipOutput);
        zipOutput.closeEntry();
      }
    }
    return zipPath;
  }

  private static void deleteIfExists(final Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (final var paths = Files.walk(path)) {
      final var entries = paths.sorted(Comparator.reverseOrder()).toList();
      for (final var entry : entries) {
        Files.delete(entry);
      }
    }
  }

  @FunctionalInterface
  public interface DataDirectoryCopier {
    void copyTo(Path targetDirectory) throws IOException;
  }
}
