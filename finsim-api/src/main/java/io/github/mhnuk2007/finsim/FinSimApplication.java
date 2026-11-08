package io.github.mhnuk2007.finsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Each direct sub-package of {@code io.github.mhnuk2007.finsim} ({@code core}, {@code
 * registry}, {@code sim}, ...) is an application module verified by Spring Modulith.
 */
@SpringBootApplication
public class FinSimApplication {

  public static void main(String[] args) {
    SpringApplication.run(FinSimApplication.class, args);
  }
}
