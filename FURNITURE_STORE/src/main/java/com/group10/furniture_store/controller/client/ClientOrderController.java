package com.group10.furniture_store.controller.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.group10.furniture_store.domain.Cart;
import com.group10.furniture_store.domain.CartDetails;
import com.group10.furniture_store.domain.User;
import com.group10.furniture_store.repository.CartRepository;
import com.group10.furniture_store.service.ProductService;
import com.group10.furniture_store.service.UserService;
import com.group10.furniture_store.service.sendEmail.SendEmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/order")
public class ClientOrderController {
    //them dong duoi
    private static final String SELECTED_CART_DETAILS_SESSION_KEY = "selectedCartDetailIds";

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Autowired
    private SendEmailService sendEmailService;

    @Autowired
    private CartRepository cartRepository;

    @GetMapping("/checkout")
    public String getCheckoutPage(Model model, HttpServletRequest request) {
        HttpSession session = request.getSession();
        //them dong duoi
        List<Long> selectedIds = getSelectedIdsFromSession(session);

        Long cartId = (Long) session.getAttribute("cartId");
        if (cartId == null) {
            return "redirect:/cart";
        }

        Cart cart = this.cartRepository.findById(cartId).orElse(null);
        if (cart == null) {
            return "redirect:/cart";
        }

        List<CartDetails> cartDetails = cart.getCartDetails();

        if (cartDetails == null || cartDetails.isEmpty()) {
            return "redirect:/cart";
        }
        //===========viet them ==============
        if (!selectedIds.isEmpty()) {
            List<CartDetails> filtered = new ArrayList<>();
            for (CartDetails cd : cartDetails) {
                if (cd != null && selectedIds.contains(cd.getId())) {
                    filtered.add(cd);
                }
            }
            cartDetails = filtered;
        }

        if (cartDetails.isEmpty()) {
            return "redirect:/cart";
        }
        //====================================

        double totalPrice = 0;
        for (CartDetails cd : cartDetails) {
            totalPrice += cd.getPrice() * cd.getQuantity();
        }

        model.addAttribute("cartDetails", cartDetails);
        model.addAttribute("totalPrice", totalPrice);

        return "client/cart/checkout";
    }

    @PostMapping("/checkout")
    public String postCheckoutPage(Model model, HttpServletRequest request,
            @RequestParam(value = "selectedCartDetailIds", required = false) String selectedCartDetailIds) {
        HttpSession session = request.getSession();
        List<Long> parsedIds = parseSelectedIds(selectedCartDetailIds);
        if (parsedIds.isEmpty()) {
            session.removeAttribute(SELECTED_CART_DETAILS_SESSION_KEY);
        } else {
            session.setAttribute(SELECTED_CART_DETAILS_SESSION_KEY, parsedIds);
        }
        return getCheckoutPage(model, request);
    }

    @PostMapping("/place-order")
    public String handlePlaceOrder(
            HttpServletRequest request,
            @RequestParam("receiverName") String receiverName,
            @RequestParam("receiverAddress") String receiverAddress,
            @RequestParam("receiverPhone") String receiverPhone,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam("totalPrice") String totalPrice) {

        HttpSession session = request.getSession();
        long id = (long) session.getAttribute("id");

        User currentUser = new User();
        currentUser.setId(id);

        final String uuid = UUID.randomUUID().toString().replace("-", "");
        this.productService.handlePlaceOrder(
                currentUser,
                session,
                receiverName,
                receiverAddress,
                receiverPhone,
                paymentMethod,
                uuid,
                Double.parseDouble(totalPrice),
                getSelectedIdsFromSession(session));

        session.removeAttribute(SELECTED_CART_DETAILS_SESSION_KEY);

        return "redirect:/order/after-order";
    }

    @GetMapping("/after-order")
    public String getAfterOrderPage(HttpServletRequest request) {
        HttpSession session = request.getSession();
        long id = (long) session.getAttribute("id");

        User user = this.userService.getUserById(id);
        String email = user.getEmail();

        sendEmailService.sendEmail(
                email,
                "Xác nhận đơn hàng",
                "Furniture Store chân thành cảm ơn bạn đã tin tưởng sử dụng sản phẩm của chúng tôi!");

        return "client/cart/after-order";
    }
    //=============viet them ==============
    private List<Long> parseSelectedIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split(",");
        List<Long> result = new ArrayList<>();
        for (String part : parts) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip invalid values
            }
        }
        return result;
    }

    private List<Long> getSelectedIdsFromSession(HttpSession session) {
        Object data = session.getAttribute(SELECTED_CART_DETAILS_SESSION_KEY);
        if (data instanceof List<?>) {
            List<?> rawList = (List<?>) data;
            List<Long> result = new ArrayList<>();
            for (Object value : rawList) {
                if (value instanceof Number number) {
                    result.add(number.longValue());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
