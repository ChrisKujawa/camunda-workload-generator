package io.zell.cwg.runtime;

import io.zell.cwg.artifacts.ZeebeDataArtifacts;
import java.io.IOException;
import java.nio.file.Path;

public interface ZeebeDataArtifactSource {

  ZeebeDataArtifacts writeZeebeData(Path outputDirectory, boolean zip) throws IOException;
}
