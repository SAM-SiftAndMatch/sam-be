package com.sam.be.modules.job.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

@Component
public class SrsPdfRenderer {
    private static final String FONT_DIR = "/usr/share/fonts/truetype/dejavu/";
    private static final float MARGIN = 48;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

    public byte[] render(String title, String markdown) throws IOException {
        if (markdown == null || markdown.isBlank()) throw new IllegalArgumentException("SRS is empty");
        if (markdown.length() > 100_000) throw new IllegalArgumentException("SRS is too long");
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font regular = PDType0Font.load(document, new File(FONT_DIR + "DejaVuSans.ttf"));
            PDType0Font bold = PDType0Font.load(document, new File(FONT_DIR + "DejaVuSans-Bold.ttf"));
            try (Layout layout = new Layout(document, regular, bold)) {
                layout.write(title == null ? "Tài liệu SRS" : title, true, 17);
                layout.gap(8);
                for (String raw : markdown.replace("\\r", "").replace("\\n", "\n").split("\n")) {
                    String line = raw.strip();
                    if (line.isEmpty()) {
                        layout.gap(7);
                        continue;
                    }
                    int heading = 0;
                    while (heading < line.length() && line.charAt(heading) == '#') heading++;
                    boolean isHeading = heading > 0 && heading <= 6 && heading < line.length() && line.charAt(heading) == ' ';
                    if (isHeading) line = line.substring(heading).strip();
                    else if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.substring(2);
                    else if (line.startsWith("|")) line = line.replace('|', ' ').replaceAll("\\s{2,}", "  ").strip();
                    line = line.replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                            .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                            .replace("**", "").replace("__", "").replace("`", "");
                    if (line.matches("[-:| ]+")) continue;
                    if (isHeading) layout.gap(6);
                    layout.write(line, isHeading, isHeading ? Math.max(11, 15 - heading) : 10);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static final class Layout implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private PDPageContentStream stream;
        private float y;

        private Layout(PDDocument document, PDType0Font regular, PDType0Font bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            nextPage();
        }

        private void nextPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        private void gap(float amount) { y -= amount; }

        private void write(String text, boolean heading, float size) throws IOException {
            PDType0Font font = heading ? bold : regular;
            String clean = supported(font, Normalizer.normalize(text, Normalizer.Form.NFC));
            StringBuilder line = new StringBuilder();
            for (String word : clean.split("\\s+")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.getStringWidth(candidate) * size / 1000 > WIDTH) {
                    writeLine(line.toString(), font, size);
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
            if (!line.isEmpty()) writeLine(line.toString(), font, size);
        }

        private String supported(PDType0Font font, String value) {
            StringBuilder result = new StringBuilder();
            value.codePoints().forEach(cp -> {
                if (Character.isISOControl(cp)) return;
                String s = new String(Character.toChars(cp));
                try { font.getStringWidth(s); result.append(s); }
                catch (IOException | IllegalArgumentException ignored) { result.append('?'); }
            });
            return result.toString();
        }

        private void writeLine(String line, PDType0Font font, float size) throws IOException {
            if (y < MARGIN + 18) nextPage();
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(line);
            stream.endText();
            y -= size * 1.55f;
        }

        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
