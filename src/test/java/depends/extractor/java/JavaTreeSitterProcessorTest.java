package depends.extractor.java;

import depends.LangRegister;
import depends.extractor.FileParser;
import depends.extractor.LangProcessorRegistration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JavaTreeSitterProcessorTest {
    @Test
    public void should_register_java_ts_language() {
        new LangRegister();
        assertNotNull(LangProcessorRegistration.getRegistry().getProcessorOf("java-ts"));
    }

    @Test
    public void should_report_java_ts_as_supported_language() {
        JavaTreeSitterProcessor processor = new JavaTreeSitterProcessor();
        assertEquals("java-ts", processor.supportedLanguage());
    }

    @Test
    public void should_create_java_ts_file_parser() {
        JavaTreeSitterProcessor processor = new JavaTreeSitterProcessor();
        FileParser parser = processor.createFileParser();
        assertTrue(parser instanceof JavaTreeSitterFileParser);
    }

    @Test
    public void should_use_tree_sitter_for_java_language() {
        JavaProcessor processor = new JavaProcessor();
        assertEquals("java", processor.supportedLanguage());
        assertTrue(processor.createFileParser() instanceof JavaTreeSitterFileParser);
    }

    @Test
    public void should_keep_antlr_java_parser_available() {
        new LangRegister();
        assertNotNull(LangProcessorRegistration.getRegistry().getProcessorOf("java-antlr"));
        JavaAntlrProcessor processor = new JavaAntlrProcessor();
        assertEquals("java-antlr", processor.supportedLanguage());
        assertTrue(processor.createFileParser() instanceof JavaFileParser);
    }
}
