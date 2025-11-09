package com.group10.furniture_store.controller.client;
//them thư viện
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.group10.furniture_store.domain.Cart;
import com.group10.furniture_store.domain.CartDetails;
import com.group10.furniture_store.domain.User;
import com.group10.furniture_store.service.ProductService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

class CartRequest {
    private long quantity;
    private long productId;

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getProductId() {
        return productId;
    }

    public void setProductId(long productId) {
        this.productId = productId;
    }
}

@RestController
@RequiredArgsConstructor
public class CartAPI {
    private final ProductService productService;

    @PostMapping("/api/add-product-to-cart")
    public ResponseEntity<Integer> addProductToCart(@RequestBody CartRequest cartRequest, HttpServletRequest request) {
        HttpSession session = request.getSession();
        String email = (String) session.getAttribute("email");
        this.productService.handleAddProductToCart(email, cartRequest.getProductId(), session,
                cartRequest.getQuantity() == 0 ? 1 : cartRequest.getQuantity());
        int sum = (int) session.getAttribute("sum");
        return ResponseEntity.ok().body(sum);
    }

    //=====================sửa thêm start==========================

    @GetMapping("/api/cart/preview")
    public ResponseEntity<List<Map<String, Object>>> getCartPreview(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("id") == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        Object userIdAttr = session.getAttribute("id");
        if (!(userIdAttr instanceof Number)) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        long userId = ((Number) userIdAttr).longValue();
        User currentUser = new User();
        currentUser.setId(userId);

        Cart cart = this.productService.fetchCartByUser(currentUser);
        if (cart == null || cart.getCartDetails() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Map<String, Object>> previewItems = cart.getCartDetails().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(CartDetails::getId).reversed())
                .limit(5)
                .map(cartDetails -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cartDetails.getId());
                    map.put("quantity", cartDetails.getQuantity());
                    map.put("price", cartDetails.getPrice());
                    if (cartDetails.getProduct() != null) {
                        map.put("productId", cartDetails.getProduct().getId());
                        map.put("name", cartDetails.getProduct().getName());
                        map.put("image", cartDetails.getProduct().getImage());
                    } else {
                        map.put("productId", null);
                        map.put("name", "Sản phẩm");
                        map.put("image", null);
                    }
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(previewItems);
    }
}
//=====================sửa thêm end==========================