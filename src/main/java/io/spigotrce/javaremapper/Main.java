package io.spigotrce.javaremapper;

import java.io.*;

public class Main {

    public static void main(String[] args) throws IOException {
        System.out.println("Staring JavaRemapper...");
        System.out.println("Version: " + Main.class.getPackage().getImplementationVersion());
        if (args.length != 2) {
            System.out.println("Usage: java -jar JavaRemapper.jar <input-jar> <output-jar>");
            return;
        }
        File inputJar = new File(args[0]);
        File outputJar = new File(args[1]);

        if (!inputJar.exists() || !inputJar.isFile()) {
            System.out.println("Input jar file does not exist or is not a file: " + inputJar.getAbsolutePath());
            return;
        }
    }
}
