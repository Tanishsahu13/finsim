package io.github.mhnuk2007.finsim.registry.web;

import io.github.mhnuk2007.finsim.registry.Network;
import io.github.mhnuk2007.finsim.registry.Registry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration API for networks. Transfers on a network are added in a later issue. */
@RestController
@RequestMapping("/api/v1/networks")
class NetworkController {

  private final Registry registry;

  NetworkController(Registry registry) {
    this.registry = registry;
  }

  @GetMapping
  List<Network> list() {
    return registry.networks();
  }

  @GetMapping("/{networkId}")
  Network get(@PathVariable("networkId") String networkId) {
    return registry.network(networkId);
  }

  /** Adds an institution to the network. Idempotent. */
  @PostMapping("/{networkId}/members")
  Network addMember(
      @PathVariable("networkId") String networkId, @RequestBody AddMemberRequest request) {
    return registry.addMember(networkId, request.institutionId());
  }
}
