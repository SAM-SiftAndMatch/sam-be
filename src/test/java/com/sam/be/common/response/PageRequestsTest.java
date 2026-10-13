package com.sam.be.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sam.be.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageRequestsTest {
    @Test
    void acceptsTenByDefaultAndAtMostOneHundred() {
        assertEquals(10, PageRequests.of(0, 10, Sort.unsorted()).getPageSize());
        assertEquals(100, PageRequests.of(0, 100, Sort.unsorted()).getPageSize());
        assertThrows(ApiException.class, () -> PageRequests.of(0, 101, Sort.unsorted()));
        assertThrows(ApiException.class, () -> PageRequests.of(-1, 10, Sort.unsorted()));
    }
}
