package io.github.mhnuk2007.finsim.registry.internal;

import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InvalidInstitution;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * First line of SSRF defense for institution base URLs.
 *
 * <p>Always rejected: non-http(s) schemes, credentials in the URL, and link-local addresses (which
 * include the cloud metadata address 169.254.169.254). When {@code allowPrivateAddresses} is false,
 * loopback, private ranges, and {@code localhost} are rejected too.
 *
 * <p>This validates literal addresses only and never performs DNS lookups. Protection against
 * hostnames that resolve to private addresses must also be applied when FinSim makes the outbound
 * call (planned; see docs/registry.md).
 */
public final class BaseUrlValidator {

  private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

  private BaseUrlValidator() {}

  public static void validate(String baseUrl, boolean allowPrivateAddresses) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new InvalidInstitution("baseUrl is required.");
    }
    URI uri;
    try {
      uri = new URI(baseUrl.trim());
    } catch (URISyntaxException e) {
      throw new InvalidInstitution("baseUrl is not a valid URL.");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new InvalidInstitution("baseUrl must use http or https.");
    }
    if (uri.getUserInfo() != null) {
      throw new InvalidInstitution("baseUrl must not contain credentials.");
    }
    if (uri.getFragment() != null) {
      throw new InvalidInstitution("baseUrl must not contain a fragment.");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new InvalidInstitution("baseUrl must include a host.");
    }
    host = host.toLowerCase(Locale.ROOT);
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }

    if (isIpLiteral(host)) {
      InetAddress address = parseLiteral(host);
      if (address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
        throw new InvalidInstitution("baseUrl points to a link-local or unspecified address.");
      }
      if (!allowPrivateAddresses && isPrivateOrLoopback(address)) {
        throw new InvalidInstitution("baseUrl points to a private or loopback address.");
      }
    } else if (!allowPrivateAddresses
        && (host.equals("localhost") || host.endsWith(".localhost"))) {
      throw new InvalidInstitution("baseUrl points to localhost.");
    }
  }

  private static boolean isIpLiteral(String host) {
    return host.contains(":") || IPV4.matcher(host).matches();
  }

  private static InetAddress parseLiteral(String host) {
    try {
      // Safe: the input is an IP literal, so no DNS lookup happens.
      return InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      throw new InvalidInstitution("baseUrl host is not a valid IP address.");
    }
  }

  private static boolean isPrivateOrLoopback(InetAddress address) {
    if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (bytes.length == 16) {
      return (bytes[0] & 0xFE) == 0xFC; // IPv6 unique local fc00::/7
    }
    return bytes[0] == 100 && (bytes[1] & 0xC0) == 64; // carrier-grade NAT 100.64.0.0/10
  }
}
