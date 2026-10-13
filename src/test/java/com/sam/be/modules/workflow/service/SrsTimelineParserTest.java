package com.sam.be.modules.workflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SrsTimelineParserTest {

    @Test
    void parsesWeeklyTasksOnlyFromTimelineSection() {
        String srs =
                """
                # 7. Trách nhiệm của khách hàng
                - Cung cấp dữ liệu

                # 8. Mốc thời gian và tiêu chí nghiệm thu
                ### Tuần 1: Nền tảng
                - Hoàn thành đăng nhập
                - Hoàn thành phân quyền
                ### Tuần 2: Nghiệp vụ chính
                - Hoàn thành luồng đặt hàng

                # 9. Bảo hành và hỗ trợ kỹ thuật
                - Bảo hành 30 ngày
                """;

        List<SrsTimelineParser.ParsedTimelineTask> tasks = SrsTimelineParser.parse(srs);

        assertEquals(3, tasks.size());
        assertEquals(1, tasks.get(0).weekNumber());
        assertEquals("Hoàn thành đăng nhập", tasks.get(0).title());
        assertEquals(2, tasks.get(2).weekNumber());
        assertEquals("Hoàn thành luồng đặt hàng", tasks.get(2).title());
    }

    @Test
    void fallsBackToMilestoneBulletsForLegacySrs() {
        String srs =
                """
                # 8. Mốc thời gian và tiêu chí nghiệm thu
                - Hoàn thiện giao diện
                - Hoàn thiện chức năng
                - Bàn giao
                # 9. Bảo hành
                """;

        List<SrsTimelineParser.ParsedTimelineTask> tasks = SrsTimelineParser.parse(srs);

        assertEquals(3, tasks.size());
        assertEquals(1, tasks.get(0).weekNumber());
        assertEquals(3, tasks.get(2).weekNumber());
    }
}
