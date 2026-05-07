package com.surimap.offlinepackage.exception;

public class TileUnavailableException extends RuntimeException {

  public TileUnavailableException() {
    super("tile_unavailable");
  }
}
