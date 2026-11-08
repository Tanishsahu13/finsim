plugins {
    application
}

dependencies {
    implementation(libs.picocli)
}

application {
    mainClass.set("io.github.mhnuk2007.finsim.cli.FinSimCli")
    applicationName = "finsim"
}
