package io.zell.cwg.runtime;

import java.util.Optional;

public interface SecondaryStorageRuntime {

  Optional<SecondaryStorageEndpoint> secondaryStorageEndpoint();
}
