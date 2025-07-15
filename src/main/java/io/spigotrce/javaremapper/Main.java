package io.spigotrce.javaremapper;

import io.spigotrce.javaremapper.transfomer.ClassNameTransformer;

import java.io.*;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

public class Main {
    public static final Random RANDOM = new SecureRandom();
    public static final String GREEK_SET = "αβγδεζηθικλμνξοπρστυφχψω";

    public static void main(String[] args) throws Exception {
        System.out.println("Staring JavaRemapper...");
        System.out.println("Version: " + Main.class.getPackage().getImplementationVersion());
        if (args.length != 1) {
            System.out.println("Usage: java -jar JavaRemapper.jar <input-jar>");
            return;
        }
        File inputJar = new File(args[0]);
        File outputJar = new File(args[0] + "-out.jar");

        if (!inputJar.exists() || !inputJar.isFile()) {
            System.out.println("Input jar file does not exist or is not a file: " + inputJar.getAbsolutePath());
            return;
        }

        new ClassNameTransformer().obfuscate(inputJar.getAbsolutePath(), outputJar.getAbsolutePath());
    }

    public static String generateString() {
        int length = RANDOM.nextInt(10) + 20;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(GREEK_SET.charAt(RANDOM.nextInt(GREEK_SET.length())));
        }
        return sb.toString();
    }

    public static String generateAlpha() {
        return "a" + UUID.randomUUID().toString().replace("-", "");
    }
}
