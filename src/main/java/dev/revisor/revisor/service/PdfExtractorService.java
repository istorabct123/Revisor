package dev.revisor.revisor.service;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.File;
import java.io.FileInputStream;

public class PdfExtractorService {

    public static String extractText(File file) {
        try (FileInputStream input = new FileInputStream(file)) {

            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(5_000_000); // SEM LIMITE
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(input, handler, metadata, context);

            return handler.toString();

        } catch (Exception e) {
            throw new RuntimeException("Erro ao extrair texto do PDF", e);
        }
    }
}