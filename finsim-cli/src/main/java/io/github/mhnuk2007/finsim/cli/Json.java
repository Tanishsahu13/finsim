package io.github.mhnuk2007.finsim.cli;

import java.util.List;
import java.util.stream.Collectors;

/** Just enough JSON writing for the CLI's small request bodies. */
final class Json {

  private Json() {}

  static String quote(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }

  static String array(List<String> values) {
    return values.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]"));
  }
}
