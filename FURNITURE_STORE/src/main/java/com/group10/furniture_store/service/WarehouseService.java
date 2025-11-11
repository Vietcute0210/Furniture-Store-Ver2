package com.group10.furniture_store.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.group10.furniture_store.domain.Product;
import com.group10.furniture_store.domain.Warehouse;
import com.group10.furniture_store.repository.WarehouseRepository;

import jakarta.transaction.Transactional;

@Service
public class WarehouseService {
    @Autowired
    private WarehouseRepository warehouseRepository;

    public Warehouse getWarehouseByProductId(Long productId) {
        System.out.println("Getting warehouse for product: " + productId);
        Optional<Warehouse> warehouse = warehouseRepository.findByProductId(productId);
        if (warehouse.isPresent()) {
            return warehouse.get();
        } else {
            return null;
        }
    }

    public Long getStockQuantity(Long productId) {
        Warehouse warehouse = getWarehouseByProductId(productId);
        Long quantity = warehouse != null ? warehouse.getQuantity() : 0L;
        return quantity;
    }

    public void updateStockQuantity(Long productId, Long newQuantity) {
        Optional<Warehouse> warehouse = warehouseRepository.findByProductId(productId);
        if (warehouse.isPresent()) {
            Warehouse w = warehouse.get();
            w.setQuantity(newQuantity);
            w.setLastUpdated(LocalDateTime.now());
            warehouseRepository.save(w);
        }
    }

    // Kiểm tra và trừ tồn kho (riêng sp) với pessimistic lock để tránh race
    // condition
    // Trả về true nếu trừ được , false nếu đã hết hàng
    @Transactional
    public boolean decreaseStockSafely(Long productId, Long quantity) {
        Optional<Warehouse> warehouseOptional = warehouseRepository.findByProductIdWithLock(productId);

        if (warehouseOptional.isEmpty()) {
            return false;
        }

        Warehouse warehouse = warehouseOptional.get();

        // Kiểm tra slg sp trong kho có đủ để đơn hàng lấy ko
        if (warehouse.getQuantity() < quantity) {
            return false; // Không đủ hàng
        }

        // Trừ tồn kho
        warehouse.setQuantity(warehouse.getQuantity() - quantity);
        warehouse.setLastUpdated(LocalDateTime.now());
        warehouseRepository.save(warehouse);

        return true;
    }

    // Ktra trong kho có đủ sp ko
    public boolean checkStockAvailability(Long productId, Long quantity) {
        Long currentStock = getStockQuantity(productId);
        return currentStock >= quantity;
    }

    // Ktra và trừ tồn kho sl sp trong đơn hàng
    @Transactional
    public Map<Long, Boolean> decreaseStockForMultipleProducts(Map<Long, Long> productQuantities) {
        Map<Long, Boolean> results = new HashMap<>();

        for (Map.Entry<Long, Long> entry : productQuantities.entrySet()) {
            Long productId = entry.getKey();
            Long quantity = entry.getValue();
            results.put(productId, decreaseStockSafely(productId, quantity));
        }

        return results;
    }

    @Transactional
    public Warehouse getOrCreateWarehouse(Product product) {
        Optional<Warehouse> warehouse = warehouseRepository.findByProductId(product.getId());
        if (warehouse.isEmpty()) {
            Warehouse newWarehouse = new Warehouse();
            newWarehouse.setProduct(product);
            newWarehouse.setQuantity(0L);
            newWarehouse.setLastUpdated(LocalDateTime.now());
            return warehouseRepository.save(newWarehouse);
        }
        return warehouse.get();
    }
}