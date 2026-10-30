package dev.paulo.orders_service.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.paulo.orders_service.order.domain.Order;
import dev.paulo.orders_service.order.dto.CreateOrderRequest;
import dev.paulo.orders_service.order.dto.OrderResponse;
import dev.paulo.orders_service.order.exception.OrderNotFoundException;
import dev.paulo.orders_service.order.exception.ProductNotFoundException;
import dev.paulo.orders_service.order.repository.OrderRepository;
import dev.paulo.orders_service.product.domain.Product;
import dev.paulo.orders_service.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orders;

    @Mock
    ProductRepository products;

    @InjectMocks
    OrderService service;

    @Test
    void create_usesPriceFromDatabase() {
        UUID productId = UUID.randomUUID();
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        when(product.getPrice()).thenReturn(new BigDecimal("39.99"));
        when(products.findAllById(anyCollection())).thenReturn(List.of(product));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateOrderRequest(UUID.randomUUID(), List.of(new CreateOrderRequest.Item(productId, 2)));

        OrderResponse response = service.create(request);

        assertThat(response.totalAmount()).isEqualByComparingTo("79.98");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("39.99");
    }

    @Test
    void create_withUnknownProduct_throwsAndDoesNotSave() {
        UUID unknown = UUID.randomUUID();
        when(products.findAllById(anyCollection())).thenReturn(List.of());

        var request = new CreateOrderRequest(UUID.randomUUID(), List.of(new CreateOrderRequest.Item(unknown, 1)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ProductNotFoundException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void get_returnsOrderWithItems() {
        Order order = new Order(UUID.randomUUID());
        order.addItem(UUID.randomUUID(), 2, new BigDecimal("39.99"));
        when(orders.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = service.get(order.getId());

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.items()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("79.98");
    }

    @Test
    void get_withUnknownId_throwsOrderNotFound() {
        UUID unknown = UUID.randomUUID();
        when(orders.findWithItemsById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(unknown)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void list_capsPageSizeAt100AndSortsByNewest() {
        UUID customer = UUID.randomUUID();
        when(orders.findByCustomerId(eq(customer), any(Pageable.class))).thenReturn(Page.empty());

        service.list(customer, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findByCustomerId(eq(customer), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending())
                .isTrue();
    }

    @Test
    void list_withNegativePageOrZeroSize_usesSafeDefaults() {
        UUID customer = UUID.randomUUID();
        when(orders.findByCustomerId(eq(customer), any(Pageable.class))).thenReturn(Page.empty());

        service.list(customer, -3, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findByCustomerId(eq(customer), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
