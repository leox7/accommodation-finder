package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

// A simple page of results, so we never return Spring's Page object directly
@Getter
@Setter
@NoArgsConstructor
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static <T> PagedResponse<T> from(Page<T> page) {
        PagedResponse<T> response = new PagedResponse<>();
        response.setContent(page.getContent());
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLast(page.isLast());
        return response;
    }
}
