<%@page contentType="text/html" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

        <!DOCTYPE html>
        <html lang="vi">

        <head>
            <meta charset="utf-8">
            <title>Thanh toán thất bại</title>
            <link href="/client/css/bootstrap.min.css" rel="stylesheet">
            <link href="/client/css/style.css" rel="stylesheet">
        </head>

        <body>
            <jsp:include page="../layout/header.jsp" />

            <div class="container-fluid py-5">
                <div class="container py-5 text-center">
                    <div class="row justify-content-center">
                        <div class="col-lg-6">
                            <i class="bi bi-x-circle display-1 text-danger"></i>
                            <h1 class="display-5 fw-bold text-danger mb-4">Thanh toán thất bại!</h1>
                            <p class="lead mb-4">${message != null ? message : 'Đã có lỗi xảy ra trong quá trình thanh
                                toán'}</p>
                            <a href="/cart" class="btn btn-primary rounded-pill py-3 px-5 mt-3">Quay lại giỏ hàng</a>
                        </div>
                    </div>
                </div>
            </div>

            <jsp:include page="../layout/footer.jsp" />
        </body>

        </html>