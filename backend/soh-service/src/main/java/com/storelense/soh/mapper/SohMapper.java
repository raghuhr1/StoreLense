package com.storelense.soh.mapper;

import com.storelense.soh.domain.entity.SohResult;
import com.storelense.soh.domain.entity.SohSession;
import com.storelense.soh.domain.entity.Transfer;
import com.storelense.soh.dto.SohResultResponse;
import com.storelense.soh.dto.SohSessionResponse;
import com.storelense.soh.dto.TransferResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SohMapper {
    SohSessionResponse toResponse(SohSession session);

    @Mapping(target = "sessionId", source = "session.id")
    SohResultResponse toResultResponse(SohResult result);

    TransferResponse toTransferResponse(Transfer transfer);
}
