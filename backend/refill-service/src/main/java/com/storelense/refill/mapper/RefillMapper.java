package com.storelense.refill.mapper;

import com.storelense.refill.domain.entity.RefillTask;
import com.storelense.refill.domain.entity.RefillTaskItem;
import com.storelense.refill.dto.RefillTaskResponse;
import com.storelense.refill.dto.TaskItemResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RefillMapper {
    RefillTaskResponse toResponse(RefillTask task);
    TaskItemResponse toItemResponse(RefillTaskItem item);
}
