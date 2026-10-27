# Local verification

Use Java 17 and the repository wrapper. No modpack checkout or bundled pack artifact is needed by the surface-sample fixture.

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.17-tem TMPDIR=$HOME/.tmp ./gradlew verifyFull --max-workers=2
JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.17-tem TMPDIR=$HOME/.tmp ./gradlew runGameTestServer --max-workers=2
```

`verifyFull` retains the existing JVM/resource/generator checks, including the processing recipe graph. `runGameTestServer` is the isolated native surface-sample regression. Its fixture source set is not shipped in the production JAR. Every invocation owns a fresh `build/gametest/<UUID>` directory and verifies one completed required test in its native log.

The native fixture invokes the registered sample block's actual `use` method, checking denied build permission, dry/waterlogged replacement, one matching dropped small chunk, unchanged player inventory, the sampled-family event (not extraction), and unchanged loaded-chunk count. This establishes that collecting a sample does not load chunks looking for a deposit. It does not establish that no reads of already-loaded chunks occurred, nor does it prove natural deposit generation/detection or client presentation.
