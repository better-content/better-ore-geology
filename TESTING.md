# Local verification

Use Java 17 and the repository wrapper. The deterministic and resource verification lane is:

```sh
TMPDIR="$HOME/.tmp" ./gradlew verifyFull --max-workers=2
```

The separate native surface-sample GameTest is available with:

```sh
TMPDIR="$HOME/.tmp" ./gradlew runGameTestServer --max-workers=2
```

`verifyFull` includes `verifyFast` and the repository's resource, texture, exposure, optional-data, and worldgen-generator checks. It does not depend on `runGameTestServer`. The latter uses a nonshipping fixture in a fresh `build/gametest/<UUID>` directory and checks its native log for one completed required test. The fixture invokes the registered sample block's `use` method, checking denied collection, dry and waterlogged replacement, a matching dropped chunk, inventory preservation, sampled-family event, and unchanged loaded-chunk count. It does not establish natural deposit generation or client presentation. These are available owner commands; this note records no execution result.
