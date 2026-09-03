package uz.bobnoza.wedding.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Plain, guaranteed-serializable page wrapper. Deliberately not returning
 * Spring Data's Page<T> directly from controllers — its Jackson support has
 * historically been version-sensitive, and this project runs on Jackson 3
 * (Spring Boot 4 default), so a plain record sidesteps that entirely.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
