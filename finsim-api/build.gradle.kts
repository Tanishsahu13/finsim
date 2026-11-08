plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":finsim-core"))
    implementation(project(":finsim-registry"))
    implementation(project(":finsim-sim"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation(libs.archunit.junit5)
}
