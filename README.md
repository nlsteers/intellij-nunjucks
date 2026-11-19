# Nunjucks for IntelliJ

Language support for [Nunjucks](https://mozilla.github.io/nunjucks/templating.html) templates in IntelliJ-based IDEs.

## Features

- **Files**: `.njk`, `.nunjucks`, `.nj`, plus `.html`/`.htm` files containing `{% … %}` tags (can be switched off in settings).
  The template data language can be changed per file/directory under *Settings | Languages & Frameworks | Template Data Languages*.
- **Parsing**: every tag and expression in the templating docs, including `asyncEach`/`asyncAll`/`ifAsync`, `raw`/`verbatim`,
  whitespace control (`{%-`, `-}}`), regex literals, inline `if`, `is`/`is not` tests, `in`/`not in`, slices, keyword arguments,
  `call` with caller arguments, and extension tags. Unclosed blocks, stray end tags and junk inside tags are recovered locally.
- **Editor**: highlighting (customisable under *Editor | Color Scheme | Nunjucks*), brace and `{% if %}`↔`{% endif %}` matching,
  folding, `{# #}` commenting everywhere in the file, structure view, auto-closing of `{{`/`{%`/`{#` and end tags, live templates.
- **Formatter**: indents block bodies and object/array/argument literals, cooperating with the HTML formatter. Indent size
  comes from the template data language (HTML) settings, so `.editorconfig` applies.
- **Navigation & refactoring**: template paths, imported and namespaced macros, keyword arguments, variables, loop targets,
  parameters, `endblock` names, block overrides (gutter icon and *Go to Declaration*), custom filters (jumps to the
  `env.addFilter()` call). Find usages and rename.
- **Completion**: tag names, filters and tests including custom ones, variables/macros/imports in scope, globals, `loop.*`, 
  macro keyword arguments, imported macro names, template paths, and GOV.UK component options.
- **GOV.UK Frontend**: object keys passed to component macros are completed, documented and checked against the
  component's `macro-options.json`, including nested `params` and `isComponent` options.
- **Documentation & parameter info**: built-in filters/tests/globals, macros (using a `{# … #}` comment directly above
  them), component options; parameter hints for macro calls.
- **Inspections** (*Settings | Editor | Inspections | Nunjucks*): unknown filter/test, unresolved template, unresolved
  imported macro, unused import, unused scoped variable, mismatched end tag, unknown macro argument, unknown/deprecated
  component option, mismatched bracket, duplicate block.

## Building and installing locally

### 1. Prerequisites (once)

- **JDK 25.** The build compiles with a JDK 25 toolchain.
- **Network access to JetBrains' repositories.** The first build downloads the IntelliJ test framework, Grammar-Kit and
  JFlex from `cache-redirector.jetbrains.com`. Behind an inspection proxy this needs either a TLS-inspection exemption for
  `*.jetbrains.com` and `*.intellij.net`, or the trust store described in [Behind an inspection proxy](#behind-a-tls-inspecting-proxy).
- **Optional: build against your installed IDE.** Without doing this, Gradle will download the IntelliJ IDEA version set as
  `platformVersion` in `gradle.properties` (a full IDE distribution, so a large download). To use the IDE you already have, add it to
  `~/.gradle/gradle.properties`:

  ```properties
  platformLocalPath=/Users/you/Applications/IntelliJ IDEA.app
  ```

### 2. Build the plugin

```sh
./gradlew buildPlugin
```

This generates the lexer and parser, compiles, and writes `build/distributions/intellij-nunjucks-<version>.zip`.
To run the tests and the JetBrains compatibility check first:

```sh
./gradlew test verifyPlugin buildPlugin
```

### 3. Install it

1. In IntelliJ, open *Settings | Plugins*.
2. Click ⚙ and choose *Install Plugin from Disk…*.
3. Select `{{repo location}}/build/distributions/intellij-nunjucks-<version>.zip`.

### 4. Update it

Rebuild with `./gradlew buildPlugin` and install the new zip the same way; it replaces the installed copy. Bump 
`pluginVersion` in `gradle.properties` if you want to tell installed versions apart under *Installed*.

### Try changes without installing

```sh
./gradlew runIde
```

This starts a separate sandbox IDE (its own settings and plugins under `.intellijPlatform/sandbox`) with the current
build of the plugin loaded, leaving your normal IDE untouched. Open any project containing templates in it.

### Other tasks

```sh
./gradlew test           # unit tests
./gradlew verifyPlugin   # JetBrains Plugin Verifier against the IDE you build with
```

### Behind a TLS-inspecting proxy

Some corporate proxies might intercept TLS to JetBrains' repositories, and Java doesn't use the macOS keychain, so it rejects the
substituted certificates. Until those hosts are exempted from inspection, build a trust store containing the JDK's
default CAs plus the TLS inspection proxies root cert:

```sh
security find-certificate -c "{{ name of tls inspection proxy root ca }}" -p /Library/Keychains/System.keychain > inspection.pem
cp "$JAVA_HOME/lib/security/cacerts" ~/.gradle/cacerts-tls-inspection
keytool -importcert -noprompt -alias inspection-root -file inspection.pem -keystore ~/.gradle/cacerts-tls-inspection -storepass changeit
```

Then point Gradle at it in `~/.gradle/gradle.properties`, which applies to builds started from a terminal and from IntelliJ:

```properties
systemProp.javax.net.ssl.trustStore=/Users/you/.gradle/cacerts-tls-inspection
systemProp.javax.net.ssl.trustStorePassword=changeit
```

The copied `cacerts` is a snapshot, so recreate it occasionally (after a JDK upgrade) to pick up any new CA changes.

To check whether a host is still intercepted:

```sh
echo | openssl s_client -connect cache-redirector.jetbrains.com:443 -servername cache-redirector.jetbrains.com 2>/dev/null | openssl x509 -noout -issuer
```

You'll see an issuer specifying your corporate TLS proxy if host interception is still active for the jetbrains.com domain.

### Diagnostics against real templates

```sh
# parse every .njk under these directories and fail on any parse error
./gradlew test --tests '*CorpusTest*' -PnunjucksCorpus=/path/to/app/src:/path/to/app/node_modules/govuk-frontend/dist

# reformat each template and print what changes
./gradlew test --tests '*FormatterTest.testFormattedCorpusIsStable' -PnunjucksFormatCorpus=/path/to/app/src -PshowTestOutput

# run every inspection over an app's templates and print the problems
./gradlew test --tests '*RealProjectTest*' -PrealProject=/path/to/app -PshowTestOutput
```

## Licence

MIT, see [LICENSE](LICENSE). Some formatter and file-view code is derived from JetBrains' Handlebars plugin and remains
under the Apache License 2.0; [NOTICE](NOTICE) lists those files and [LICENSE-APACHE-2.0.txt](LICENSE-APACHE-2.0.txt)
has the licence text. All three are packaged in the plugin jar's `META-INF`.
