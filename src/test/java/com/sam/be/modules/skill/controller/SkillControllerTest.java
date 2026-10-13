package com.sam.be.modules.skill.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.skill.dto.response.SkillResponse;
import com.sam.be.modules.skill.repository.SkillRepository;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SkillControllerTest {

    @Test
    void searchesShareOnlyTheAllSkillsCacheKey() {
        SkillRepository repository = mock(SkillRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        List<SkillResponse> catalog =
                List.of(
                        SkillResponse.builder().id(1).name("Java").build(),
                        SkillResponse.builder().id(2).name("JavaScript").build(),
                        SkillResponse.builder().id(3).name("Python").build());
        when(cache.getListOrSet(
                        eq(RedisKeys.skillAll()),
                        eq(Duration.ofHours(1)),
                        eq(SkillResponse.class),
                        any()))
                .thenReturn(catalog);

        SkillController controller = new SkillController(repository, cache);
        assertEquals(2, controller.getSkills(" JAVA ").getResult().size());
        assertEquals(1, controller.getSkills("python").getResult().size());
        assertEquals(3, controller.getSkills(null).getResult().size());
        verify(cache, org.mockito.Mockito.times(3))
                .getListOrSet(
                        eq(RedisKeys.skillAll()),
                        eq(Duration.ofHours(1)),
                        eq(SkillResponse.class),
                        any(Supplier.class));
    }
}
