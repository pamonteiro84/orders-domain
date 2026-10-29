package dev.paulo.orders_service.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.paulo.orders_service.product.domain.Product;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>{
    
}






