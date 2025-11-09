package com.group10.furniture_store.service;

//them thư viện
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.group10.furniture_store.domain.Cart;
import com.group10.furniture_store.domain.CartDetails;
import com.group10.furniture_store.domain.Order;
import com.group10.furniture_store.domain.OrderDetail;
import com.group10.furniture_store.domain.Product;
import com.group10.furniture_store.domain.User;
import com.group10.furniture_store.domain.Warehouse;
import com.group10.furniture_store.repository.CartDetailsRepository;
import com.group10.furniture_store.repository.CartRepository;
import com.group10.furniture_store.repository.OrderDetailRepository;
import com.group10.furniture_store.repository.OrderRepository;
import com.group10.furniture_store.repository.ProductRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Service
public class ProductService {
    //=====================thêm 3 dòng dưới=======================
    private static final Map<String, double[]> PRICE_RANGE_MAP = createPriceRangeMap();
    private static final Map<String, Set<String>> FACTORY_SYNONYMS = createFactorySynonyms();
    private static final Map<String, Set<String>> TARGET_KEYWORDS = createTargetKeywordMap();

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartDetailsRepository cartDetailsRepository;
    private final UserService userService;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private WarehouseService warehouseService;

    public ProductService(ProductRepository productRepository, CartRepository cartRepository,
            CartDetailsRepository cartDetailsRepository, UserService userService, OrderRepository orderRepository,
            OrderDetailRepository orderDetailRepository, WarehouseService warehouseService) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.cartDetailsRepository = cartDetailsRepository;
        this.userService = userService;
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.warehouseService = warehouseService;
    }

    public Page<Product> getAllProducts(Pageable pageable) {
        return this.productRepository.findAll(pageable);
    }

    public Product handleSaveProduct(Product product) {
        return this.productRepository.save(product);
    }

    public Product getProductById(long id) {
        Optional<Product> productOptional = this.productRepository.findById(id);
        Product product = productOptional.isPresent() ? productOptional.get() : null;
        return product;
    }

    public void handleDeleteProduct(long id) {
        this.productRepository.deleteById(id);
    }

    public void handleAddProductToCart(String email, long productId, HttpSession session, long quantity) {
        User user = this.userService.getUserByEmail(email);
        if (user != null) {
            // check user đã có Cart chưa ? Nếu chưa => Tạo mới
            Cart cart = this.cartRepository.findByUser(user);
            if (cart == null) {
                // tạo mới cart khi user chưa có cart
                Cart otherCart = new Cart();
                otherCart.setUser(user);
                otherCart.setSum(0L);

                cart = this.cartRepository.save(otherCart);
            }

            // save cart detail
            // tìm productById

            Optional<Product> producOptional = this.productRepository.findById(productId);
            if (producOptional.isPresent()) {
                Product pr = producOptional.get();

                // check sản phẩm đã từng được thêm vào giỏ hàng trước đó chưa
                CartDetails oldCartDetail = this.cartDetailsRepository.findByCartAndProduct(cart, pr);
                //

                // Nếu chưa được thêm , thì phải thêm vào giỏ
                if (oldCartDetail == null) {
                    CartDetails cd = new CartDetails();

                    cd.setCart(cart);
                    cd.setProduct(pr);
                    cd.setPrice(pr.getPrice());
                    cd.setQuantity(quantity);

                    this.cartDetailsRepository.save(cd);

                    // update cart(sum)
                    Long newSum = cart.getSum() + 1L;
                    cart.setSum(newSum);
                    this.cartRepository.save(cart);
                    // update session(sum)
                    session.setAttribute("sum", newSum);
                }
                //

                // Nếu sp đã được thêm vào giỏ hàng trước đó rồi , thì update quantity cho nó
                else {
                    oldCartDetail.setQuantity(oldCartDetail.getQuantity() + quantity);
                    this.cartDetailsRepository.save(oldCartDetail);
                }
                // Lưu cart vào session để thanh toán
                session.setAttribute("cartId", cart.getId());
                session.setAttribute("sum", cart.getSum());
            }
        }
    }

    public void handleRemoveCartDetail(long cartDetailId, HttpSession session) {
        Optional<CartDetails> cartDetailsOptional = this.cartDetailsRepository.findById(cartDetailId);
        if (cartDetailsOptional.isPresent()) {
            CartDetails cartDetails = cartDetailsOptional.get();
            Cart currentCart = cartDetails.getCart();

            // delete cartDetail
            this.cartDetailsRepository.deleteById(cartDetailId);

            // update cart
            if (currentCart.getSum() > 1) {
                Long newSum = currentCart.getSum() - 1L;
                currentCart.setSum(newSum);
                session.setAttribute("sum", newSum);
                this.cartRepository.save(currentCart);
            } else {
                // xóa cả cart nếu không còn sản phẩm
                this.cartRepository.deleteById(currentCart.getId());
                // update session
                session.setAttribute("sum", 0);
            }
        }
    }

    public void handleUpdateCartBeforeCheckout(List<CartDetails> cartDetails) {
        for (CartDetails cd : cartDetails) {
            Optional<CartDetails> cdOptional = this.cartDetailsRepository.findById(cd.getId());
            if (cdOptional.isPresent()) {
                CartDetails currentCartDetails = cdOptional.get();
                currentCartDetails.setQuantity(cd.getQuantity());
                this.cartDetailsRepository.save(currentCartDetails);
            }
        }
    }

    @Transactional
    public void handlePlaceOrder(User user, HttpSession session, String receiverName, String receiverAddress,
            String receiverPhone, String paymentMethod, String uuid, Double totalPrice) {
        // Get cart by user
        Cart cart = this.cartRepository.findByUser(user);
        if (cart != null) {
            List<CartDetails> cartDetails = cart.getCartDetails();
            if (cartDetails != null) {
                // Create order
                Order order = new Order();
                order.setUser(user);
                order.setReceiverName(receiverName);
                order.setReceiverAddress(receiverAddress);
                order.setReceiverPhone(receiverPhone);
                order.setStatus("Chưa được xử lý");
                //

                order.setPaymentMethod(paymentMethod);
                order.setPaymentStatus("Thanh toán thành công");
                order.setPaymentRef(paymentMethod.equals("COD") ? "UNKNOWN" : uuid);
                order.setTotalPrice(totalPrice);
                order = this.orderRepository.save(order);

                // create orderDetail

                for (CartDetails cd : cartDetails) {
                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setOrder(order);
                    orderDetail.setProduct(cd.getProduct());
                    orderDetail.setPrice(cd.getPrice());
                    orderDetail.setQuantity(cd.getQuantity());

                    this.orderDetailRepository.save(orderDetail);
                }

                // Delete cart_detail and cart sau đó
                for (CartDetails cd : cartDetails) {
                    // xóa từng cartdetails trong cart trước rồi mới xóa cart
                    this.cartDetailsRepository.deleteById(cd.getId());
                }
                this.cartRepository.deleteById(cart.getId());
                // update session
                session.setAttribute("sum", 0);
            }
        }
    }

    public Cart fetchCartByUser(User user) {
        return this.cartRepository.findByUser(user);
    }

    public void updatePaymentStatus(String paymentRef, String paymentStatus) {
        Order order = this.orderRepository.findByPaymentRef(paymentRef);
        order.setPaymentStatus(paymentStatus);
    }
