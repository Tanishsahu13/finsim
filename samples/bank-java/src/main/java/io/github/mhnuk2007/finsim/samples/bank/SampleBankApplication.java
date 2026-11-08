package io.github.mhnuk2007.finsim.samples.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * A sample FinSim institution: an in-memory bank implementing the finsim/v1 participant contract.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SampleBankApplication {

  public static void main(String[] args) {
    SpringApplication.run(SampleBankApplication.class, args);
  }
}
