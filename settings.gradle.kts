rootProject.name = "finsim"

include(
    "finsim-core",
    "finsim-registry",
    "finsim-sim",
    "finsim-api",
    "finsim-cli",
)

include("sample-bank-java")
project(":sample-bank-java").projectDir = file("samples/bank-java")

