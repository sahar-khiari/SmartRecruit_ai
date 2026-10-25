package com.smartrecruit.mapper;

import com.smartrecruit.dto.response.MessageSourceResponse;
import com.smartrecruit.entity.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageSourceMapper {

    public MessageSourceResponse toResponse(MessageSource source) {
        if (source == null) {
            return null;
        }
        return new MessageSourceResponse(
                source.getDocumentId(),
                source.getChunkId(),
                source.getChunkIndex(),
                source.getTitle(),
                source.getCategory(),
                source.getScore(),
                source.getDocumentVersion(),
                source.getExcerpt()
        );
    }

    public List<MessageSourceResponse> toResponses(List<MessageSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream().map(this::toResponse).toList();
    }
}
