package io.github.mhnuk2007.finsim;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinSimApplicationTests {

  @Test
  void contextLoads() {}
}
