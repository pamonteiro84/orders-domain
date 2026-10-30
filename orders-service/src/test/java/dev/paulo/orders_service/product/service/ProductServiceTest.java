package dev.paulo.orders_service.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.paulo.orders_service.product.domain.Product;
import dev.paulo.orders_service.product.dto.ProductResponse;
import dev.paulo.orders_service.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository products;

    @InjectMocks
    ProductService service;

    @Test
    void get_mapsEveryProductToResponse() {
        UUID id = UUID.randomUUID();
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn("adagio");
        when(product.getPrice()).thenReturn(new BigDecimal("39.99"));
        when(products.findAll()).thenReturn(List.of(product));

        List<ProductResponse> response = service.get();

        assertThat(response).containsExactly(new ProductResponse(id, "adagio", new BigDecimal("39.99")));
    }

    @Test
    void get_withNoProducts_returnsEmptyList() {
        when(products.findAll()).thenReturn(List.of());

        assertThat(service.get()).isEmpty();
    }
}
