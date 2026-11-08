// Sample institution: a tiny in-memory bank that implements the finsim/v1 participant contract.
// It deliberately does NOT depend on finsim-core or any other FinSim module, to prove that
// an institution only needs the HTTP contract.
plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
