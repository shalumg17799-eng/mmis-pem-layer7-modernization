plugins { id("pem.java-library") }

// Copybook-driven fixed-width codec (data-engineer). Layouts are JSON files exported from the knowledge graph.
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
