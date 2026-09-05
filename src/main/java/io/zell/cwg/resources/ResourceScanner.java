package io.zell.cwg.resources;

import io.zell.cwg.config.ConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

public final class ResourceScanner {

  public ResourceScanResult scan(final Path resourcesDirectory) throws IOException {
    if (!Files.exists(resourcesDirectory)) {
      throw new ConfigException("Resources directory does not exist: %s".formatted(resourcesDirectory));
    }
    if (!Files.isDirectory(resourcesDirectory)) {
      throw new ConfigException("Resources path is not a directory: %s".formatted(resourcesDirectory));
    }

    final var deployableResources = new ArrayList<ResourceFile>();
    final var payloadOrConfigResources = new ArrayList<ResourceFile>();
    final var otherResources = new ArrayList<ResourceFile>();

    try (final var paths = Files.walk(resourcesDirectory)) {
      paths
          .filter(Files::isRegularFile)
          .sorted(Comparator.comparing(path -> resourcesDirectory.relativize(path).toString()))
          .forEach(
              path -> {
                final var resource = new ResourceFile(path, resourcesDirectory.relativize(path), type(path));
                if (resource.type().isDeployable()) {
                  deployableResources.add(resource);
                } else if (resource.type().isPayloadOrConfig()) {
                  payloadOrConfigResources.add(resource);
                } else {
                  otherResources.add(resource);
                }
              });
    }

    return new ResourceScanResult(deployableResources, payloadOrConfigResources, otherResources);
  }

  private static ResourceType type(final Path path) {
    final var fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (fileName.endsWith(".bpmn")) {
      return ResourceType.BPMN;
    }
    if (fileName.endsWith(".dmn")) {
      return ResourceType.DMN;
    }
    if (fileName.endsWith(".form")) {
      return ResourceType.FORM;
    }
    if (fileName.endsWith(".json")) {
      return ResourceType.JSON;
    }
    if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
      return ResourceType.YAML;
    }
    return ResourceType.OTHER;
  }
}
