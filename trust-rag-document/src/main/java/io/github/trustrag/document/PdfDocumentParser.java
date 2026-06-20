package io.github.trustrag.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename, String contentType) {
        return "pdf".equals(ParserSupport.extension(filename))
                || "application/pdf".equalsIgnoreCase(contentType);
    }

    @Override
    public List<ParsedDocumentSection> parse(
            Path path,
            String filename,
            String contentType,
            String sourceUrl) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) {
                throw new IOException("Encrypted PDF documents are not supported");
            }
            PDDocumentInformation information = document.getDocumentInformation();
            String sourceTitle = information == null ? null : information.getTitle();
            if (sourceTitle == null || sourceTitle.isBlank()) {
                sourceTitle = ParserSupport.fallbackTitle(filename);
            }
            ParserSupport.Sections result = new ParserSupport.Sections(sourceTitle, sourceUrl);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = ParserSupport.normalizeText(stripper.getText(document));
                if (!text.isBlank()) {
                    String section = "Page " + page;
                    result.begin(sourceTitle + " - " + section, section, page);
                    result.append(text);
                }
            }
            return result.finish();
        }
    }
}
