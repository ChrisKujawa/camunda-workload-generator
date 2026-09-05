package io.zell.cwg.runtime;

public interface CamundaRuntime extends AutoCloseable {

  void start();

  String gatewayAddress();

  @Override
  void close();
}
