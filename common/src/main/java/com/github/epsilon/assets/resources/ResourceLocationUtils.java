package com.github.epsilon.assets.resources;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import static com.github.epsilon.Constants.mc;

public class ResourceLocationUtils {

    public static ResourceLocation getIdentifier(String path) {
        return ResourceLocation.fromNamespaceAndPath("epsilon", path);
    }

    public static ByteBuffer loadResource(ResourceLocation identifier) {
        final var manager = mc.getResourceManager();
        Optional<Resource> resource = manager.getResource(identifier);

        if (resource.isEmpty()) {
            throw new RuntimeException("Couldn't find resource at " + identifier);
        }

        try (InputStream is = resource.get().open()) {
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

}
