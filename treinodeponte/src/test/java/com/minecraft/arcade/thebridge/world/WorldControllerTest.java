package com.minecraft.arcade.thebridge.world;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class WorldControllerTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void usesNestedWorldBeforeOtherTemplates() throws Exception {
        File source = temporary.newFolder("map");
        File container = temporary.newFolder("server");
        Path nested = Files.createDirectory(source.toPath().resolve("world"));
        Files.write(nested.resolve("level.dat"), new byte[]{1});
        Files.write(source.toPath().resolve("level.dat"), new byte[]{2});
        Path named = Files.createDirectory(container.toPath().resolve("template"));
        Files.write(named.resolve("level.dat"), new byte[]{3});

        assertEquals(nested.toRealPath(), WorldController.resolveTemplate(source, container, "template"));
    }

    @Test
    public void acceptsWorldAlongsideMapConfig() throws Exception {
        File source = temporary.newFolder("map");
        File container = temporary.newFolder("server");
        Files.write(source.toPath().resolve("level.dat"), new byte[]{1});

        assertEquals(source.toPath().toRealPath(), WorldController.resolveTemplate(source, container, "template"));
    }

    @Test
    public void acceptsNamedServerWorld() throws Exception {
        File source = temporary.newFolder("map");
        File container = temporary.newFolder("server");
        Path named = Files.createDirectory(container.toPath().resolve("template"));
        Files.write(named.resolve("level.dat"), new byte[]{1});

        assertEquals(named.toRealPath(), WorldController.resolveTemplate(source, container, "template"));
    }

    @Test
    public void allowsSlimeFallbackOnlyWhenNoStandardTemplateExists() throws Exception {
        File source = temporary.newFolder("map");
        File container = temporary.newFolder("server");

        assertNull(WorldController.resolveTemplate(source, container, "slime-template"));
    }

    @Test
    public void rejectsIncompleteStandardWorld() throws Exception {
        File source = temporary.newFolder("map");
        File container = temporary.newFolder("server");
        Files.createDirectory(source.toPath().resolve("world"));

        assertThrows(IOException.class, () -> WorldController.resolveTemplate(source, container, "template"));
    }

    @Test
    public void rejectsTemplatesOutsideServerContainer() throws Exception {
        File source = temporary.newFolder("map");
        File container = temporary.newFolder("server");

        assertThrows(IOException.class, () -> WorldController.resolveTemplate(source, container, "../outside"));
        assertThrows(IOException.class, () -> WorldController.resolveTemplate(source, container, "."));
    }

    @Test
    public void copiesRegionsWithoutIdentityOrLocksAndPreservesTemplate() throws Exception {
        Path source = temporary.newFolder("template").toPath();
        Path destination = temporary.newFolder("arena").toPath();
        byte[] region = new byte[]{4, 3, 2, 1};
        Files.write(source.resolve("level.dat"), new byte[]{1});
        Files.write(source.resolve("uid.dat"), new byte[]{2});
        Files.write(source.resolve("session.lock"), new byte[]{3});
        Files.createDirectory(source.resolve("region"));
        Files.write(source.resolve("region/r.0.0.mca"), region);

        WorldController.copyTemplate(source, destination);

        assertArrayEquals(region, Files.readAllBytes(destination.resolve("region/r.0.0.mca")));
        assertTrue(Files.exists(destination.resolve("level.dat")));
        assertFalse(Files.exists(destination.resolve("uid.dat")));
        assertFalse(Files.exists(destination.resolve("session.lock")));
        Files.write(destination.resolve("region/r.0.0.mca"), new byte[]{9});
        assertArrayEquals(region, Files.readAllBytes(source.resolve("region/r.0.0.mca")));
        assertTrue(Files.exists(source.resolve("uid.dat")));
        assertTrue(Files.exists(source.resolve("session.lock")));
    }

    @Test
    public void standardWorldSupportLoadsWithoutSlimeClasses() throws Exception {
        assertNotNull(Class.forName(WorldController.class.getName()).getDeclaredMethods());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.grinderwolf.swm.plugin.SWMPlugin"));
    }
}
