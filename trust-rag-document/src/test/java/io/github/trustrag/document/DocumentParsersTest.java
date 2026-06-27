package io.github.trustrag.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DocumentParsers 的关键行为、边界条件和回归场景。
 */
class DocumentParsersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsPdfByPage() throws Exception {
        Path path = temporaryDirectory.resolve("guide.pdf");
        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle("PDF Guide");
            document.setDocumentInformation(information);
            addPdfPage(document, "First page");
            addPdfPage(document, "Second page");
            document.save(path.toFile());
        }

        List<ParsedDocumentSection> sections =
                new PdfDocumentParser().parse(path, "guide.pdf", "application/pdf", "file://guide.pdf");

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).sourceTitle()).isEqualTo("PDF Guide");
        assertThat(sections.get(0).pageNumber()).isEqualTo(1);
        assertThat(sections.get(1).content()).contains("Second page");
    }

    @Test
    void extractsWordHeadingStructure() throws Exception {
        Path path = temporaryDirectory.resolve("manual.docx");
        try (XWPFDocument document = new XWPFDocument();
             OutputStream output = Files.newOutputStream(path)) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Install");
            document.createParagraph().createRun().setText("Run the installer.");
            document.write(output);
        }

        List<ParsedDocumentSection> sections =
                new WordDocumentParser().parse(path, "manual.docx", null, "file://manual.docx");

        assertThat(sections).singleElement().satisfies(section -> {
            assertThat(section.title()).isEqualTo("Install");
            assertThat(section.sectionPath()).isEqualTo("Install");
            assertThat(section.content()).contains("Run the installer.");
        });
    }

    @Test
    void extractsHtmlAndMarkdownSections() throws Exception {
        Path html = temporaryDirectory.resolve("page.html");
        Files.writeString(
                html,
                "<html><head><title>Site</title></head><body>"
                        + "<h1>Overview</h1><p>HTML body</p><script>ignore()</script></body></html>",
                StandardCharsets.UTF_8);
        Path markdown = temporaryDirectory.resolve("readme.md");
        Files.writeString(
                markdown,
                "# Start\nMarkdown body\n\n## Detail\nMore detail",
                StandardCharsets.UTF_8);

        List<ParsedDocumentSection> htmlSections =
                new HtmlDocumentParser().parse(html, "page.html", "text/html", "https://example.test/page");
        List<ParsedDocumentSection> markdownSections =
                new MarkdownDocumentParser().parse(markdown, "readme.md", "text/markdown", null);

        assertThat(htmlSections).singleElement().satisfies(section -> {
            assertThat(section.sourceTitle()).isEqualTo("Site");
            assertThat(section.sectionPath()).isEqualTo("Overview");
            assertThat(section.content()).contains("HTML body").doesNotContain("ignore");
        });
        assertThat(markdownSections).hasSize(2);
        assertThat(markdownSections.get(0).sectionPath()).isEqualTo("Start");
        assertThat(markdownSections.get(1).sectionPath()).isEqualTo("Start / Detail");
    }

    @Test
    void usesTikaForPlainText() throws Exception {
        Path text = temporaryDirectory.resolve("notes.txt");
        Files.writeString(text, "plain text content", StandardCharsets.UTF_8);

        List<ParsedDocumentSection> sections =
                new TikaDocumentParser().parse(text, "notes.txt", "text/plain", null);

        assertThat(sections).singleElement()
                .extracting(ParsedDocumentSection::content)
                .asString()
                .contains("plain text content");
    }

    @Test
    void readsAllowedLocalGitRepository() throws Exception {
        Path repository = temporaryDirectory.resolve("repositories").resolve("docs");
        Files.createDirectories(repository);
        Files.writeString(repository.resolve("README.md"), "# Repository\nGit content");
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            git.add().addFilepattern("README.md").call();
            git.commit()
                    .setMessage("initial")
                    .setAuthor("TrustRAG", "test@example.com")
                    .call();
        }
        DocumentImportSettings settings = settings(
                temporaryDirectory.resolve("storage"),
                Set.of(temporaryDirectory.resolve("repositories")));
        GitDocumentSourceLoader loader =
                new GitDocumentSourceLoader(settings, new GitSourcePolicy(settings));
        DocumentImportTask task = DocumentImportTask.pendingGit(
                repository.toString(),
                null,
                new DocumentImportOptions(null, "document", null, null, null, null, null, null),
                java.time.Instant.parse("2026-06-13T00:00:00Z"));

        try (LoadedDocumentSource source = loader.load(task)) {
            assertThat(source.resources()).singleElement().satisfies(resource -> {
                assertThat(resource.filename()).isEqualTo("README.md");
                assertThat(resource.sourceUrl()).endsWith("#README.md");
            });
        }
    }

    @Test
    void rejectsLocalGitRepositoryOutsideAllowlist() throws Exception {
        Path repository = temporaryDirectory.resolve("private-repository");
        Files.createDirectories(repository);
        try (Git ignored = Git.init().setDirectory(repository.toFile()).call()) {
            // Repository existence is enough for the policy check.
        }
        DocumentImportSettings settings = settings(
                temporaryDirectory.resolve("storage"),
                Set.of(temporaryDirectory.resolve("allowed")));
        GitDocumentSourceLoader loader =
                new GitDocumentSourceLoader(settings, new GitSourcePolicy(settings));
        DocumentImportTask task = DocumentImportTask.pendingGit(
                repository.toString(),
                null,
                new DocumentImportOptions(null, "document", null, null, null, null, null, null),
                java.time.Instant.parse("2026-06-13T00:00:00Z"));

        Assertions.assertThatThrownBy(() -> loader.load(task))
                .isInstanceOf(io.github.trustrag.core.exception.InvalidRagRequestException.class)
                .hasMessageContaining("allowlist");
    }

    private DocumentImportSettings settings(Path storage, Set<Path> allowedRoots) {
        return new DocumentImportSettings(
                storage,
                10_000_000,
                1_000_000,
                50,
                5,
                3,
                true,
                false,
                Set.of(),
                allowedRoots,
                Set.of("md", "txt"));
    }

    private void addPdfPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 720);
            content.showText(text);
            content.endText();
        }
    }
}
