package com.group10.furniture_store.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.group10.furniture_store.domain.Product;
import com.group10.furniture_store.domain.Warehouse;
import com.group10.furniture_store.repository.WarehouseRepository;

@Service
public class WarehouseService {
    @Autowired
    private WarehouseRepository warehouseRepository;

    public Warehouse getWarehouseByProductId(Long productId) {
        System.out.println("Getting warehouse for product: " + productId);
        Optional<Warehouse> warehouse = warehouseRepository.findByProductId(productId);
        if (warehouse.isPresent()) {
            System.out.println("Found warehouse with quantity: " + warehouse.get().getQuantity());
            return warehouse.get();
        } else {
            System.out.println("Warehouse not found for product: " + productId);
            return null;
        }
    }

    public Integer getStockQuantity(Long productId) {
        System.out.println("Getting stock quantity for product: " + productId);
        Warehouse warehouse = getWarehouseByProductId(productId);
        Integer quantity = warehouse != null ? warehouse.getQuantity() : 0;
        System.out.println("Stock quantity: " + quantity);
        return quantity;
    }

    public void updateStockQuantity(Long productId, Integer newQuantity) {
        Optional<Warehouse> warehouse = warehouseRepository.findByProductId(productId);
        if (warehouse.isPresent()) {
            Warehouse w = warehouse.get();
            w.setQuantity(newQuantity);
            w.setLastUpdated(LocalDateTime.now());
            warehouseRepository.save(w);
        }
    }

    public void decreaseStock(Long productId, Integer quantity) {
        Integer currentStock = getStockQuantity(productId);
        if (currentStock >= quantity) {
            updateStockQuantity(productId, currentStock - quantity);
        }
    }

    public void increaseStock(Long productId, Integer quantity) {
        Integer currentStock = getStockQuantity(productId);
        updateStockQuantity(productId, currentStock + quantity);
    }

    public Warehouse getOrCreateWarehouse(Product product) {
        Optional<Warehouse> warehouse = warehouseRepository.findByProductId(product.getId());
        if (warehouse.isEmpty()) {
            Warehouse newWarehouse = new Warehouse();
            newWarehouse.setProduct(product);
            newWarehouse.setQuantity(0);
            newWarehouse.setLastUpdated(LocalDateTime.now());
            return warehouseRepository.save(newWarehouse);
        }
        return warehouse.get();
    }
}
