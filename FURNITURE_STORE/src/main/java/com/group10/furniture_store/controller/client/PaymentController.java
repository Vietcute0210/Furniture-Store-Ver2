package com.group10.furniture_store.controller.client;

import com.group10.furniture_store.domain.Cart;
import com.group10.furniture_store.domain.CartDetails;
import com.group10.furniture_store.domain.DTO.PaymentDTO;
import com.group10.furniture_store.repository.CartRepository;
import com.group10.furniture_store.service.VNPayService;
import com.group10.furniture_store.utils.VNPayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/order")
public class PaymentController {

    @Autowired
    private VNPayUtils vnPayUtils;

    @Autowired
    private VNPayService vnPayService;

    @Autowired
    private CartRepository cartRepository;

    // Tạo đơn hàng COD
    @PostMapping("/create")
    public String createOrder(HttpServletRequest request, HttpSession session) {
        // Xử lý tạo đơn hàng COD(tạm thời không xử lý gì chỉ lấy thông tin thôi nhé)
        return "redirect:/thankyou";
    }

    // Tạo đơn hàng và thanh toán VNPay
    @PostMapping("/create-vnpay")
    @ResponseBody
    public Map<String, String> createOrderVNPay(HttpServletRequest request, HttpSession session) {
        Map<String, String> response = new HashMap<>();

        try {
            // Lấy cart ID từ session
            Long cartId = (Long) session.getAttribute("cartId");
            // System.out.println("Cart ID from session: " + cartId);

            if (cartId == null) {
                // System.out.println("Cart ID bị null rồi");
                response.put("success", "false");
                response.put("message", "Giỏ hàng trống");
                return response;
            }

            // Query cart từ database
            Cart cart = this.cartRepository.findById(cartId).orElse(null);

            if (cart == null) {
                System.out.println("Cart not found!");
                response.put("success", "false");
                response.put("message", "Giỏ hàng không tồn tại");
                return response;
            }

            List<CartDetails> cartDetails = cart.getCartDetails();

            if (cartDetails == null || cartDetails.isEmpty()) {
                System.out.println("Cart details is empty!");
                response.put("success", "false");
                response.put("message", "Giỏ hàng trống");
                return response;
            }

            // Tính tổng tiền
            double totalPrice = 0;
            for (CartDetails cd : cartDetails) {
                totalPrice += cd.getPrice() * cd.getQuantity();
            }

            Long orderId = System.currentTimeMillis();
            Long amount = (long) totalPrice;
            String orderInfo = "Thanh toan don hang " + orderId;

            System.out.println("Order ID: " + orderId);
            System.out.println("Amount: " + amount);

            PaymentDTO paymentDTO = new PaymentDTO(orderId, amount, orderInfo);
            String paymentUrl = vnPayService.createPaymentUrl(paymentDTO, request);

            System.out.println("Payment URL: " + paymentUrl);

            response.put("success", "true");
            response.put("paymentUrl", paymentUrl);
        } catch (Exception e) {
            System.err.println("Error creating VNPay order: " + e.getMessage());
            e.printStackTrace();
            response.put("success", "false");
            response.put("message", e.getMessage());
        }

        return response;
    }

    // Callback từ VNPay
    @GetMapping("/vnpay-callback")
    public String vnpayCallback(HttpServletRequest request, Model model) {
        Map<String, String> responseParams = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameter(paramName);
            responseParams.put(paramName, paramValue);
        }

        // Xác minh response
        boolean isValid = vnPayUtils.verifyCallback(responseParams);

        if (isValid) {
            String vnpResponseCode = responseParams.get("vnp_ResponseCode");
            if ("00".equals(vnpResponseCode)) {
                // Thanh toán thành công
                String orderId = responseParams.get("vnp_TxnRef");
                String amount = responseParams.get("vnp_Amount");

                // System.out.println("Thanh toán thành công với order : " + orderId);

                // Cập nhật trạng thái đơn hàng trong database
                model.addAttribute("orderId", orderId);
                model.addAttribute("amount", Long.parseLong(amount) / 100);
                return "client/order/success";
            } else {
                // Thanh toán thất bại
                model.addAttribute("message", "Thanh toán thất bại");
                return "client/order/failed";
            }
        } else {
            // System.err.println("Invalid VNPay response");
            model.addAttribute("message", "Xác thực thanh toán thất bại");
            return "client/cart/failed";
        }
    }

    @GetMapping("/success")
    public String paymentSuccess(String orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return orderId == null || orderId.isBlank()
                ? "redirect:/thankyou"
                : "redirect:/thankyou?orderId=" + orderId;
    }

    @GetMapping("/failed")
    public String paymentFailed(Model model) {
        return "client/cart/failed";
    }
}
