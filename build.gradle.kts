import javax.xml.parsers.DocumentBuilderFactory
import java.util.UUID

plugins {
    jacoco
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.spongepowered.mixin") version "0.7.38"
    java
}

val minecraftVersion = project.property("minecraft_version") as String
val forgeVersion = project.property("forge_version") as String
val modId = project.property("mod_id") as String
val modName = project.property("mod_name") as String
val modVersion = project.property("mod_version") as String

group = project.property("mod_group") as String
version = modVersion

base {
    archivesName.set("better-ore-geology")
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

mixin { config("better_ore_geology.mixins.json") }

// Native review fixture is never included in the production JAR.
val exposureHarness by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output
}
configurations[exposureHarness.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[exposureHarness.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

val gameTestFixtures by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output
}
configurations[gameTestFixtures.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[gameTestFixtures.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

val sampleGameTestDirectory = layout.buildDirectory.dir("gametest/${UUID.randomUUID()}")

minecraft {
    mappings("official", minecraftVersion)

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        val baseClient = create("client") {
            property("forge.enabledGameTestNamespaces", modId)
        }

        val baseServer = create("server") {
            property("forge.enabledGameTestNamespaces", modId)
            arg("--nogui")
        }

        create("gameTestServer") {
            workingDirectory(sampleGameTestDirectory.get().asFile)
            property("forge.enabledGameTestNamespaces", modId)
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            arg("--nogui")
            jvmArgs("-Xmx2G", "-XX:ActiveProcessorCount=2")
            mods { named(modId) { source(gameTestFixtures) } }
        }

        create("exposureServer") {
            parent(baseServer)
            workingDirectory(project.file("run-exposure/server"))
            property("better_ore_geology.exposureProbe", "true")
            jvmArgs("-Xmx3G", "-XX:ActiveProcessorCount=4")
            arg("--nogui")
            mods { create("better_ore_exposure_probe") { source(exposureHarness) } }
        }
        create("exposureClient") {
            parent(baseClient)
            workingDirectory(project.file("run-exposure/client"))
            jvmArgs("-Xmx4G", "-XX:ActiveProcessorCount=4")
            args("--quickPlayMultiplayer", "127.0.0.1:25585", "--width", "1280", "--height", "900")
            mods { create("better_ore_exposure_probe") { source(exposureHarness) } }
        }
        create("data") {
            args(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath
            )
        }
    }
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(fg.deobf("curse.maven:excavated-variants-577411:5166315"))
    compileOnly(fg.deobf("curse.maven:dynamic-asset-generator-577096:5008421"))
    add(exposureHarness.implementationConfigurationName, fg.deobf("curse.maven:excavated-variants-577411:5166315"))
    add(exposureHarness.implementationConfigurationName, fg.deobf("curse.maven:dynamic-asset-generator-577096:5008421"))
    add(exposureHarness.runtimeOnlyConfigurationName, fg.deobf("curse.maven:unearthed-406825:7742307"))
    add(exposureHarness.runtimeOnlyConfigurationName, fg.deobf("curse.maven:hyle-609850:7736352"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

tasks.processResources {
    val props = mapOf(
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "minecraftVersion" to minecraftVersion,
        "forgeVersion" to forgeVersion
    )
    inputs.properties(props)
    filesMatching("META-INF/mods.toml") {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.register("prepareExposureLaunch") {
    group = "verification"
    description = "Prepares nonshipping native probes and exports direct Java launches (no concurrent Gradle runtimes)."
    dependsOn("prepareRunExposureServer", "prepareRunExposureClient", "prepareRunServer", "prepareRunClient", "exposureHarnessClasses")
    doLast {
        for (side in listOf("Server", "Client", "StandaloneServer", "StandaloneClient")) {
            val baseSide = side.removePrefix("Standalone")
            val taskName = if (side.startsWith("Standalone")) "run$baseSide" else "runExposure$side"
            val run = tasks.named<JavaExec>(taskName).get()
            // ForgeGradle assembles JavaExec inputs only inside exec(); use its pinned token resolver
            // to export the same launch without holding a Gradle invocation open beside Minecraft.
            val generator = net.minecraftforge.gradle.common.util.runs.RunConfigGenerator::class.java
            fun resolve(name: String, vararg arguments: Any): Any {
                val method = generator.declaredMethods.single { it.name == name && it.parameterCount == arguments.size }
                method.isAccessible = true
                return requireNotNull(method.invoke(null, *arguments))
            }
            val config = (run.javaClass.getMethod("getRunConfig").invoke(run)
                as org.gradle.api.provider.Property<net.minecraftforge.gradle.common.util.RunConfig>).get()
            if (config.main == null) config.mergeParents()
            check(config.main != null) { "Missing native main for ${config.name}; parents=${config.parents.map { it.name + ":" + it.main }}; base=${minecraft.runs.getByName(baseSide.lowercase()).main}" }
            val tokens = resolve("configureTokensLazy", project, config,
                resolve("mapModClassesToGradle", project, config),
                resolve("getMinecraftArtifacts", project), resolve("getRuntimeClasspathArtifacts", project)) as Map<String, *>
            val gameArgs = (resolve("getArgsStream", config, tokens, false) as java.util.stream.Stream<*>).map { it.toString() }.toList()
            val additional = if (side.endsWith("Client")) {
                val getter = run.javaClass.getMethod("getAdditionalClientArgs")
                (getter.invoke(run) as org.gradle.api.provider.ListProperty<*>).get().map { config.replace(tokens, it.toString()) }
            } else emptyList()
            val properties = config.properties.map { (name, value) -> "-D$name=${config.replace(tokens, value)}" }
            val classpath = files(config.allSources.map { it.runtimeClasspath }).asPath
            val command = listOf(run.javaLauncher.get().executablePath.asFile.absolutePath) +
                run.allJvmArgs + config.jvmArgs.map { config.replace(tokens, it) } + additional + properties +
                listOf("-cp", classpath, config.main) + gameArgs
            val output = layout.buildDirectory.file("exposure-launch/${side.lowercase()}.json").get().asFile
            output.parentFile.mkdirs()
            output.writeText(groovy.json.JsonOutput.toJson(mapOf(
                "command" to command, "cwd" to config.workingDirectory,
                "environment" to config.environment.mapValues { config.replace(tokens, it.value) }
            )))
        }
    }
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar into build/libs using the canonical release filename."
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

tasks.named("assemble") {
    dependsOn(stageRuntimeJar)
}

tasks.test {
    useJUnitPlatform()
    inputs.files(
        layout.projectDirectory.file("tools/ore_art_manifest.json"),
        layout.projectDirectory.file("tools/geological_worldgen.json")
    )
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Runs deterministic unit/resource checks without Forge game tests."
    dependsOn(tasks.named("check"))
}

tasks.register<JavaExec>("renderWorldgenGallery") {
    group = "documentation"
    description = "Renders one geological worldgen review sheet per family archetype."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.bettercontent.betteroregeology.worldgen.DepositMorphologyGallery")
    args(layout.buildDirectory.dir("worldgen-gallery").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true")
}

val verifyItemTextures by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies curated processing sprites against their 1024px masters."
    commandLine(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }.get().executablePath.asFile.absolutePath,
        "tools/DownsampleItemTextures.java",
        "--check"
    )
}

val verifyBlockMasters by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the complete geology-v7 standalone alpha master suite."
    commandLine(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }.get().executablePath.asFile.absolutePath,
        "tools/GenerateDepositTextures.java",
        "--validate-masters"
    )
}

val verifyExposureMasters by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates all 96 approved independent exposure masters."
    commandLine(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
        .get().executablePath.asFile.absolutePath, "tools/GenerateDepositTextures.java", "--validate-exposure-masters")
}

val verifyExposureResources by tasks.registering(Exec::class) {
    group = "verification"
    description = "Rejects drift in native exposure blockstates and models."
    commandLine("python3", "-B", "tools/generate_exposure_models.py", "--check")
}

val verifyWorldgenGenerator by tasks.registering(Exec::class) {
    group = "verification"
    description = "Tests worldgen output ownership and optional-biome selector equivalence."
    commandLine("python3", "-B", "tools/test_generate_geological_worldgen.py")
}

val verifyExposureDiagnostics by tasks.registering(Exec::class) {
    group = "verification"
    description = "Tests strict isolated-native diagnostic classification and rejection boundaries."
    commandLine("python3", "-B", "tools/test_exposure_diagnostics.py")
}

val verifyOptionalData by tasks.registering(Exec::class) {
    group = "verification"
    description = "Rejects drift in optional-mod recipe and tag guards."
    commandLine("python3", "-B", "tools/generate_phase3_resources.py", "--check-optional-data")
}

val verifyBlockTextures by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies all shipped block textures against the approved geology-v7 masters."
    commandLine(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }.get().executablePath.asFile.absolutePath,
        "tools/GenerateDepositTextures.java",
        "--check"
    )
}

tasks.matching { it.name == "runGameTestServer" }.configureEach {
    doFirst {
        val directory = sampleGameTestDirectory.get().asFile
        directory.mkdirs()
        directory.resolve("server.properties").writeText("# Isolated surface sample fixture\n")
        copy {
            from("src/gameTestFixtures/resources/gameteststructures")
            into(directory.resolve("gameteststructures"))
        }
    }
    doLast {
        val log = sampleGameTestDirectory.get().file("logs/latest.log").asFile.readText()
        check(log.contains("All 1 required tests passed") && log.contains("1 GAME TESTS COMPLETE")) {
            "Missing surface-sample native completion in ${sampleGameTestDirectory.get()}"
        }
    }
}

tasks.register("verifyFull") {
    group = "verification"
    description = "Runs the full verification lane for this repo."
    dependsOn(tasks.named("verifyFast"))
    dependsOn(verifyItemTextures)
    dependsOn(verifyBlockMasters)
    dependsOn(verifyBlockTextures)
    dependsOn(verifyExposureMasters, verifyExposureResources, verifyOptionalData, verifyExposureDiagnostics, verifyWorldgenGenerator)
}

jacoco {
    toolVersion = "0.8.12"
}

val coveredModelClasses = listOf(
    "com.bettercontent.betteroregeology.ore.OreDefinition",
    "com.bettercontent.betteroregeology.ore.OreDefinition\$VariantDefinition",
    "com.bettercontent.betteroregeology.ore.OreDefinition\$TextureMode",
    "com.bettercontent.betteroregeology.ore.DisabledFeaturesDefinition",
    "com.bettercontent.betteroregeology.visual.ExposureField",
    "com.bettercontent.betteroregeology.compat.exposure.ExcavatedExposureModels",
    "com.bettercontent.betteroregeology.compat.exposure.ExposureComposite"
)
val coveredModelFiles = sourceSets.main.get().output.classesDirs.asFileTree.matching {
    include(coveredModelClasses.map { it.replace('.', '/') + ".class" })
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(coveredModelFiles)
}

val verifyCoverageInputs by tasks.registering {
    group = "verification"
    description = "Rejects missing model classes or missing JaCoCo execution data."
    dependsOn(tasks.test)
    doLast {
        val selected = tasks.jacocoTestReport.get().classDirectories.asFileTree.files
        val missing = coveredModelClasses.filter { name ->
            selected.none { it.invariantSeparatorsPath.endsWith(name.replace('.', '/') + ".class") }
        }
        check(missing.isEmpty()) { "Coverage selection is missing expected classes: $missing" }
        val execution = tasks.test.get().extensions.getByType<JacocoTaskExtension>().destinationFile
        check(execution != null && execution.isFile && execution.length() > 0) {
            "Coverage execution data is missing or empty"
        }
    }
}

tasks.jacocoTestReport { dependsOn(verifyCoverageInputs) }

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(tasks.jacocoTestReport.map { it.classDirectories })
    doFirst {
        val xml = tasks.jacocoTestReport.get().reports.xml.outputLocation.get().asFile
        check(xml.isFile) { "Coverage report is missing" }
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val document = factory.newDocumentBuilder().parse(xml)
        val classes = document.getElementsByTagName("class")
        val executableClasses = (0 until classes.length).mapNotNull { index ->
            val klass = classes.item(index) as org.w3c.dom.Element
            val counters = klass.childNodes
            val executable = (0 until counters.length).any { child ->
                val counter = counters.item(child) as? org.w3c.dom.Element
                counter?.tagName == "counter" && counter.getAttribute("type") == "LINE" &&
                    counter.getAttribute("missed").toLong() + counter.getAttribute("covered").toLong() > 0
            }
            if (executable) klass.getAttribute("name").replace('/', '.') else null
        }
        check(executableClasses.containsAll(coveredModelClasses)) {
            "Coverage report is missing executable counters for ${coveredModelClasses - executableClasses.toSet()}"
        }
    }
    violationRules {
        rule {
            element = "CLASS"
            includes = coveredModelClasses
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
