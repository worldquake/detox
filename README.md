# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

How to build the tool:

```bash
# Build everything
./gradlew build

# Run the root detox framework shell
./gradlew run

# Run the szexpartnerek subproject
./gradlew :szexpartnerek:run

# Run tests
./gradlew test

# Run tests for a specific subproject
./gradlew :szexpartnerek:test

# Build distribution zip (bootDistZip/distTar are disabled; use distZip)
./gradlew distZip

# Regenerate ANTLR grammar sources
./gradlew generateGrammarSource
```

Build output goes to `target/` (not the standard `build/`), configured in `gradle/common.gradle`.

To enable the `dev` Spring profile (e.g. `application-dev.properties`), run with `-DIDE=true`:

```bash
./gradlew run -Djvm.args="-DIDE=true"
```

Also, the `startup.*` scripts can be used as in the build `target/distributions` folder the zip is built.

## Architecture Overview

**Multi-module Gradle project** (Java 22 toolchain, source/target compat 21):

- Root module: `detox` — reusable utility framework
- Submodule: `szexpartnerek` — web scraper application that depends on root

### Root Module (`hu.detox.*`)

**Entry Points:**

- `hu.Main` — bootstraps Spring, resolves `application.yaml` from the file system (via `Agent` path resolution), sets
  `root` system property for command name derivation
- `hu.detox.Main` — extends `hu.Main`, sets the shell prompt, provides `cr()` factory for Spring Shell
  `CommandRegistration`
- `hu.detox.test.Test` — standalone AI chat test app (not a JUnit test); runs with `Main.main(Test.class, args)`

**Key Infrastructure Classes:**

- `Agent` — static class initialized before Spring. Manages environment dirs: `HOME` (`~/.detox-utils/`), `WORK` (
  `target/`), `ENV` (`DTX_ENV_HOME`), `SYS` (`dtx.sys`). File resolution via `Agent.getFile()/getFiles()` searches these
  dirs in priority order. System props: `debug`, `dtx.test`, `dtx.sys`, `dtx.env.home`, `dtx.user.home`.

- `DetoxConfig` — root Spring `@Configuration`. Sets up OpenAI API bean with custom header rewriting (supports
  non-Bearer auth header remapping via `openai.header` property), registers type converters, manages shared
  `AsyncTaskExecutor` and `GenericConversionService`.

- `Shell` — Spring Shell runner. Supports **dynamic package loading**: calling `shell.execute(packageSuffix, args)`
  scans and loads a Spring child context for that package at runtime. Packages loaded this way are registered into the
  parent context.

- `Commands` — root shell commands: `run` (executes subcommands with optional `--test`/`--log`), `quit`/`exit`. Also
  provides the shell prompt.

- `ConditionalOnNoApp` — `@TypeFilter` that excludes classes annotated with `@ConditionalOnNoApp.Annotation` during
  child-context scanning (prevents re-loading framework bootstrap classes).

**Command Registration Pattern:**
`Main.cr(cmdName)` auto-derives command group from the calling class's package (stripping the `root` prefix and
`.spring` segment, mapping `.` to ` `). Passing `null` or the null Unicode char uses the calling method name as the
command name.

**ANTLR Grammar:**
`AmountCalculator.g4` defines an expression grammar for unit-aware math (using JScience `Amount`). Grammar is compiled
to `target/generated/antlr/` and auto-added to `sourceSets.main.java`.

### Submodule (`hu.detox.szexpartnerek.*`)

Web scraper app with:

- **`sync/`** — data sync/transform pipeline (`ITrafoEngine`, `IPersister`, `AbstractPersister`)
- **`ws/`** — Spring MVC REST API with SQLite backend via JDBC + Flyway
- **`spring/`** — shell commands (`SyncCommand`, `WSCommand`) and Spring config (`SzexConfig`)
- **`sync/rl/`** — scraping components for partner/user data with feedback/rating logic

### Configuration Lookup Order

At runtime, `Agent.getFiles(name, filter)` resolves config/resources in this order:

1. Absolute path
2. `WORK/` (= `target/`)
3. `HOME/` (= `~/.detox-utils/`)
4. Each `SYS` dir
5. `ENV/` (`DTX_ENV_HOME`)
6. `BASE/` (installation dir)

An external `application.yaml` in `WORK` overrides the classpath one.

### Startup Script

`src/main/sh/startup.sh` is the production launcher. It:

- Auto-detects OS (Linux/Mac/Windows/Cygwin)
- Reads `java-args.txt` and `<name>-args.txt` files from multiple dirs
- Resolves main class from script name (defaults to `hu.detox.Main`)
- Supports `DEBUG=local|remote|mgmtr` for debug/JMX modes
