package com.github.epsilon.graphics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LuminFontShaderContractTest {
    private static final Path SHADER_ROOT = Path.of("src/main/resources/assets/epsilon/shaders");

    @Test
    void minecraftFontPipelinesTreatHighSdfValuesAsCoveredPixels() throws IOException {
        for (String name : new String[] {"ttf_font_aa.fsh", "ttf_font_no_aa.fsh"}) {
            String shader = Files.readString(SHADER_ROOT.resolve(name));
            assertTrue(shader.contains("texture(Sampler0"), name + " must sample the glyph atlas");
            assertFalse(shader.contains("1.0 - texture(Sampler0"),
                    name + " must not turn the zero-valued SDF atlas background opaque");
        }
    }
}