//=====================sửa thêm start==========================
    public List<Product> filterProducts(List<String> factories, List<String> targets, List<String> priceRanges,
            String sortMode) {
        List<Product> allProducts = this.productRepository.findAll();
        if (allProducts.isEmpty()) {
            return allProducts;
        }

        Set<String> factorySet = normalizeValues(factories);
        Set<String> targetSet = normalizeValues(targets);
        Set<String> priceSet = normalizeValues(priceRanges);

        List<Product> filtered = allProducts.stream()
                .filter(product -> matchesProduct(product, factorySet, targetSet, priceSet))
                .collect(Collectors.toList());

        sortProducts(filtered, sortMode);
        return filtered;
    }

    private boolean matchesProduct(Product product, Set<String> factorySet, Set<String> targetSet, Set<String> priceSet) {
        String normalizedFactory = normalizeValue(product.getFactory());
        String normalizedTarget = normalizeValue(product.getTarget());
        String normalizedName = normalizeValue(product.getName());
        String searchableText = buildSearchableKeywords(product);
        return matchesFactory(normalizedFactory, factorySet)
                && matchesTarget(targetSet, normalizedTarget, normalizedName, searchableText)
                && matchesPrice(product.getPrice(), priceSet);
    }

    private boolean matchesFactory(String normalizedFactory, Set<String> factorySet) {
        if (factorySet.isEmpty()) {
            return true;
        }
        for (String selectedFactory : factorySet) {
            if (matchWithSynonyms(normalizedFactory, null, selectedFactory, FACTORY_SYNONYMS)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTarget(Set<String> selectedTargets, String normalizedTargetField, String normalizedName,
            String searchableText) {
        if (selectedTargets.isEmpty()) {
            return true;
        }
        for (String selected : selectedTargets) {
            if ("KHAC".equals(selected)) {
                if (matchesOtherCategories(normalizedTargetField, normalizedName, searchableText)) {
                    return true;
                }
                continue;
            }
            if (matchesTargetCode(selected, normalizedTargetField, normalizedName, searchableText)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTargetCode(String selectedTarget, String normalizedTargetField, String normalizedName,
            String searchableText) {
        if (selectedTarget == null) {
            return false;
        }
        Set<String> keywords = TARGET_KEYWORDS.get(selectedTarget);
        if (keywords == null || keywords.isEmpty()) {
            return selectedTarget.equals(normalizedTargetField);
        }

        if (containsKeyword(normalizedName, keywords)) {
            return true;
        }

        if (normalizedName != null && !normalizedName.isEmpty()) {
            return false;
        }

        if (containsKeyword(searchableText, keywords)) {
            return true;
        }

        return selectedTarget.equals(normalizedTargetField);
    }

    private boolean matchesOtherCategories(String normalizedTargetField, String normalizedName, String searchableText) {
        for (String code : TARGET_KEYWORDS.keySet()) {
            if ("KHAC".equals(code)) {
                continue;
            }
            if (matchesTargetCode(code, normalizedTargetField, normalizedName, searchableText)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesPrice(double price, Set<String> priceSet) {
        if (priceSet.isEmpty()) {
            return true;
        }
        for (String code : priceSet) {
            double[] range = PRICE_RANGE_MAP.get(code);
            if (range == null) {
                continue;
            }
            if (price >= range[0] && price < range[1]) {
                return true;
            }
        }
        return false;
    }

    private void sortProducts(List<Product> products, String sortMode) {
        if (products == null || products.isEmpty()) {
            return;
        }
        Comparator<Product> comparator = Comparator.comparingLong(Product::getId);
        if ("gia-tang-dan".equalsIgnoreCase(sortMode)) {
            comparator = Comparator.comparingDouble(Product::getPrice);
        } else if ("gia-giam-dan".equalsIgnoreCase(sortMode)) {
            comparator = Comparator.comparingDouble(Product::getPrice).reversed();
        }
        products.sort(comparator);
    }

    private Set<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values.stream()
                .map(ProductService::normalizeValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Map<String, double[]> createPriceRangeMap() {
        Map<String, double[]> ranges = new HashMap<>();
        putPriceRange(ranges, "duoi-10-trieu", 0d, 10_000_000d);
        putPriceRange(ranges, "10-15-trieu", 10_000_000d, 15_000_000d);
        putPriceRange(ranges, "15-20-trieu", 15_000_000d, 20_000_000d);
        putPriceRange(ranges, "tren-20-trieu", 20_000_000d, Double.POSITIVE_INFINITY);
        return Collections.unmodifiableMap(ranges);
    }

    private static Map<String, Set<String>> createFactorySynonyms() {
        Map<String, List<String>> raw = new HashMap<>();
        raw.put("FACTORY1", List.of("FACTORY1", "CTY1", "CONG-TY-1", "NHA-MAY-1", "HANG1"));
        raw.put("FACTORY2", List.of("FACTORY2", "CTY2", "CONG-TY-2", "NHA-MAY-2", "HANG2"));
        raw.put("FACTORY3", List.of("FACTORY3", "CTY3", "CONG-TY-3", "NHA-MAY-3", "HANG3"));
        return normalizeSynonymMap(raw);
    }

    // private static Map<String, Set<String>> createTargetKeywordMap() {
    //     Map<String, List<String>> raw = new HashMap<>();
    //     raw.put("GAMING", List.of("GHE", "GHE-AN", "GHE-AN-BOC-NI", "GHE-XOAY", "GHE-CONG-THAI-HOC", "SOFA", "SALON",
    //             "GHE-THU-GIAN", "BO-BAN-GHE"));
    //     raw.put("SINHVIEN-VANPHONG",
    //             List.of("BAN", "BAN-LAM-VIEC", "BAN-HOC", "BAN-MAY-TINH", "BAN-VAN-PHONG", "BAN-AN", "BAN-DAU-GIUONG",
    //                     "BAN-TRANG-DIEM", "BO-BAN-GHE"));
    //     raw.put("MONG-NHE", List.of("TU", "TU-QUAN-AO", "TU-GO", "TU-NHUA", "TU-DAU-GIUONG", "TU-LI"));
    //     raw.put("DOANH-NHAN", List.of("KE", "KE-SACH", "KE-TRANG-TRI", "KE-GO", "KE-TIVI"));
    //     raw.put("THIET-KE-DO-HOA", List.of("GIUONG", "GIUONG-NGU", "BO-GIUONG", "GIUONG-GO", "GIUONG-CAO"));
    //     return normalizeSynonymMap(raw);
    // }

    private static Map<String, Set<String>> createTargetKeywordMap() {
        Map<String, List<String>> raw = new HashMap<>();
        raw.put("GAMING",
                List.of("GHE", "GHE-XOAY", "GHE-CONG-THAI-HOC", "GHE-GAMING", "GHE-VAN-PHONG", "GHE-TRE-EM",
                        "GHE-THU-GIAN", "SOFA", "SALON", "GHE-SOFA", "BO-BAN-GHE"));
        raw.put("SINHVIEN-VANPHONG",
                List.of("BAN", "BAN-AN", "BAN-LAM-VIEC", "BAN-HOC", "BAN-MAY-TINH", "BAN-TRANG-DIEM", "BAN-TRA",
                        "BAN-DAU-GIUONG", "BAN-CAFE", "BAN-SOFA", "BAN-THONG-MINH", "BO-BAN-GHE"));
        raw.put("MONG-NHE",
                List.of("TU", "TU-QUAN-AO", "TU-GO", "TU-NHUA", "TU-DAU-GIUONG", "TU-GIAY", "TU-BEP", "TU-TRANG-TRI"));
        raw.put("DOANH-NHAN",
                List.of("KE", "KE-SACH", "KE-GO", "KE-TIVI", "KE-TRANG-TRI", "KE-GOC", "KE-GIAY", "KE-TREO"));
        raw.put("THIET-KE-DO-HOA",
                List.of("GIUONG", "GIUONG-NGU", "GIUONG-GO", "GIUONG-THONG-MINH", "BO-GIUONG"));
        return normalizeSynonymMap(raw);
    }

    private static Map<String, Set<String>> normalizeSynonymMap(Map<String, List<String>> rawMap) {
        Map<String, Set<String>> normalized = new HashMap<>();
        rawMap.forEach((key, values) -> {
            String normalizedKey = normalizeValue(key);
            if (normalizedKey == null) {
                return;
            }
            Set<String> normalizedValues = values.stream()
                    .map(ProductService::normalizeValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            normalized.put(normalizedKey, normalizedValues);
        });
        return Collections.unmodifiableMap(normalized);
    }

    private static boolean matchWithSynonyms(String normalizedFieldValue, String fallbackSearchText,
            String selectedCode, Map<String, Set<String>> synonymsMap) {
        Set<String> synonyms = synonymsMap.getOrDefault(selectedCode, Collections.singleton(selectedCode));
        boolean hasFieldValue = normalizedFieldValue != null && !normalizedFieldValue.isEmpty();
        for (String synonym : synonyms) {
            if (synonym == null || synonym.isEmpty()) {
                continue;
            }
            if (hasFieldValue && valueMatches(normalizedFieldValue, synonym)) {
                return true;
            }
            if (fallbackSearchText != null && fallbackSearchText.contains(synonym)) {
                return true;
            }
        }
        return false;
    }
    private static boolean valueMatches(String value, String keyword) {
        return value.equals(keyword) || value.contains(keyword);
    }

    private static boolean containsKeyword(String text, Set<String> keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String buildSearchableKeywords(Product product) {
        StringBuilder builder = new StringBuilder();
        appendNormalized(builder, product.getName());
        appendNormalized(builder, product.getShortDesc());
        appendNormalized(builder, product.getDetailDesc());
        return builder.length() == 0 ? null : builder.toString();
    }

    private static void appendNormalized(StringBuilder builder, String source) {
        String normalized = normalizeValue(source);
        if (normalized == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('-');
        }
        builder.append(normalized);
    }

    private static void putPriceRange(Map<String, double[]> ranges, String code, double min, double max) {
        String normalizedCode = normalizeValue(code);
        if (normalizedCode != null) {
            ranges.put(normalizedCode, new double[] { min, max });
        }
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }
    //=====================sửa thêm end==========================

    public List<Product> searchByName(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        System.out.println("Searching for: " + keyword);
        try {
            List<Product> results = productRepository.findByNameContainingIgnoreCaseOrderByIdDesc(keyword);
            System.out.println("Search results: " + results.size());

            // Load warehouse cho mỗi product
            results.forEach(p -> {
                Warehouse warehouse = warehouseService.getWarehouseByProductId(p.getId());
                if (warehouse != null) {
                    p.setWarehouse(warehouse);
                    System.out.println("Product " + p.getName() + " has stock: " + warehouse.getQuantity());
                } else {
                    System.out.println("Product " + p.getName() + " has no warehouse");
                }
            });

            return results;
        } catch (Exception e) {
            System.err.println("Error searching: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
