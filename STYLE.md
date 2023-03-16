# Code style guide

Note: this is a set of recommendations, not requirements.

## Formatting

Before you commit, make sure to run the Spotless formatter:

	$ mvn spotless:apply

## Java constructors vs factory methods

- Prefer plain constructors to _factory_ static methods (e.g., `new ABC()` over `ABC.create()` / `ABC.of()`).
- Do use factory methods when:
- some initialization logic cannot be expressed from a constructor, or it becomes inelegant,
- hiding a concrete implementation behind an interface (e.g., `val vertx = Vertx.vertx();`).

## Getters and setters

- Prefer the JavaBeans convention of `getABC()` / `setABC(abc)` for domain objects that are intended to be serialized through _Jackson_.

## Top-level components

- Prepend `Insights` to the interface definitions of top-level components (e.g., `InsightsHttpClient`).
