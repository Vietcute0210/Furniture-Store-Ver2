// /client/js/product_filter.js
// Lọc sản phẩm client-side bằng cách gọi API /products/filter-data (server-side filtering)

(function () {
  const filterBtn = document.getElementById("btnFilter");
  const productsContainer = document.getElementById("productsContainer");

  if (!filterBtn || !productsContainer) {
    return;
  }

  const numberFormatter = new Intl.NumberFormat("vi-VN");

  function getCheckedValues(containerSelector) {
    const container = document.querySelector(containerSelector);
    if (!container) {
      return [];
    }
    return Array.from(
      container.querySelectorAll('input[type="checkbox"]:checked')
    ).map((cb) => cb.value);
  }

  function getSelectedSort() {
    const radio = document.querySelector('input[name="radio-sort"]:checked');
    return radio ? radio.value : "gia-nothing";
  }

  function buildQueryString() {
    const params = new URLSearchParams();
    getCheckedValues("#factoryFilter").forEach((value) =>
      params.append("factories", value)
    );
    getCheckedValues("#targetFilter").forEach((value) =>
      params.append("targets", value)
    );
    getCheckedValues("#priceFilter").forEach((value) =>
      params.append("prices", value)
    );
    params.append("sort", getSelectedSort());
    const queryString = params.toString();
    return queryString ? `?${queryString}` : "";
  }

  function resolveCsrfField() {
    const input = document.querySelector(
      ".product-item form input[type='hidden'][name]"
    );
    if (!input) {
      return null;
    }
    return {
      name: input.getAttribute("name"),
      value: input.value,
    };
  }

  const csrfField = resolveCsrfField();

  function createProductCard(product) {
    const formattedPrice = numberFormatter.format(product.price || 0);
    const stockQuantity =
      typeof product.stockQuantity === "number" ? product.stockQuantity : 0;
    const csrfInputHtml = csrfField
      ? `<input type="hidden" name="${csrfField.name}" value="${csrfField.value}" />`
      : "";

    return `
      <div class="col-md-6 col-lg-4 product-item">
        <div class="rounded position-relative fruite-item">
          <div class="fruite-img">
            <img src="/images/product/${product.image || ""}" class="img-fluid w-100 rounded-top" alt="">
          </div>
          <div class="text-white bg-secondary px-3 py-1 rounded position-absolute" style="top: 10px; left: 10px;">
            Sản phẩm
          </div>
          <div class="p-4 border border-secondary border-top-0 rounded-bottom">
            <h4 style="font-size: 15px;" class="product-name">
              <a href="/product/${product.id || 0}">
                ${product.name || ""}
              </a>
            </h4>
            <p style="font-size: 13px;">
              ${product.shortDesc || ""}
            </p>
            <p style="font-size: 13px;">
              Chỉ còn: <span class="text-success fw-bold">${stockQuantity} sản phẩm</span>
            </p>
            <div class="d-flex flex-lg-wrap justify-content-center flex-column">
              <p style="font-size: 15px; text-align: center; width: 100%;" class="text-dark fw-bold mb-3">
                ${formattedPrice} đ
              </p>
              <form action="/add-product-to-cart/${product.id || 0}" method="post">
                ${csrfInputHtml}
                <button class="mx-auto btn border border-secondary rounded-pill px-3 text-primary" type="submit">
                  <i class="fa fa-shopping-bag me-2 text-primary"></i>
                  Add to cart
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function removePaginationBlocks() {
    const paginations = document.querySelectorAll(".pagination");
    paginations.forEach((node) => node.remove());
  }

  function renderProducts(products) {
    removePaginationBlocks();

    productsContainer.innerHTML = "";

    if (!products || !products.length) {
      productsContainer.innerHTML =
        '<div class="col-12 text-center mt-5">Không tìm thấy sản phẩm phù hợp</div>';
      return;
    }

    productsContainer.innerHTML = products
      .map((product) => createProductCard(product))
      .join("");
  }

  async function requestFilteredProducts() {
    const response = await fetch(`/products/filter-data${buildQueryString()}`, {
      headers: {
        Accept: "application/json",
      },
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    return response.json();
  }

  function setLoadingState(isLoading) {
    if (isLoading) {
      if (!filterBtn.dataset.originalText) {
        filterBtn.dataset.originalText = filterBtn.textContent.trim();
      }
      filterBtn.textContent = "Đang lọc...";
      filterBtn.disabled = true;
    } else {
      filterBtn.textContent =
        filterBtn.dataset.originalText || "Lọc Sản Phẩm";
      filterBtn.disabled = false;
    }
  }

  filterBtn.addEventListener("click", async function (event) {
    event.preventDefault();
    try {
      setLoadingState(true);
      const products = await requestFilteredProducts();
      renderProducts(products);
    } catch (error) {
      console.error("Filter error:", error);
      productsContainer.innerHTML = `<div class="col-12 text-center mt-5 text-danger">Lỗi lọc sản phẩm: ${error.message}</div>`;
    } finally {
      setLoadingState(false);
    }
  });
})();
