package io.zell.cwg.resources;

import java.nio.file.Path;

public record ResourceFile(Path path, Path relativePath, ResourceType type) {

  @Override
  public String toString() {
    return "%s %s".formatted(type, relativePath.toString().replace('\\', '/'));
  }
}
