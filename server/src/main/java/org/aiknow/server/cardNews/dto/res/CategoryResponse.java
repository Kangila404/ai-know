package org.aiknow.server.cardNews.dto.res;

import org.aiknow.server.cardNews.domain.Category;

public record CategoryResponse (
        Long id,
        String name
){
    public static CategoryResponse from(Category category){
        return  new CategoryResponse(
                category.getId(),
                category.getName()
        );
    }
}
