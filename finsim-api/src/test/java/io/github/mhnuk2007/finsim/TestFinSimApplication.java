package io.github.mhnuk2007.finsim;

import org.springframework.boot.SpringApplication;

public class TestFinSimApplication {

  public static void main(String[] args) {
    SpringApplication.from(FinSimApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
