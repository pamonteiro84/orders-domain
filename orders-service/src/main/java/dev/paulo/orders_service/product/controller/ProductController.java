package dev.paulo.orders_service.product.controller;

import dev.paulo.orders_service.product.dto.ProductResponse;
import dev.paulo.orders_service.product.service.ProductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping()
    public List<ProductResponse> get() {
        return productService.get();
    }
}
