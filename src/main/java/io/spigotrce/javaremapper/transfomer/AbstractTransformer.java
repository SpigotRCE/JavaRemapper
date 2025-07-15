package io.spigotrce.javaremapper.transfomer;

public abstract class AbstractTransformer {
    public abstract void obfuscate(String inputPath, String outputPath) throws Exception;
}
