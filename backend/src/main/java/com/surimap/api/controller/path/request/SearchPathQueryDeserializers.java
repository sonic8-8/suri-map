package com.surimap.api.controller.path.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** 버전 숫자→문자열, 소수 순번→정수 같은 Jackson의 자동 보정은 새 조회 계약에서 사용하지 않는다. */
public final class SearchPathQueryDeserializers {
  private SearchPathQueryDeserializers() {}

  public static class Version extends StdDeserializer<String> {
    public Version() {
      super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (!parser.hasToken(JsonToken.VALUE_STRING)) {
        throw context.wrongTokenException(
            parser, String.class, JsonToken.VALUE_STRING, "version must be a decimal string");
      }
      return parser.getText();
    }
  }

  public static class PointOrder extends StdDeserializer<Integer> {
    public PointOrder() {
      super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
        throw context.wrongTokenException(
            parser, Integer.class, JsonToken.VALUE_NUMBER_INT, "point order must be an integer");
      }
      return parser.getIntValue();
    }
  }
}
