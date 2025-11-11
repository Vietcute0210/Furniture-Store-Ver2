package com.group10.furniture_store.controller.client;

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
import com.group10.furniture_store.repository.CartRepository;
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
    private final CartRepository cartRepository;

    // ==================== MERGED addProductToCart ====================

    @PostMapping("/api/add-product-to-cart")
    public ResponseEntity<?> addProductToCart(@RequestBody CartRequest cartRequest, HttpServletRequest request) {
        HttpSession session = request.getSession();
        String email = (String) session.getAttribute("email");

        // Giữ message cũ
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(401)
                    .body("Vui lòng đăng nhập để thêm sản phẩm vào giỏ hàng");
        }

        try {
            long quantity = cartRequest.getQuantity() == 0 ? 1 : cartRequest.getQuantity();

            // gọi service như cũ
            this.productService.handleAddProductToCart(email, cartRequest.getProductId(), session, quantity);

            // ----- logic cũ: xử lý sum / cartId / lưu xuống DB -----
            Long sum = (Long) session.getAttribute("sum");
            if (sum == null) {
                Long cartId = (Long) session.getAttribute("cartId");
                if (cartId != null) {
                    Cart cart = this.cartRepository.findById(cartId).orElse(null);
                    if (cart != null) {
                        sum = cart.getCartDetails() != null
                                ? (long) cart.getCartDetails().size()
                                : 0L;
                        cart.setSum(sum);
                        this.cartRepository.save(cart);
                        session.setAttribute("sum", sum);
                    } else {
                        sum = 0L;
                    }
                } else {
                    sum = 0L;
                }
            }

            // ----- logic mới: ưu tiên cartItemCount, fallback về sum -----
            Object distinctAttr = session.getAttribute("cartItemCount");
            int distinctCount = distinctAttr instanceof Number ? ((Number) distinctAttr).intValue() : 0;

            if (distinctCount <= 0) {
                distinctCount = sum != null ? sum.intValue() : 0;
            }

            // Vẫn trả về 1 số (FE chỉ cần number để update icon)
            return ResponseEntity.ok(distinctCount);

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body("Có lỗi xảy ra: " + ex.getMessage());
        }
    }

    // ==================== update-cart-quantity ====================

    @PostMapping("/api/update-cart-quantity")
    public ResponseEntity<Map<String, Object>> updateCartQuantity(
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {

        HttpSession session = request.getSession();
        Map<String, Object> response = new HashMap<>();

        try {
            Long cartDetailId = Long.parseLong(requestBody.get("cartDetailId").toString());
            Long quantity = Long.parseLong(requestBody.get("quantity").toString());

            if (quantity < 1) {
                response.put("success", false);
                response.put("message", "Số lượng phải lớn hơn hoặc bằng 1");
                return ResponseEntity.badRequest().body(response);
            }

            this.productService.updateCartDetailQuantity(cartDetailId, quantity, session);

            Long cartId = (Long) session.getAttribute("cartId");
            if (cartId != null) {
                Cart cart = this.cartRepository.findById(cartId).orElse(null);
                if (cart != null && cart.getCartDetails() != null) {
                    double totalPrice = 0;
                    double subtotal = 0;

                    for (CartDetails cd : cart.getCartDetails()) {
                        double cdTotal = cd.getPrice() * cd.getQuantity();
                        totalPrice += cdTotal;

                        if (Objects.equals(cd.getId(), cartDetailId)) {
                            subtotal = cdTotal;
                        }
                    }

                    Long cartCount = (long) cart.getCartDetails().size();
                    if (cartCount == null) {
                        cartCount = 0L;
                    }

                    response.put("success", true);
                    response.put("subtotal", subtotal);
                    response.put("totalPrice", totalPrice);
                    response.put("cartCount", cartCount);
                    return ResponseEntity.ok().body(response);
                }
            }

            response.put("success", false);
            response.put("message", "Không tìm thấy giỏ hàng");
            return ResponseEntity.badRequest().body(response);

        } catch (Exception ex) {
            ex.printStackTrace();
            response.put("success", false);
            response.put("message", "Có lỗi xảy ra: " + ex.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ==================== /api/cart/preview ====================

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
                        map.put("name", "Sản phẩm");
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
