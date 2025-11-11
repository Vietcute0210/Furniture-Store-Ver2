package com.group10.furniture_store.controller.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import com.group10.furniture_store.domain.Cart;
import com.group10.furniture_store.domain.CartDetails;
import com.group10.furniture_store.domain.Product;
import com.group10.furniture_store.domain.User;
import com.group10.furniture_store.service.ProductService;
import com.group10.furniture_store.service.WarehouseService;

@Controller
public class ItemController {
    @Autowired
    private final ProductService productService;
    @Autowired
    private WarehouseService warehouseService;

    public ItemController(ProductService productService, WarehouseService warehouseService) {
        this.productService = productService;
        this.warehouseService = warehouseService;
    }

    @GetMapping("/product/{id}")
    public String getProductPage(Model model, @PathVariable long id) {
        Product product = this.productService.getProductById(id);
        model.addAttribute("product", product);
        // gợi ý sản phẩm liên quan
        model.addAttribute("recommendations", this.productService.getRecommendedProducts(product, 4));
        return "client/product/detail";
    }

    @GetMapping("/products")
    public String getProductPage(Model model,
                                 @RequestParam("page") Optional<String> pageOptional,
                                 HttpServletRequest request) {
        int page = 1;
        try {
            if (pageOptional.isPresent()) {
                page = Integer.parseInt(pageOptional.get());
            }
        } catch (Exception ex) {
        }

        Pageable pageable = PageRequest.of(page - 1, 3);
        Page<Product> products = this.productService.getAllProducts(pageable);

        // giữ lại query string (trừ tham số page) để phân trang kèm filter/search
        String queryString = request.getQueryString();
        if (queryString != null && queryString.contains("page=")) {
            queryString = queryString.replaceAll("page=\\d+&?", "");
            queryString = queryString.replaceAll("&$", "");
            if (queryString.length() > 0 && !queryString.startsWith("&")) {
                queryString = "&" + queryString;
            } else if (queryString.length() == 0) {
                queryString = "";
            }
        } else if (queryString != null && queryString.length() > 0) {
            queryString = "&" + queryString;
        } else {
            queryString = "";
        }

        model.addAttribute("products", products.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("queryString", queryString);
        return "client/product/show";
    }

    @PostMapping("/add-product-to-cart/{id}")
    public String addProductToCart(
            @PathVariable long id,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") long quantity,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            HttpServletRequest request) {
        // fallback cũ: quay về trang /products khi không có redirectUrl
        return processAddProductToCart(id, quantity, redirectUrl, "/products", request);
    }

    @PostMapping("/add-product-from-view-detail")
    public String handleAddProductFromViewDetail(
            @RequestParam("id") long id,
            @RequestParam("quantity") long quantity,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            HttpServletRequest request) {
        // fallback: quay lại trang chi tiết sản phẩm
        return processAddProductToCart(id, quantity, redirectUrl, "/product/" + id, request);
    }

    private String processAddProductToCart(long productId,
                                           long quantity,
                                           String redirectUrl,
                                           String fallback,
                                           HttpServletRequest request) {
        HttpSession session = request.getSession();
        String email = (String) session.getAttribute("email");

        // giữ behavior cũ: bắt đăng nhập
        if (email == null || email.trim().isEmpty()) {
            return "redirect:/login";
        }

        long safeQuantity = quantity <= 0 ? 1 : quantity;
        this.productService.handleAddProductToCart(email, productId, session, safeQuantity);
        String target = resolveRedirectTarget(redirectUrl, fallback, request.getContextPath());
        return "redirect:" + target;
    }

    private String resolveRedirectTarget(String redirectUrl, String fallback, String contextPath) {
        if (redirectUrl == null) {
            return fallback;
        }
        String trimmed = redirectUrl.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("//")) {
            return fallback;
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        if (contextPath != null && !contextPath.trim().isEmpty() && trimmed.startsWith(contextPath + "/")) {
            trimmed = trimmed.substring(contextPath.length());
            if (trimmed.isEmpty()) {
                trimmed = "/";
            }
        }
        if (trimmed.startsWith("/WEB-INF") || trimmed.startsWith("/META-INF")
                || trimmed.startsWith("/add-product-to-cart")) {
            return fallback;
        }
        return trimmed;
    }

    @GetMapping("/cart")
    public String getCartPage(Model model, HttpServletRequest request) {
        HttpSession session = request.getSession();
        long id = (long) session.getAttribute("id");

        User currentUser = new User();
        currentUser.setId(id);

        Cart cart = this.productService.fetchCartByUser(currentUser);
        if (cart != null) {
            session.setAttribute("cartId", cart.getId());

            // tính lại sum từ cartDetails cho chắc
            Long sum = cart.getCartDetails() != null ? (long) cart.getCartDetails().size() : 0L;
            cart.setSum(sum);
            session.setAttribute("sum", sum);

            // đếm số dòng cartDetails (distinct item)
            int distinctCount = cart.getCartDetails() == null ? 0
                    : (int) cart.getCartDetails().stream().filter(java.util.Objects::nonNull).count();
            session.setAttribute("cartItemCount", distinctCount);
        } else {
            // không có cart: reset session
            session.removeAttribute("cartId");
            session.setAttribute("sum", 0L);
            session.setAttribute("cartItemCount", 0);
        }

        List<CartDetails> cartDetails = cart == null ? new ArrayList<>() : cart.getCartDetails();

        double totalPrice = 0;
        for (CartDetails cd : cartDetails) {
            totalPrice += cd.getQuantity() * cd.getPrice();
        }
        model.addAttribute("cartDetails", cartDetails);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("cart", cart);
        return "client/cart/show";
    }

    @PostMapping("/delete-cart-product/{id}")
    public String deleteCartDetail(@PathVariable long id, HttpServletRequest request) {
        HttpSession session = request.getSession();
        long cartDetailId = id;
        this.productService.handleRemoveCartDetail(cartDetailId, session);
        return "redirect:/cart";
    }

    @PostMapping("/confirm-checkout")
    public String confirmCheckout(@ModelAttribute("cart") Cart cart) {
        List<CartDetails> cartDetails = cart == null ? new ArrayList<>() : cart.getCartDetails();
        this.productService.handleUpdateCartBeforeCheckout(cartDetails);
        return "redirect:/checkout";
    }

    // ===================== filter-products start ==========================

    @GetMapping("/products/filter-data")
    public ResponseEntity<?> filterProducts(
            @RequestParam(value = "factories", required = false) List<String> factories,
            @RequestParam(value = "targets", required = false) List<String> targets,
            @RequestParam(value = "prices", required = false) List<String> prices,
            @RequestParam(value = "sort", required = false, defaultValue = "gia-nothing") String sortMode) {
        try {
            List<Product> products = productService.filterProducts(factories, targets, prices, sortMode);
            List<Map<String, Object>> result = products.stream().map(p -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", p.getId());
                map.put("name", p.getName());
                map.put("image", p.getImage());
                map.put("price", p.getPrice());
                map.put("shortDesc", p.getShortDesc());
                map.put("factory", p.getFactory());
                map.put("target", p.getTarget());

                Long stockQuantity = warehouseService.getStockQuantity(p.getId());
                map.put("stockQuantity", stockQuantity != null ? stockQuantity : 0);
                return map;
            }).collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }
    }

    // ===================== filter-products end ==========================

    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(@RequestParam("keyword") String keyword) {
        try {
            List<Product> products = productService.searchByName(keyword);
            List<Map<String, Object>> result = products.stream().map(p -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", p.getId());
                map.put("name", p.getName());
                map.put("image", p.getImage());
                map.put("price", p.getPrice());
                map.put("shortDesc", p.getShortDesc());

                // Load stock từ warehouse service
                Long stockQuantity = warehouseService.getStockQuantity(p.getId());
                map.put("stockQuantity", stockQuantity != null ? stockQuantity : 0);

                return map;
            }).collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }
    }

    @GetMapping("/thankyou")
    public String thankyouPage(@RequestParam(required = false) String orderId, Model model) {
        if (orderId != null) {
            model.addAttribute("orderId", orderId);
        }
        // dùng view mới theo file 2
        return "client/cart/thankyou";
    }
}
    