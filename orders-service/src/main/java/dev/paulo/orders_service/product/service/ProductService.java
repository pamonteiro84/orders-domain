package dev.paulo.orders_service.product.service;

import dev.paulo.orders_service.product.dto.ProductResponse;
import dev.paulo.orders_service.product.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> get() {
        return products.findAll().stream().map(ProductResponse::from).toList();
    }
}
