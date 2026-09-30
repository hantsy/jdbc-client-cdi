# Contributing

Thanks for your interest in contributing to **FluDa: Fluent Data Toolkit for Jakarta EE/CDI**!

This document describes how to set up the project, make changes, and submit them for review.

## Ways to contribute

- **Report bugs** or **request features** by opening a [GitHub issue](https://github.com/hantsy/jdbc-client-cdi/issues).
- **Improve the documentation** — the MkDocs site lives under `docs/`.
- **Fix bugs or add features** by opening a pull request (see below).

## Prerequisites

- JDK 21 or later (JDK 25 is required to publish a release to Maven Central).
- Maven 3.9+ (or use the included wrapper `./mvnw`).
- Docker (optional — only needed to run the Arquillian integration tests that boot MariaDB / PostgreSQL).

## Building

Clone the repository and build everything:

```bash
git clone https://github.com/hantsy/jdbc-client-cdi.git
cd jdbc-client-cdi
./mvnw clean install
```

## Project structure

```
.
├── jdbc-client/                # fluda-jdbc-client aggregator
│   ├── core/                   # fluda-jdbc-client-core — the plain JdbcClient API (DataSource only)
│   ├── config/                 # fluda-jdbc-client-config — MicroProfile Config integration
│   └── cdi/                    # fluda-jdbc-client-cdi — CDI producers for JdbcClient and converters
├── examples/                   # runnable examples (built separately from the main reactor)
│   ├── vanilla/                # core only, no CDI
│   ├── javase/                 # core + cdi (Weld SE)
│   ├── servlet/                # core + cdi (Tomcat 11)
│   └── jakartaee/              # core + cdi + config (GlassFish / WildFly)
└── docs/                       # MkDocs reference documentation
```

The `examples` module is documented in [examples/README.md](examples/README.md).

## Code style

Formatting is driven by the committed [`.editorconfig`](.editorconfig) (IntelliJ IDEA settings):

- 4-space indentation, 120-column maximum line width, CRLF line endings.
- No wildcard imports — one class per import.
- Import layout: project classes, then `java.**` / `javax.**` / `jakarta.**`, then static imports.

Use IntelliJ IDEA's **Reformat Code** action (which reads `.editorconfig`) before committing, so formatting changes are kept out of functional diffs.

## Testing

Run the unit tests:

```bash
./mvnw test
```

The examples ship Arquillian integration tests that deploy to real containers and databases. They are
skipped by default; see [examples/README.md](examples/README.md) for how to run them (they require
MariaDB and/or PostgreSQL, provided locally by Docker Compose).

## Documentation

The reference documentation is built with [MkDocs Material](https://squidfunk.github.io/mkdocs-material/):

```bash
python -m pip install -r docs/requirements.txt
mkdocs serve          # live preview
mkdocs build --strict # build, failing on warnings
```

Update `docs/` (and `mkdocs.yml`) whenever you change the public API or add a feature, and add a link to
the new page from `mkdocs.yml`'s `nav`.

## Commits and pull requests

- Follow [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`,
  `chore:`, `ci:`, `refactor:` (see the existing git history for examples).
- Keep each pull request to a single logical change, and reference any related issue.
- Add or update tests and documentation alongside the change.

## License

All contributions are licensed under the [Apache License, Version 2.0](LICENSE). By contributing you
agree that your contributions are licensed under the same terms.
