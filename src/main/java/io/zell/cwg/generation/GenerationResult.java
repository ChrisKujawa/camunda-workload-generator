package io.zell.cwg.generation;

import java.nio.file.Path;

public record GenerationResult(int deployedResources, Path manifestPath, Path reportPath) {}
