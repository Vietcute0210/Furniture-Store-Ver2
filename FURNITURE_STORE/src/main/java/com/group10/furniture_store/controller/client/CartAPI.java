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
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        long quantity = cartRequest.getQuantity() == 0 ? 1 : cartRequest.getQuantity();
        this.productService.handleAddProductToCart(email, cartRequest.getProductId(), session, quantity);
        Object distinctAttr = session.getAttribute("cartItemCount");
        int distinctCount = distinctAttr instanceof Number ? ((Number) distinctAttr).intValue() : 0;
        if (distinctCount <= 0) {
            Object sumAttr = session.getAttribute("sum");
            distinctCount = sumAttr instanceof Number ? ((Number) sumAttr).intValue() : 0;
        }
        return ResponseEntity.ok(distinctCount);
    }

    //=====================sửa thêm start==========================

    @GetMapping("/api/cart/preview")
    public ResponseEntity<Map<String, Object>> getCartPreview(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("id") == null) {
            return ResponseEntity.ok(buildPreviewResponse(Collections.emptyList(), 0));
        }
        Object userIdAttr = session.getAttribute("id");
        if (!(userIdAttr instanceof Number)) {
            return ResponseEntity.ok(buildPreviewResponse(Collections.emptyList(), 0));
        }
        long userId = ((Number) userIdAttr).longValue();
        User currentUser = new User();
        currentUser.setId(userId);

        Cart cart = this.productService.fetchCartByUser(currentUser);
        if (cart == null || cart.getCartDetails() == null) {
            return ResponseEntity.ok(buildPreviewResponse(Collections.emptyList(), 0));
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
                        map.put("name", "S???n ph??cm");
                        map.put("image", null);
                    }
                    return map;
                })
                .collect(Collectors.toList());

        long totalDistinctItems = cart.getCartDetails().stream()
                .filter(Objects::nonNull)
                .map(cd -> cd.getProduct() != null ? cd.getProduct().getId() : cd.getId())
                .distinct()
                .count();
        return ResponseEntity.ok(buildPreviewResponse(previewItems, (int) totalDistinctItems));
    }

    private Map<String, Object> buildPreviewResponse(List<Map<String, Object>> items, int totalQuantity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", items);
        payload.put("totalQuantity", Math.max(0, totalQuantity));
        return payload;
    }

}
//=====================sửa thêm end==========================
