package io.github.mhnuk2007.finsim.sim;

import java.util.Random;

/** Deterministic pseudo-random number generator for reproducible fault evaluation. */
public final class DeterministicRandom {

  private final Random random;

  public DeterministicRandom(Long seed) {
    this.random = seed != null ? new Random(seed) : new Random();
  }

  public synchronized double nextDouble() {
    return random.nextDouble();
  }
}
