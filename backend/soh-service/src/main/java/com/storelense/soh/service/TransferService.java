package com.storelense.soh.service;

import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.soh.domain.entity.Transfer;
import com.storelense.soh.domain.repository.TransferRepository;
import com.storelense.soh.dto.CreateTransferRequest;
import com.storelense.soh.dto.ReceiveTransferRequest;
import com.storelense.soh.dto.TransferResponse;
import com.storelense.soh.mapper.SohMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final SohMapper          sohMapper;

    @SuppressWarnings("null")
    @Transactional
    public TransferResponse createTransfer(CreateTransferRequest req, UUID createdBy) {
        if (req.sourceStoreId().equals(req.destStoreId())) {
            throw new BusinessException("SAME_STORE",
                    "Source and destination stores must be different", HttpStatus.BAD_REQUEST);
        }

        Transfer transfer = Transfer.builder()
                .sourceStoreId(req.sourceStoreId())
                .destStoreId(req.destStoreId())
                .type(req.type())
                .createdBy(createdBy)
                .epcs(req.epcs())
                .build();

        Transfer saved = transferRepository.save(transfer);
        log.info("Transfer created: id={} source={} dest={} epcs={}",
                saved.getId(), saved.getSourceStoreId(), saved.getDestStoreId(), saved.getEpcs().size());
        return sohMapper.toTransferResponse(saved);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID id) {
        return sohMapper.toTransferResponse(findOrThrow(id));
    }

    @Transactional
    public TransferResponse receiveTransfer(UUID id, ReceiveTransferRequest req) {
        Transfer transfer = findOrThrow(id);

        if (!"PENDING".equals(transfer.getStatus()) && !"IN_TRANSIT".equals(transfer.getStatus())) {
            throw new BusinessException("TRANSFER_NOT_RECEIVABLE",
                    "Transfer must be PENDING or IN_TRANSIT to receive (current: " + transfer.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        transfer.setStatus("RECEIVED");
        transfer.setReceivedAt(OffsetDateTime.now());
        transfer.setReceivedEpcs(req.receivedEpcs());

        Transfer saved = transferRepository.save(transfer);
        log.info("Transfer received: id={} receivedEpcs={}", saved.getId(), saved.getReceivedEpcs().size());
        return sohMapper.toTransferResponse(saved);
    }

    @SuppressWarnings("null")
    private Transfer findOrThrow(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", id));
    }
}
