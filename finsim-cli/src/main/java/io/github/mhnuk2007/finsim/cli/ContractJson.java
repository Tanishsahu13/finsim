package io.github.mhnuk2007.finsim.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON reader for contract responses; rejects malformed or unexpected structures. */
final class ContractJson {
  private final String source;
  private int pos;

  private ContractJson(String source) {
    this.source = source;
  }

  static Object parse(String source) {
    ContractJson reader = new ContractJson(source);
    Object value = reader.value();
    reader.space();
    if (reader.pos != source.length()) throw new IllegalArgumentException("Trailing JSON data");
    return value;
  }

  static String write(Object value) {
    if (value == null) return "null";
    if (value instanceof String s) return Json.quote(s);
    if (value instanceof Boolean || value instanceof Number) return value.toString();
    if (value instanceof Map<?, ?> map) {
      List<String> fields = new ArrayList<>();
      map.forEach((key, item) -> fields.add(Json.quote((String) key) + ":" + write(item)));
      return "{" + String.join(",", fields) + "}";
    }
    if (value instanceof List<?> list) {
      return "[" + String.join(",", list.stream().map(ContractJson::write).toList()) + "]";
    }
    throw new IllegalArgumentException("Unsupported JSON value");
  }

  private Object value() {
    space();
    if (pos == source.length()) throw bad();
    char c = source.charAt(pos);
    if (c == '"') return string();
    if (c == '{') {
      pos++;
      Map<String, Object> map = new LinkedHashMap<>();
      space();
      if (take('}')) return map;
      do {
        space();
        if (pos == source.length() || source.charAt(pos) != '"') throw bad();
        String key = string();
        space();
        if (!take(':')) throw bad();
        map.put(key, value());
        space();
        if (take('}')) return map;
      } while (take(','));
      throw bad();
    }
    if (c == '[') {
      pos++;
      List<Object> list = new ArrayList<>();
      space();
      if (take(']')) return list;
      do {
        list.add(value());
        space();
        if (take(']')) return list;
      } while (take(','));
      throw bad();
    }
    for (String literal : List.of("true", "false", "null")) {
      if (source.startsWith(literal, pos)) {
        pos += literal.length();
        return switch (literal) {
          case "true" -> true;
          case "false" -> false;
          default -> null;
        };
      }
    }
    int start = pos;
    if (take('-')) {}
    while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
    if (take('.')) while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
    if (take('e') || take('E')) {
      if (!take('+')) take('-');
      while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
    }
    if (start == pos) throw bad();
    try {
      return new java.math.BigDecimal(source.substring(start, pos));
    } catch (NumberFormatException e) {
      throw bad();
    }
  }

  private String string() {
    int start = pos++;
    while (pos < source.length()) {
      char c = source.charAt(pos++);
      if (c == '"') {
        String raw = source.substring(start, pos);
        return unescape(raw);
      }
      if (c == '\\') {
        if (pos == source.length()) throw bad();
        if (source.charAt(pos++) == 'u') {
          if (pos + 4 > source.length()) throw bad();
          pos += 4;
        }
      } else if (c < 32) throw bad();
    }
    throw bad();
  }

  private static String unescape(String raw) {
    StringBuilder result = new StringBuilder();
    for (int i = 1; i < raw.length() - 1; i++) {
      char c = raw.charAt(i);
      if (c != '\\') {
        result.append(c);
        continue;
      }
      char escape = raw.charAt(++i);
      switch (escape) {
        case '"', '\\', '/' -> result.append(escape);
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        case 'u' -> {
          try {
            result.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid JSON unicode escape", e);
          }
          i += 4;
        }
        default -> throw new IllegalArgumentException("Invalid JSON escape");
      }
    }
    return result.toString();
  }

  private boolean take(char c) {
    if (pos < source.length() && source.charAt(pos) == c) {
      pos++;
      return true;
    }
    return false;
  }

  private void space() {
    while (pos < source.length() && " \t\r\n".indexOf(source.charAt(pos)) >= 0) pos++;
  }

  private IllegalArgumentException bad() {
    return new IllegalArgumentException("Malformed JSON at character " + pos);
  }
}
