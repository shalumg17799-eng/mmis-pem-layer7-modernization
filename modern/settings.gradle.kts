rootProject.name = "pem-modern"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include("libs:fixed-width", "libs:service-kit", "libs:test-support")

include(
    "services:api-gateway",
    "services:provider-service",
    "services:enrollment-service",
    "services:screening-service",
    "services:risk-service",
    "services:revalidation-service",
    "services:sanctions-service",
    "services:notification-service",
    "services:directory-service",
    "services:tmsis-service",
)

// Local low-memory runtime: every service in one JVM, each with its own Spring context, port and database (ADR-011)
include("infra:local-runtime")
