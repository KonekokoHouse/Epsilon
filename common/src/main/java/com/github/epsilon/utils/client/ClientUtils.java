package com.github.epsilon.utils.client;

import net.minecraft.client.ResourceLoadStateTracker;

import java.io.*;

import static com.github.epsilon.Constants.mc;

public class ClientUtils {

    public static boolean isLoading() {
        ResourceLoadStateTracker.ReloadState state = mc.reloadStateTracker.reloadState;
        return state == null || !state.finished;
    }

    public static boolean loadNativeLibrary(String path,String name) {
        try (InputStream in = ClientUtils.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new FileNotFoundException("DLL not found: " + path);
            }

            File tempDll = File.createTempFile(name, ".dll");
            tempDll.deleteOnExit();

            try (OutputStream out = new FileOutputStream(tempDll)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
h
            System.load(tempDll.getAbsolutePath());
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load audio meter DLL", e);
        }
    }

}
