package io.zell.cwg.artifacts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportWriter {

  public Path write(final Path outputDirectory, final WorkloadReport report) throws IOException {
    Files.createDirectories(outputDirectory);
    final var reportFile = outputDirectory.resolve("report.json");
    ArtifactJson.MAPPER.writeValue(reportFile.toFile(), report);
    return reportFile;
  }
}
