package dev.revisor.revisor.service;

import org.apache.tika.Tika;
import java.io.File;

public class PdfExtractorService {

    private static final Tika tika = new Tika();

    public static String extractText(File file) {
        try {
            return tika.parseToString(file);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao extrair texto do PDF", e);
        }
    }
}