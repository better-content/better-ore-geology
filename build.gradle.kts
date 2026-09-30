import javax.xml.parsers.DocumentBuilderFactory

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

        create("client") {
            property("forge.enabledGameTestNamespaces", modId)
        }

        create("server") {
            property("forge.enabledGameTestNamespaces", modId)
            arg("--nogui")
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

tasks.register("verifyFull") {
    group = "verification"
    description = "Runs the full verification lane for this repo."
    dependsOn(tasks.named("verifyFast"))
    dependsOn(verifyItemTextures)
    dependsOn(verifyBlockMasters)
    dependsOn(verifyBlockTextures)
}

jacoco {
    toolVersion = "0.8.12"
}

val coveredModelClasses = listOf(
    "com.bettercontent.betteroregeology.ore.OreDefinition",
    "com.bettercontent.betteroregeology.ore.OreDefinition\$VariantDefinition",
    "com.bettercontent.betteroregeology.ore.OreDefinition\$TextureMode",
    "com.bettercontent.betteroregeology.ore.DisabledFeaturesDefinition"
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
