package com.alechilles.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconStandalonePackagingIT {

    @Test
    void producedJarContainsBeaconIdentityMetadataAndUsableUiAssets() throws Exception {
        String version = standaloneVersion();
        Path jarPath = packagedJar(version);
        assertTrue(Files.isRegularFile(jarPath), "the produced Beacon JAR must exist");

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest jarManifest = jar.getManifest();
            assertNotNull(jarManifest, "the produced JAR must have a manifest");
            assertEquals("com.alechilles", jarManifest.getMainAttributes().getValue("Plugin-Group"));
            assertEquals("beacon", jarManifest.getMainAttributes().getValue("Plugin-Name"));
            assertEquals(version, jarManifest.getMainAttributes().getValue("Plugin-Version"));

            String manifest = entryText(jar, "manifest.json");
            assertEquals("Alechilles", jsonString(manifest, "Group"));
            assertEquals("Beacon", jsonString(manifest, "Name"));
            assertEquals(version, jsonString(manifest, "Version"));
            assertEquals("https://beacon.modstats.io", jsonString(manifest, "Website"));
            assertEquals("com.alechilles.beacon.Beacon", jsonString(manifest, "Main"));

            assertMavenProperties(jar, "META-INF/maven/com.alechilles/beacon/pom.properties",
                    "com.alechilles", "beacon", version);
            assertMavenProperties(jar, "META-INF/maven/com.alechilles/beacon-runtime/pom.properties",
                    "com.alechilles", "beacon-runtime", version);

            String beaconLicense = entryText(jar, "META-INF/LICENSES/LICENSE");
            assertTrue(beaconLicense.startsWith("Beacon Runtime License 1.0"));

            String credits = entryText(jar, "Server/Credits/Beacon.json");
            assertEquals("Alechilles:Beacon", jsonString(credits, "Plugin"));

            assertImage(jar, "Common/UI/Custom/BeaconLogo.png");
            assertImage(jar, "Common/UI/Custom/icon-256.png", 256, 256);
            assertImage(jar, "Common/UI/Custom/TelemetryConsentHeader.png");
            assertImage(jar, "icon-256.png", 256, 256);
        }
    }

    private static String standaloneVersion() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = BeaconStandalonePackagingIT.class.getClassLoader()
                .getResourceAsStream("META-INF/maven/com.alechilles/beacon/pom.properties")) {
            assertNotNull(input, "standalone version metadata must be available");
            properties.load(input);
        }
        return properties.getProperty("version");
    }

    private static Path packagedJar(String version) throws URISyntaxException {
        Path testClasses = Path.of(BeaconStandalonePackagingIT.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path mavenJar = testClasses.getParent().resolve("Beacon v" + version + ".jar");
        Path gradleJar = testClasses.resolve("../../..").normalize().resolve("libs/Beacon v" + version + ".jar");
        return Files.isRegularFile(mavenJar) ? mavenJar : gradleJar;
    }

    private static String entryText(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path + " must be packaged in the standalone JAR");
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String jsonString(String json, String key) {
        var value = JsonParser.parseString(json).getAsJsonObject().get(key);
        assertNotNull(value, "manifest must contain a property named " + key);
        return value.getAsString();
    }

    private static void assertMavenProperties(JarFile jar, String path, String groupId, String artifactId,
                                              String version) throws IOException {
        Properties properties = new Properties();
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path + " must be packaged in the standalone JAR");
        try (InputStream input = jar.getInputStream(entry)) {
            properties.load(input);
        }
        assertEquals(groupId, properties.getProperty("groupId"));
        assertEquals(artifactId, properties.getProperty("artifactId"));
        assertEquals(version, properties.getProperty("version"));
    }

    private static void assertImage(JarFile jar, String path, int width, int height) throws IOException {
        BufferedImage image = assertImage(jar, path);
        assertEquals(width, image.getWidth(), path + " width");
        assertEquals(height, image.getHeight(), path + " height");
    }

    private static BufferedImage assertImage(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path + " must be packaged in the standalone JAR");
        try (InputStream input = jar.getInputStream(entry)) {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path + " must be a readable image");
            assertTrue(image.getColorModel().hasAlpha(), path + " must retain alpha");
            return image;
        }
    }
}
