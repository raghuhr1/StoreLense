package com.storelense.product.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.product.domain.entity.Barcode;
import com.storelense.product.domain.entity.EpcTag;
import com.storelense.product.domain.entity.Product;
import com.storelense.product.domain.repository.BarcodeRepository;
import com.storelense.product.domain.repository.EpcTagRepository;
import com.storelense.product.domain.repository.ProductRepository;
import com.storelense.product.dto.*;
import com.storelense.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository    productRepository;
    private final EpcTagRepository     epcTagRepository;
    private final BarcodeRepository    barcodeRepository;
    private final ProductMapper        productMapper;
    private final StringRedisTemplate  redis;

    private static final String EPC_CACHE_PREFIX = "product:epc:";
    private static final Duration EPC_CACHE_TTL  = Duration.ofMinutes(30);

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listProducts(String search, UUID storeId,
                                                      OffsetDateTime since, Pageable pageable) {
        var page = storeId != null
                ? (StringUtils.hasText(search)
                        ? productRepository.searchByStore(search, storeId.toString(), since, pageable)
                        : productRepository.findByStore(storeId.toString(), since, pageable))
                : (StringUtils.hasText(search)
                        ? productRepository.search(search, pageable)
                        : productRepository.findByActiveTrue(pageable));
        return PageResponse.from(page.map(productMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listAllActive(String search, Pageable pageable) {
        var page = StringUtils.hasText(search)
                ? productRepository.search(search, pageable)
                : productRepository.findByActiveTrue(pageable);
        return PageResponse.from(page.map(productMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return productMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        return productRepository.findBySku(sku)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", sku));
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest req) {
        if (productRepository.existsBySku(req.sku())) {
            throw new BusinessException("SKU_EXISTS", "SKU already exists", HttpStatus.CONFLICT);
        }
        return productMapper.toResponse(productRepository.save(productMapper.toEntity(req)));
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest req) {
        Product product = findOrThrow(id);
        productMapper.updateEntity(req, product);
        productRepository.save(product);

        if (req.ean() != null && !req.ean().isBlank()) {
            Barcode barcode = barcodeRepository
                    .findByProduct_IdAndBarcodeType(id, "ean13")
                    .orElseGet(() -> Barcode.builder().product(product).barcodeType("ean13").primary(true).build());
            barcode.setBarcodeValue(req.ean().trim());
            barcodeRepository.save(barcode);
        }

        return productMapper.toResponse(productRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public EpcLookupResponse lookupEpc(String epc) {
        // Check Redis cache first
        String cached = redis.opsForValue().get(EPC_CACHE_PREFIX + epc);
        if (cached != null) {
            return new EpcLookupResponse(epc, UUID.fromString(cached), true);
        }

        return epcTagRepository.findByEpc(epc.toUpperCase())
                .filter(t -> t.isActive() && t.getProduct() != null)
                .map(t -> {
                    redis.opsForValue().set(EPC_CACHE_PREFIX + epc,
                            t.getProduct().getId().toString(), EPC_CACHE_TTL);
                    return new EpcLookupResponse(epc, t.getProduct().getId(), false);
                })
                .orElseThrow(() -> new ResourceNotFoundException("EpcTag", epc));
    }

    @Transactional
    public EpcTag associateEpc(UUID productId, String epc, UUID encodedBy) {
        Product product = findOrThrow(productId);
        String upperEpc = epc.toUpperCase();

        if (epcTagRepository.existsByEpc(upperEpc)) {
            throw new BusinessException("EPC_EXISTS", "EPC already registered");
        }

        EpcTag tag = EpcTag.builder()
                .epc(upperEpc)
                .product(product)
                .encoded(true)
                .encodedAt(OffsetDateTime.now())
                .encodedBy(encodedBy)
                .build();

        redis.delete(EPC_CACHE_PREFIX + upperEpc);
        return epcTagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public boolean existsByEan(String ean) {
        return barcodeRepository.existsByBarcodeValueIgnoreCase(ean);
    }

    @Transactional(readOnly = true)
    public EpcLookupResponse lookupByEan(String ean) {
        return barcodeRepository.findByBarcodeValueIgnoreCase(ean)
                .filter(b -> b.getProduct() != null)
                .map(b -> new EpcLookupResponse(ean, b.getProduct().getId(), false))
                .orElseThrow(() -> new ResourceNotFoundException("Barcode", ean));
    }

    @Transactional(readOnly = true)
    public List<String> getEpcsByEan(String ean) {
        return epcTagRepository.findActiveByBarcodeValue(ean)
                .stream()
                .map(EpcTag::getEpc)
                .toList();
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
