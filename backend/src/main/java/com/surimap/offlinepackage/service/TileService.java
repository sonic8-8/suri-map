package com.surimap.offlinepackage.service;

import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;

public interface TileService {

  TileStyleResponse getStyle(String styleId);

  TileBlobResponse getTile(String style, int z, int x, int y);

  TileBlobResponse getGlyph(String fontStack, String range);
}
