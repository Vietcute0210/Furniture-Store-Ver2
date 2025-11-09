// ============ Cập nhật giá tiền khi tăng/giảm số lượng ============ //
$(document).ready(function () {
  const QUANTITY_STORAGE_KEY = "furniture_store_cart_quantities";
  const SELECTION_STORAGE_KEY = "furniture_store_cart_selection";
  let selectionStateMap = null;

  // Currency formatter for VNĐ
  function formatCurrency(number) {
    const amount = Number(number);
    const safeAmount = Number.isFinite(amount) ? amount : 0;
    return safeAmount.toLocaleString("vi-VN") + " đ";
  }

  function parsePrice(raw) {
    if (typeof raw === "number") {
      return Number.isFinite(raw) ? raw : 0;
    }
    if (raw === null || typeof raw === "undefined") return 0;

    let normalized = String(raw).trim();

    const decimalMatch = normalized.match(/([.,])(\d{1,2})$/);
    if (decimalMatch) {
      const integerPart = normalized
        .slice(0, decimalMatch.index)
        .replace(/[^\d-]/g, "");
      normalized = `${integerPart}.${decimalMatch[2]}`;
    } else {
      normalized = normalized.replace(/[^\d-]/g, "");
    }

    const numericPrice = parseFloat(normalized);
    return Number.isFinite(numericPrice) ? numericPrice : 0;
  }

  function getSelectionMap() {
    if (!selectionStateMap) {
      selectionStateMap = loadSavedSelections();
    }
    return selectionStateMap;
  }

  function loadSavedSelections() {
    try {
      const raw = localStorage.getItem(SELECTION_STORAGE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (e) {
      console.warn("Failed to parse saved cart selections", e);
      return {};
    }
  }

  function persistSelectionMap(map) {
    try {
      localStorage.setItem(SELECTION_STORAGE_KEY, JSON.stringify(map));
    } catch (e) {
      console.warn("Failed to save cart selections", e);
    }
  }

  function getSelectionState(id) {
    if (typeof id === "undefined") {
      return true;
    }
    const map = getSelectionMap();
    const key = String(id);
    if (!Object.prototype.hasOwnProperty.call(map, key)) {
      map[key] = true;
      persistSelectionMap(map);
    }
    return !!map[key];
  }

  function setSelectionState(id, selected) {
    if (typeof id === "undefined") return;
    const map = getSelectionMap();
    map[String(id)] = !!selected;
    persistSelectionMap(map);
  }

  function clearSavedSelections() {
    try {
      localStorage.removeItem(SELECTION_STORAGE_KEY);
    } catch (e) {
      /* ignore */
    }
    selectionStateMap = {};
  }

  function updateRowVisualState(cartDetailId, isSelected) {
    const row = $(`[data-cart-row-id='${cartDetailId}']`);
    if (row.length) {
      row.toggleClass("cart-row--muted", !isSelected);
    }
  }

  function syncSelectAllCheckboxState() {
    const selectAll = $("[data-cart-select-all]");
    if (!selectAll.length) return;
    const checkboxes = $("[data-cart-select]");
    if (!checkboxes.length) {
      selectAll.prop("checked", false).prop("indeterminate", false);
      return;
    }
    const checkedCount = checkboxes.filter(":checked").length;
    selectAll.prop(
      "checked",
      checkedCount > 0 && checkedCount === checkboxes.length
    );
    selectAll.prop(
      "indeterminate",
      checkedCount > 0 && checkedCount < checkboxes.length
    );
  }

  function getSelectedIdsFromCheckboxes() {
    return $("[data-cart-select]")
      .filter(":checked")
      .map(function () {
        return $(this).data("cart-detail-id");
      })
      .get();
  }

  function updateSelectedIdsField() {
    const target = $("#selectedCartDetailIdsField");
    if (!target.length) return;
    const ids = getSelectedIdsFromCheckboxes();
    target.val(ids.join(","));
  }

  function applySavedSelections() {
    const checkboxes = $("[data-cart-select]");
    if (!checkboxes.length) return;
    checkboxes.each(function () {
      const checkbox = $(this);
      const id = checkbox.data("cart-detail-id");
      const isSelected = getSelectionState(id);
      checkbox.prop("checked", isSelected);
      updateRowVisualState(id, isSelected);
    });
    syncSelectAllCheckboxState();
    updateSelectedIdsField();
  }

  function shouldIncludeCartItem(cartDetailId) {
    if (typeof cartDetailId === "undefined") {
      return true;
    }
    const checkbox = $(
      `[data-cart-select][data-cart-detail-id='${cartDetailId}']`
    );
    if (checkbox.length) {
      return checkbox.is(":checked");
    }
    return getSelectionState(cartDetailId);
  }

  // Compute cart total using raw quantity/price data for accuracy
  function updateTotal() {
    let total = 0;
    const cartInputs = $("input[data-cart-detail-id]");
    if (cartInputs.length) {
      cartInputs.each(function () {
        const cartDetailId = $(this).data("cart-detail-id");
        if (!shouldIncludeCartItem(cartDetailId)) {
          return;
        }
        const qty = parseInt($(this).val(), 10) || 0;
        const pricePerItem = parsePrice($(this).data("cart-detail-price"));
        total += qty * pricePerItem;
      });
    } else {
      const checkoutItems = $("[data-checkout-unit-price][data-cart-detail-id]");
      checkoutItems.each(function () {
        const cartDetailId = $(this).data("cart-detail-id");
        if (!shouldIncludeCartItem(cartDetailId)) {
          return;
        }
        const pricePerItem = getCheckoutUnitPrice(cartDetailId);
        const qty =
          parseInt(
            $(
              `[data-checkout-quantity][data-cart-detail-id='${cartDetailId}']`
            ).text(),
            10
          ) || 0;
        total += pricePerItem * qty;
      });
    }
    $("[data-cart-total-price]").text(formatCurrency(total));
  }

  function syncHiddenQuantity(input, qty) {
    if (!input || !input.length) return;
    const index = input.data("cart-detail-index");
    if (typeof index === "undefined") return;
    const hiddenQty = $(`input[name='cartDetails[${index}].quantity']`);
    if (hiddenQty.length) {
      hiddenQty.val(qty);
    }
  }

  function getCheckoutUnitPrice(cartDetailId) {
    const unitPriceEl = $(
      `[data-checkout-unit-price][data-cart-detail-id='${cartDetailId}']`
    );
    if (!unitPriceEl.length) return 0;
    const raw =
      unitPriceEl.data("cart-detail-price") ||
      unitPriceEl.attr("data-cart-detail-price");
    return parsePrice(raw);
  }

  function updateCheckoutQuantityDisplay(cartDetailId, qty) {
    const quantityEl = $(
      `[data-checkout-quantity][data-cart-detail-id='${cartDetailId}']`
    );
    if (quantityEl.length) {
      quantityEl.text(qty);
    }
  }

  function loadSavedQuantities() {
    try {
      const raw = localStorage.getItem(QUANTITY_STORAGE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (e) {
      console.warn("Failed to parse saved cart quantities", e);
      return {};
    }
  }

  function saveQuantitiesMap(map) {
    try {
      localStorage.setItem(QUANTITY_STORAGE_KEY, JSON.stringify(map));
    } catch (e) {
      console.warn("Failed to save cart quantities", e);
    }
  }

  function saveQuantity(id, qty) {
    if (typeof id === "undefined") return;
    const map = loadSavedQuantities();
    map[id] = qty;
    saveQuantitiesMap(map);
  }

  function clearSavedQuantities() {
    try {
      localStorage.removeItem(QUANTITY_STORAGE_KEY);
    } catch (e) {
      /* ignore */
    }
    clearSavedSelections();
  }

  // Áp dụng các giá trị đã lưu (nếu có) lên DOM khi load trang
  function applySavedQuantities() {
    const map = loadSavedQuantities();
    Object.keys(map).forEach((id) => {
      const qty = parseInt(map[id], 10);
      if (!isFinite(qty)) return;

      const visibleInput = $(`input[data-cart-detail-id='${id}']`);
      let pricePerItem = 0;

      if (visibleInput.length) {
        visibleInput.val(qty);
        pricePerItem = parsePrice(visibleInput.data("cart-detail-price"));
        syncHiddenQuantity(visibleInput, qty);
      } else {
        pricePerItem = getCheckoutUnitPrice(id);
      }

      if (pricePerItem) {
        const totalForItem = pricePerItem * qty;
        $(`p[data-cart-detail-id='${id}']`).text(formatCurrency(totalForItem));
      }

      updateCheckoutQuantityDisplay(id, qty);
    });
    updateTotal();
  }

  function bindSelectionEvents() {
    $("[data-cart-select]")
      .off("change.cartSelect")
      .on("change.cartSelect", function () {
        const checkbox = $(this);
        const id = checkbox.data("cart-detail-id");
        const isSelected = checkbox.is(":checked");
        setSelectionState(id, isSelected);
        updateRowVisualState(id, isSelected);
        syncSelectAllCheckboxState();
        updateSelectedIdsField();
        updateTotal();
      });

    $("[data-cart-select-all]")
      .off("change.cartSelectAll")
      .on("change.cartSelectAll", function () {
        const shouldSelect = $(this).is(":checked");
        const checkboxes = $("[data-cart-select]");
        checkboxes.each(function () {
          const checkbox = $(this);
          const id = checkbox.data("cart-detail-id");
          checkbox.prop("checked", shouldSelect);
          setSelectionState(id, shouldSelect);
          updateRowVisualState(id, shouldSelect);
        });
        syncSelectAllCheckboxState();
        updateSelectedIdsField();
        updateTotal();
      });

    const cartForm = $("#cartSelectionForm");
    if (cartForm.length) {
      cartForm.off("submit.cartSelect").on("submit.cartSelect", function (e) {
        const selectedIds = getSelectedIdsFromCheckboxes();
        if (!selectedIds.length) {
          e.preventDefault();
          alert("Vui lòng chọn ít nhất một sản phẩm để thanh toán.");
          return false;
        }
        $("#selectedCartDetailIdsField").val(selectedIds.join(","));
        return true;
      });
    }
  }

  // Khi bấm nút cộng
  $(".btn-plus")
    .off("click")
    .on("click", function () {
      const input = $(this).closest(".quantity").find("input");
      let value = parseInt(input.val(), 10) || 0;
      value = value + 1;
      input.val(value);

      // Cập nhật input ẩn trong form checkout (tên sẽ là cartDetails[INDEX].quantity)
      syncHiddenQuantity(input, value);

      const pricePerItem = parsePrice(input.data("cart-detail-price")) || 0;
      const cartDetailId = input.data("cart-detail-id");

      // Cập nhật lại thành tiền của sản phẩm (chỉ cập nhật thẻ p chứa giá)
      const totalForItem = pricePerItem * value;
      const itemPrice = $(`p[data-cart-detail-id='${cartDetailId}']`);
      itemPrice.text(formatCurrency(totalForItem)).addClass("highlight");
      setTimeout(() => itemPrice.removeClass("highlight"), 500);

      updateCheckoutQuantityDisplay(cartDetailId, value);

      // Lưu số lượng mới
      saveQuantity(cartDetailId, value);

      updateTotal();
    });

  // Khi bấm nút trừ
  $(".btn-minus")
    .off("click")
    .on("click", function () {
      const input = $(this).closest(".quantity").find("input");
      let value = parseInt(input.val(), 10) || 0;
      if (value > 1) {
        value = value - 1;
        input.val(value);

        // Cập nhật input ẩn trong form checkout
        syncHiddenQuantity(input, value);

        const pricePerItem = parsePrice(input.data("cart-detail-price")) || 0;
        const cartDetailId = input.data("cart-detail-id");
        const totalForItem = pricePerItem * value;

        $(`p[data-cart-detail-id='${cartDetailId}']`).text(
          formatCurrency(totalForItem)
        );

        updateCheckoutQuantityDisplay(cartDetailId, value);

        // Lưu số lượng mới
        saveQuantity(cartDetailId, value);

        updateTotal();
      }
    });

  // Áp dụng các trạng thái đã lưu ngay khi trang load xong
  applySavedSelections();
  applySavedQuantities();
  bindSelectionEvents();

  // Khi submit checkout form, xóa localStorage (vì server sẽ xử lý theo dữ liệu server side)
  $(
    "form[action='/order/create'], form[action='/order/place-order'], #checkoutForm"
  ).on("submit", function () {
    clearSavedQuantities();
  });
});
