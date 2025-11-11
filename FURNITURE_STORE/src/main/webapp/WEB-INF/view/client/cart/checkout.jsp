<%@page contentType="text/html" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
        <%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
            <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

                <!DOCTYPE html>
                <html lang="vi">

                <head>
                    <meta charset="utf-8">
                    <title>Thanh toán - Furniture Store</title>
                    <meta content="width=device-width, initial-scale=1.0" name="viewport">

                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link
                        href="https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600&family=Raleway:wght@600;800&display=swap"
                        rel="stylesheet">

                    <link rel="stylesheet" href="https://use.fontawesome.com/releases/v5.15.4/css/all.css" />
                    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.4.1/font/bootstrap-icons.css"
                        rel="stylesheet">
                    <link href="/client/lib/lightbox/css/lightbox.min.css" rel="stylesheet">
                    <link href="/client/lib/owlcarousel/assets/owl.carousel.min.css" rel="stylesheet">
                    <link href="/client/css/bootstrap.min.css" rel="stylesheet">
                    <link href="/client/css/style.css" rel="stylesheet">
                    <link href="/client/css/override.css" rel="stylesheet">
                    <link href="/client/css/effects.css" rel="stylesheet">

                    <link href="/client/css/order.css" rel="stylesheet">
                </head>

                <body>
                    <jsp:include page="../layout/header.jsp" />

                    <!-- Checkout -->
                    <div class="container-fluid py-5">
                        <div class="container py-5 checkout-section">
                            <h1 class="mb-4 checkout-page-title">Thanh toán đơn hàng</h1>
                            <c:set var="checkoutError" value="${not empty error ? error : param.error}" />
                            <c:if test="${not empty checkoutError}">
                                <div class="alert alert-danger mb-4 checkout-alert" role="alert">
                                    <c:out value="${checkoutError}" />
                                </div>
                            </c:if>
                            <form action="/order/place-order" method="post" id="checkoutForm">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                <input type="hidden" name="totalPrice" value="${totalPrice}" data-cart-total-field />

                                <div class="row g-5 align-items-start">
                                    <!-- Thông tin người nhận -->
                                    <div class="col-md-12 col-lg-6 col-xl-6 checkout-form-column">
                                        <div class="checkout-card checkout-form-card">
                                            <div class="checkout-card__head">
                                                <span class="checkout-card__icon">
                                                    <i class="fas fa-address-card"></i>
                                                </span>
                                                <div>
                                                    <p class="checkout-card__eyebrow">Thông tin nhận hàng</p>
                                                    <h3 class="checkout-card__title">Chi tiết người nhận</h3>
                                                </div>
                                            </div>
                                            <div class="row g-4">
                                                <div class="col-12 col-lg-6">
                                                    <div class="form-item w-100">
                                                        <label class="form-label my-3">Họ và tên<sup>*</sup></label>
                                                        <input type="text" class="form-control" name="receiverName"
                                                            required>
                                                    </div>
                                                </div>
                                                <div class="col-12 col-lg-6">
                                                    <div class="form-item w-100">
                                                        <label class="form-label my-3">Số điện thoại<sup>*</sup></label>
                                                        <input type="tel" class="form-control" name="receiverPhone"
                                                            required>
                                                    </div>
                                                </div>
                                            </div>
                                            <div class="form-item">
                                                <label class="form-label my-3">Địa chỉ<sup>*</sup></label>
                                                <input type="text" class="form-control" name="receiverAddress" required>
                                            </div>
                                            <div class="form-item">
                                                <label class="form-label my-3">Ghi chú</label>
                                                <textarea class="form-control" name="note" rows="3"
                                                    placeholder="Ví dụ: Giao sau 18h, gọi trước khi đến..."></textarea>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Thông tin đơn hàng & thanh toán -->
                                    <div class="col-md-12 col-lg-6 col-xl-6">
                                        <div class="checkout-card checkout-summary-card">
                                            <div class="checkout-card__head">
                                                <span class="checkout-card__icon checkout-card__icon--accent">
                                                    <i class="fas fa-shopping-basket"></i>
                                                </span>
                                                <div>
                                                    <p class="checkout-card__eyebrow">Đơn hàng của bạn</p>
                                                    <h3 class="checkout-card__title">Sản phẩm & thanh toán</h3>
                                                </div>
                                            </div>
                                            <!-- Thông tin đơn hàng -->
                                            <div class="table-responsive checkout-summary-table-wrapper">
                                                <table class="table checkout-summary-table">
                                                    <thead>
                                                        <tr>
                                                            <th scope="col">Sản phẩm</th>
                                                            <th scope="col">Tên</th>
                                                            <th scope="col">Giá</th>
                                                            <th scope="col">Số lượng</th>
                                                            <th scope="col">Tổng</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        <c:forEach var="cartDetail" items="${cartDetails}">
                                                            <tr>
                                                                <th scope="row">
                                                                    <div class="d-flex align-items-center">
                                                                        <img src="/images/product/${cartDetail.product.image}"
                                                                            class="img-fluid me-3 rounded-3"
                                                                            style="width: 70px; height: 70px;" alt="">
                                                                    </div>
                                                                </th>
                                                                <td>
                                                                    <p class="mb-0">${cartDetail.product.name}</p>
                                                                </td>
                                                                <td>
                                                                    <p class="mb-0">
                                                                        <span data-checkout-unit-price="true"
                                                                            data-cart-detail-id="${cartDetail.id}"
                                                                            data-cart-detail-price="${cartDetail.price}">
                                                                            <fmt:formatNumber type="number"
                                                                                value="${cartDetail.price}" />
                                                                        </span>
                                                                        đ
                                                                    </p>
                                                                </td>
                                                                <td>
                                                                    <p class="mb-0">
                                                                        <span data-checkout-quantity="true"
                                                                            data-cart-detail-id="${cartDetail.id}">
                                                                            ${cartDetail.quantity}
                                                                        </span>
                                                                    </p>
                                                                </td>
                                                                <td>
                                                                    <p class="mb-0 cart-price-accent"
                                                                        data-cart-detail-id="${cartDetail.id}">
                                                                        <fmt:formatNumber type="number"
                                                                            value="${cartDetail.price * cartDetail.quantity}" />
                                                                        đ
                                                                    </p>
                                                                </td>
                                                            </tr>
                                                        </c:forEach>
                                                        <tr class="checkout-summary-total">
                                                            <th scope="row"></th>
                                                            <td></td>
                                                            <td></td>
                                                            <td>
                                                                <p class="mb-0 text-dark">Tổng tiền</p>
                                                            </td>
                                                            <td>
                                                                <div class="py-3 border-bottom border-top">
                                                                    <p class="mb-0 cart-total-accent" id="totalPrice"
                                                                        data-cart-total-price="true">
                                                                        <fmt:formatNumber type="number"
                                                                            value="${totalPrice}" /> đ
                                                                    </p>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    </tbody>
                                                </table>
                                            </div>

                                            <!-- Phương thức thanh toán -->
                                            <div class="checkout-payment">
                                                <p class="checkout-payment__title">Phương thức thanh toán</p>

                                                <div class="payment-option">
                                                    <input type="radio" class="payment-option__input" id="paymentCOD"
                                                        name="paymentMethod" value="COD" checked>
                                                    <label class="payment-option__label" for="paymentCOD">
                                                        <span class="payment-option__icon">
                                                            <i class="fas fa-truck-moving"></i>
                                                        </span>
                                                        <span class="payment-option__content">
                                                            <strong>Thanh toán khi nhận hàng (COD)</strong>
                                                            <small>Kiểm tra sản phẩm trước khi thanh toán.</small>
                                                        </span>
                                                    </label>
                                                </div>

                                                <div class="payment-option">
                                                    <input type="radio" class="payment-option__input" id="paymentVNPay"
                                                        name="paymentMethod" value="VNPAY">
                                                    <label class="payment-option__label" for="paymentVNPay">
                                                        <span class="payment-option__icon payment-option__icon--accent">
                                                            <img src="/client/img/vnpay-logo.png" alt="VNPay"
                                                                class="payment-option__logo">
                                                        </span>
                                                        <span class="payment-option__content">
                                                            <strong>Thanh toán qua VNPay</strong>
                                                            <small>Giao dịch bảo mật, xác nhận tức thì.</small>
                                                        </span>
                                                    </label>
                                                </div>

                                                <button type="submit"
                                                    class="btn checkout-submit-btn w-100 text-uppercase">
                                                    Đặt hàng
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </form>
                        </div>
                    </div>
                    <!-- Checkout -->

                    <jsp:include page="../layout/footer.jsp" />

                    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js"></script>
                    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.0.0/dist/js/bootstrap.bundle.min.js"></script>
                    <script src="/client/js/main.js"></script>
                    <script src="/client/js/effects.js"></script>
                    <script src="/client/js/cart.js"></script>

                    <script>
                        document.getElementById('checkoutForm').addEventListener('submit', function (e) {
                            e.preventDefault();

                            const paymentMethod = document.querySelector('input[name="paymentMethod"]:checked').value;

                            if (paymentMethod === 'VNPAY') {
                                // Thanh toán VNPay
                                const formData = new FormData(this);

                                fetch('/order/create-vnpay', {
                                    method: 'POST',
                                    body: formData
                                })
                                    .then(response => response.json())
                                    .then(data => {
                                        if (data.success === 'true') {
                                            window.location.href = data.paymentUrl;
                                        } else {
                                            alert('Lỗi: ' + data.message);
                                        }
                                    })
                                    .catch(error => {
                                        console.error('Error:', error);
                                        alert('Có lỗi xảy ra, vui lòng thử lại!');
                                    });
                            } else {
                                // Thanh toán COD: (mới chỉ thuần submit form, và chắc cũng chỉ nên dừng lại ở đây)
                                this.submit();
                            }
                        });
                    </script>
                </body>

                </html>
