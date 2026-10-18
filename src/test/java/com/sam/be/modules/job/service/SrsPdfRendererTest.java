package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SrsPdfRendererTest {
    private final SrsPdfRenderer renderer = new SrsPdfRenderer();

    @Test
    void rendersVietnameseSrsAcrossPages() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")));
        String markdown = "# Yêu cầu chính\n- Khách hàng đăng dự án\n## Tiến độ theo tuần\n"
                + "Báo cáo công việc đã hoàn thành.\n".repeat(250);
        byte[] bytes = renderer.render("Dự án tiếng Việt", markdown);
        assertTrue(new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Dự án tiếng Việt"));
            assertTrue(text.contains("Khách hàng đăng dự án"));
        }
    }

    @Test
    void rejectsEmptySrs() {
        assertThrows(IllegalArgumentException.class, () -> renderer.render("SRS", "  "));
    }
}
