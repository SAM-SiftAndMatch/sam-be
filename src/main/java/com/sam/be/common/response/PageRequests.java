package com.sam.be.common.response;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {
    private PageRequests() {}

    public static PageRequest of(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR, "page phải >= 0 và size phải từ 1 đến 100");
        }
        return PageRequest.of(page, size, sort);
    }
}
