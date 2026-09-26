# Fluent JDBC Client for Jakarta EE/CDI

A lightweight, framework-agnostic fluent JDBC client for Jakarta EE and CDI
applications. It mirrors the developer experience of Spring's `JdbcClient`
without requiring Spring.

The project is organized into three modules:

- `jdbc-client-core`: the core `JdbcClient` API and supporting types.
- `jdbc-client-config`: optional MicroProfile Config integration.
- `jdbc-client-cdi`: CDI producers for `JdbcClient` and converters.

Start with the [reference documentation](https://hantsy.github.io/jdbc-client-cdi/) for installation, usage patterns, and configuration details.

## Building

### Prerequisites

- JDK 21+
- Maven 3.9+ (or use the included Maven wrapper `./mvnw`)

### Build

Clone the repository:

```bash
git clone https://github.com/hantsy/jdbc-client-cdi.git
```

Build all modules and run the unit tests (integration tests are skipped by default):

```bash
cd jdbc-client-cdi
./mvnw clean install
```

Run the Arquillian integration tests on GlassFish, which downloads and boots GlassFish 8:

```bash
./mvnw -pl integration-tests -Parq-glassfish-managed verify
```

Run the Arquillian integration tests on WildFly, which downloads and boots WildFly 41:

```bash
./mvnw -pl integration-tests -Parq-wildfly-managed verify
```

### Building the documentation

For a local documentation site:

```bash
python -m pip install -r docs/requirements.txt
mkdocs serve
```

To build the documentation site:

```bash
mkdocs build --strict
```

## Code formatting

This project uses [Netflix jfmt](https://github.com/Netflix/jfmt) — the Java
formatter bundled with the [`ja`](https://github.com/Netflix/ja) toolchain.
jfmt has **no configuration**: its style is fixed and cannot be overridden
per project.

| Rule | Value |
|---|---|
| Block indentation | 4 spaces |
| Continuation indentation | 8 spaces |
| Maximum code line length | **none** (breaks at syntactic boundaries) |
| Javadoc prose width | 80 columns (fixed) |
| Import order | `import module` → `java.*` / `javax.*` → third-party → `static` |
| Import normalization | unused removed, wildcards expanded, qualified names shortened |
| Braces | required around single-statement `if` / `for` / `while` bodies |

> jfmt is currently in **preview**; the CI workflow pins the `ja` version.

### Installing the toolchain

jfmt requires a JDK 25 or later. Install the `ja` toolchain to get `jfmt` and
`ja fmt` on your `PATH`.

On Windows PowerShell:

```powershell
irm https://raw.githubusercontent.com/Netflix/ja/main/install.ps1 | iex
```

On macOS / Linux:

```bash
curl -fsSL https://raw.githubusercontent.com/Netflix/ja/main/install.sh | bash
```

To pin a specific version pass `-JaVersion <version>` on Windows, or set
`JA_VERSION=<version>` before the shell script. The installer prints the
activation steps, including adding `~/.local/bin` to your `PATH`.

### Local git hook

If you install the `ja` toolchain, a pre-commit hook at
`.git/hooks/pre-commit` can format staged `*.java` files and re-stage them
before each commit. The hook is local to your clone (`.git/` is never
committed); the CI `format-check` job is the shared enforcement point. If
`jfmt` is not on your `PATH`, the hook prints the install command and lets
the commit proceed unblocked.

### CI gate

`.github/workflows/build.yml` runs `jfmt --check` on every push and pull
request via a dedicated `format-check` job using
`Netflix/ja/.github/actions/setup-ja`. PRs with unformatted code fail this
check; fix them locally and push the reformatted files.

## Contributing

Contributions are welcome. Issues, pull requests, and feature suggestions are all encouraged.
