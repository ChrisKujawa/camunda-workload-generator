package io.zell.cwg.artifacts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ManifestWriter {

  public Path write(final Path outputDirectory, final WorkloadManifest manifest) throws IOException {
    Files.createDirectories(outputDirectory);
    final var manifestFile = outputDirectory.resolve("manifest.json");
    ArtifactJson.MAPPER.writeValue(manifestFile.toFile(), manifest);
    return manifestFile;
  }
}
