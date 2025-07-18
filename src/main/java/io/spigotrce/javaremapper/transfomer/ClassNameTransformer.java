package io.spigotrce.javaremapper.transfomer;

import com.google.gson.*;
import io.spigotrce.javaremapper.Main;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class ClassNameTransformer extends AbstractTransformer {
    private final Map<String, String> classNameMap = new HashMap<>();
    private final Map<Integer, String> classNameCache = new HashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String generateRandomName(String originalName) {
        return classNameCache.computeIfAbsent(originalName.hashCode(), k -> {
            String newName;
            do {
                newName = Main.generateString();
            } while (!usedNames.add(newName));
            return newName;
        });
    }

    @Override
    public void obfuscate(String inputPath, String outputPath) throws Exception {
        System.out.println("Class name obfuscation starting...");

        File inputJar = new File(inputPath);
        File outputJar = new File(outputPath);

        try (JarFile jarFile = new JarFile(inputJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

                try (InputStream is = jarFile.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(is);
                    ClassNode node = new ClassNode();
                    reader.accept(node, 0);

                    String className = entry.getName().replace(".class", "");
                    classNameMap.put(className, generateRandomName(className));
                }
            }
        }

        try (JarFile jarFile = new JarFile(inputJar);
             FileOutputStream fos = new FileOutputStream(outputJar);
             JarOutputStream jos = new JarOutputStream(fos)) {

            Enumeration<JarEntry> entries = jarFile.entries();
            SimpleRemapper remapper = new SimpleRemapper(classNameMap);

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                InputStream is = jarFile.getInputStream(entry);

                if (entry.getName().endsWith(".class")) {
                    ClassReader cr = new ClassReader(is);
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassRemapper classRemapper = new ClassRemapper(cw, remapper);
                    cr.accept(classRemapper, 0);

                    String originalName = entry.getName().replace(".class", "");
                    String newName = remapper.mapType(originalName);

                    jos.putNextEntry(new JarEntry(newName + ".class"));
                    jos.write(cw.toByteArray());
                } else {
                    byte[] originalBytes = is.readAllBytes();
                    byte[] updatedBytes = remapMetadata(entry.getName(), originalBytes);
                    jos.putNextEntry(new JarEntry(entry.getName()));
                    jos.write(updatedBytes);
                }
                jos.closeEntry();
            }
        }

        System.out.println("Class name obfuscation completed.");
    }

    private byte[] remapMetadata(String name, byte[] content) {
        try {
            String text = new String(content, StandardCharsets.UTF_8);

            // Velocity plugin remap
            if (name.endsWith("velocity-plugin.json")) {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                if (json.has("main")) {
                    String original = json.get("main").getAsString().replace('.', '/');
                    String remapped = classNameMap.getOrDefault(original, original).replace('/', '.');
                    json.addProperty("main", remapped);
                }
                return gson.toJson(json).getBytes(StandardCharsets.UTF_8);
            }

            // Fabric remap
            if (name.endsWith(".json")) {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();

                if (name.toLowerCase().endsWith(".mixins.json")) {
                    for (String section : List.of("mixins", "client", "server", "main")) {
                        if (json.has(section) && json.get(section).isJsonArray()) {
                            JsonArray arr = json.getAsJsonArray(section);
                            for (int i = 0; i < arr.size(); i++) {
                                String original = arr.get(i).getAsString().replace('.', '/');
                                original = (json.get("package").getAsString() + "." + original).replace(".", "/");
                                String remapped = classNameMap.getOrDefault(original, original).replace('/', '.');
                                arr.set(i, new JsonPrimitive(remapped));
                            }
                        }
                    }

                    if (json.has("package")) {
                        json.addProperty("package", "");
                    }

                    return gson.toJson(json).getBytes(StandardCharsets.UTF_8);
                }

                if (name.endsWith("fabric.mod.json")) {
                    if (json.has("entrypoints")) {
                        JsonObject ep = json.getAsJsonObject("entrypoints");
                        for (Map.Entry<String, JsonElement> entry : ep.entrySet()) {
                            JsonArray arr = entry.getValue().getAsJsonArray();
                            for (int i = 0; i < arr.size(); i++) {
                                JsonElement e = arr.get(i);
                                if (e.isJsonPrimitive()) {
                                    String ref = e.getAsString().replace('.', '/');
                                    String newRef = classNameMap.getOrDefault(ref, ref).replace('/', '.');
                                    arr.set(i, new JsonPrimitive(newRef));
                                } else if (e.isJsonObject()) {
                                    JsonObject obj = e.getAsJsonObject();
                                    if (obj.has("value")) {
                                        String ref = obj.get("value").getAsString().replace('.', '/');
                                        String newRef = classNameMap.getOrDefault(ref, ref).replace('/', '.');
                                        obj.addProperty("value", newRef);
                                    }
                                }
                            }
                        }
                    }

                    return gson.toJson(json).getBytes(StandardCharsets.UTF_8);
                }

                if (json.has("mappings")) {
                    JsonObject mappings = json.getAsJsonObject("mappings");
                    JsonObject newMappings = new JsonObject();

                    for (Map.Entry<String, JsonElement> entry : mappings.entrySet()) {
                        String original = entry.getKey();
                        String remapped = classNameMap.getOrDefault(original, original);
                        newMappings.add(remapped, entry.getValue());
                    }

                    json.add("mappings", newMappings);
                }

                if (json.has("data")) {
                    JsonObject data = json.getAsJsonObject("data");
                    for (Map.Entry<String, JsonElement> dataEntry : data.entrySet()) {
                        JsonObject mappings = dataEntry.getValue().getAsJsonObject();
                        JsonObject newMappings = new JsonObject();

                        for (Map.Entry<String, JsonElement> entry : mappings.entrySet()) {
                            String original = entry.getKey();
                            String remapped = classNameMap.getOrDefault(original, original);
                            newMappings.add(remapped, entry.getValue());
                        }

                        data.add(dataEntry.getKey(), newMappings);
                    }
                }

                return gson.toJson(json).getBytes(StandardCharsets.UTF_8);
            }

            // YAML remapping for plugin.yml and bungee.yml
            if (name.endsWith("plugin.yml") || name.endsWith("bungee.yml")) {
                StringBuilder updated = new StringBuilder();
                String[] lines = text.split("\n");
                for (String line : lines) {
                    if (line.trim().toLowerCase().startsWith("main:") || line.trim().toLowerCase().startsWith("injector:")) {
                        String[] parts = line.split(":");
                        if (parts.length >= 2) {
                            String key = parts[0];
                            String original = parts[1].trim().replace('.', '/');
                            String remapped = classNameMap.getOrDefault(original, original).replace('/', '.');
                            updated.append(key).append(": ").append(remapped).append("\n");
                            continue;
                        }
                    }
                    updated.append(line).append("\n");
                }
                return updated.toString().getBytes(StandardCharsets.UTF_8);
            }

        } catch (Exception e) {
            System.err.println("[WARN] Failed to remap metadata: " + name + " - " + e.getMessage());
        }

        return content;
    }
}
