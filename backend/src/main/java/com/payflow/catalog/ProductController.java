package com.payflow.catalog;

import com.payflow.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@Tag(name = "Produtos")
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return products.findByActiveTrue(pageable).map(ProductResponse::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ProductResponse get(@PathVariable UUID id) {
        return products.findById(id)
                .filter(Product::isActive)
                .map(ProductResponse::from)
                .orElseThrow(() -> BusinessException.notFound("Produto"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        Product product = products.save(new Product(request.name(), request.description(), request.price()));
        return ProductResponse.from(product);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        Product product = products.findById(id).orElseThrow(() -> BusinessException.notFound("Produto"));
        product.update(request.name(), request.description(), request.price(),
                request.active() == null || request.active());
        return ProductResponse.from(product);
    }

    public record ProductRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Boolean active
    ) {
    }

    public record ProductResponse(UUID id, String name, String description, BigDecimal price, boolean active) {

        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                    product.getPrice(), product.isActive());
        }
    }
}
