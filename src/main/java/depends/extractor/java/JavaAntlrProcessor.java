package depends.extractor.java;

import depends.extractor.FileParser;

public class JavaAntlrProcessor extends JavaProcessor {
    private static final String JAVA_ANTLR_LANG = "java-antlr";

    @Override
    public String supportedLanguage() {
        return JAVA_ANTLR_LANG;
    }

    @Override
    public FileParser createFileParser() {
        return new JavaFileParser(entityRepo, bindingResolver);
    }
}
