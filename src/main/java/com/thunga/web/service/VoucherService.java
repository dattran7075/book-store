package com.thunga.web.service;

import com.thunga.web.entity.Voucher;
import com.thunga.web.repository.VoucherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class VoucherService {

    @Autowired
    private VoucherRepository voucherRepository;

    // =====================================================
    // BUSINESS RULE - DISCOUNT LIMIT
    // =====================================================
    /**
     * Voucher áp dụng cho toàn đơn:
     * - discount_value ≤ min_order × 20%
     */
    private static final double MAX_DISCOUNT_PERCENT = 0.20;

    public List<Voucher> findAll() {
        return voucherRepository.findAll();
    }

    public Voucher findById(Integer id) {
        return voucherRepository.findById(id).orElse(null);
    }

    public Voucher findByCode(String code) {
        return voucherRepository.findByCode(code).orElse(null);
    }

    public Page<Voucher> findByLimit(Integer page, Integer limit, String sortBy) {
        Pageable paging;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            paging = PageRequest.of(page, limit, Sort.by(sortBy));
        } else {
            paging = PageRequest.of(page, limit);
        }
        return voucherRepository.findAll(paging);
    }

    public Voucher save(Voucher voucher) {
        return voucherRepository.save(voucher);
    }

    public void delete(Voucher voucher) {
        voucherRepository.delete(voucher);
    }

    // =====================================================
    // VALIDATION
    // =====================================================

    public void validateNewVoucher(String code, String name, Double discountValue,
                                   LocalDate startDate, LocalDate endDate,
                                   Integer maxUsage, Double minOrder) {

        // Validate code
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Voucher code cannot be empty");
        }

        code = code.trim();

        if (!code.matches("^SALE\\d{2}$")) {
            throw new IllegalArgumentException("Voucher code must be in format SALEXX (e.g., SALE10, SALE35)");
        }

        List<Voucher> existing = voucherRepository.findByCodeIgnoreCase(code);
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException("Voucher code already exists");
        }

        // Validate name
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Voucher name cannot be empty");
        }

        // Validate min order TRƯỚC discount value
        if (minOrder == null || minOrder <= 0) {
            throw new IllegalArgumentException("Min order value must be greater than 0");
        }

        // Validate discount value
        if (discountValue == null || discountValue <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than 0");
        }

        // ===== BUSINESS RULE: Discount limit =====
        // discount_value ≤ min_order × 20%
        double maxAllowed = minOrder * MAX_DISCOUNT_PERCENT;
        if (discountValue > maxAllowed) {
            throw new IllegalArgumentException(
                    String.format("Discount value cannot exceed 20%% of min order (max: %,.0f VND)",
                            maxAllowed)
            );
        }

        // Validate dates
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be empty");
        }

        LocalDate currentDate = LocalDate.now();
        if (startDate.isBefore(currentDate)) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after or equal to start date");
        }

        // Validate max usage
        if (maxUsage == null || maxUsage <= 0) {
            throw new IllegalArgumentException("Max usage must be greater than 0");
        }
    }

    // =====================================================
    // VALIDATION EDIT
    // =====================================================

    public void validateEditVoucher(Integer id, String name, Double discountValue,
                                    LocalDate startDate, LocalDate endDate,
                                    Integer maxUsage, Double minOrder) {

        Voucher voucher = voucherRepository.findById(id).orElse(null);
        if (voucher == null) {
            throw new IllegalArgumentException("Voucher not found");
        }

        LocalDate currentDate = LocalDate.now();
        Integer usedCount = voucher.getUsedCount() != null ? voucher.getUsedCount() : 0;

        // Validate name (luôn được edit)
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Voucher name cannot be empty");
        }

        // Validate end_date (luôn được edit nhưng phải >= current_date)
        if (endDate == null) {
            throw new IllegalArgumentException("End date cannot be empty");
        }

        if (endDate.isBefore(currentDate)) {
            throw new IllegalArgumentException("End date cannot be in the past");
        }

        // Validate max_usage (luôn được edit nhưng phải >= used_count)
        if (maxUsage == null || maxUsage <= 0) {
            throw new IllegalArgumentException("Max usage must be greater than 0");
        }

        if (maxUsage < usedCount) {
            throw new IllegalArgumentException("Max usage cannot be less than used count (" + usedCount + ")");
        }

        // ===== Validate discount_value và min_order (chỉ khi used_count = 0) =====
        if (usedCount > 0) {
            if (!discountValue.equals(voucher.getDiscountValue())) {
                throw new IllegalArgumentException("Cannot change discount value when voucher has been used");
            }

            if (!minOrder.equals(voucher.getMinOrder())) {
                throw new IllegalArgumentException("Cannot change min order when voucher has been used");
            }
        } else {
            // Validate discount_value
            if (discountValue == null || discountValue <= 0) {
                throw new IllegalArgumentException("Discount value must be greater than 0");
            }

            // ===== BUSINESS RULE: Discount limit =====
            double maxAllowed = minOrder * MAX_DISCOUNT_PERCENT;
            if (discountValue > maxAllowed) {
                throw new IllegalArgumentException(
                        String.format("Discount value cannot exceed 20%% of min order (max: %,.0f VND)",
                                maxAllowed)
                );
            }
        }

        // Validate start_date (chỉ được edit khi current_date < start_date)
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be empty");
        }

        if (!startDate.equals(voucher.getStartDate())) {
            if (!currentDate.isBefore(voucher.getStartDate())) {
                throw new IllegalArgumentException("Cannot change start date when voucher has already started");
            }

            if (startDate.isBefore(currentDate)) {
                throw new IllegalArgumentException("Start date cannot be in the past");
            }

            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("End date must be after or equal to start date");
            }
        }
    }

    // =====================================================
    // DELETE Voucher
    // =====================================================

    /**
     * Xóa Voucher khỏi hệ thống
     * Điều kiện:
     * - used_count = 0 (chưa có customer nào sử dụng)
     * <p>
     * LƯU Ý: Voucher chỉ bị chặn xóa khi đã có customer sử dụng (used_count > 0).
     * Việc voucher_id xuất hiện trong order_detail KHÔNG ngăn cản xóa.
     */
    public void deleteVoucher(Integer id) {
        Voucher voucher = voucherRepository.findById(id).orElse(null);
        if (voucher == null) {
            throw new IllegalArgumentException("Voucher not found");
        }

        if (voucher.getUsedCount() != null && voucher.getUsedCount() > 0) {
            throw new IllegalArgumentException("Cannot delete voucher that has been used (used count: " + voucher.getUsedCount() + ")");
        }
        voucherRepository.delete(voucher);
    }
    // =====================================================
    // Voucher VALIDATION & CALCULATION
    // =====================================================

    /**
     * Kiểm tra voucher có hợp lệ hay không (cho toàn đơn hàng)
     */
    public boolean isVoucherValid(Voucher voucher, Double orderTotal) {
        if (voucher == null) {
            return false;
        }

        LocalDate currentDate = LocalDate.now();

        if (!"ACTIVE".equals(voucher.getStatus())) {
            return false;
        }

        if (currentDate.isBefore(voucher.getStartDate()) ||
                currentDate.isAfter(voucher.getEndDate())) {
            return false;
        }

        if (voucher.getUsedCount() >= voucher.getMaxUsage()) {
            return false;
        }

        if (orderTotal < voucher.getMinOrder()) {
            return false;
        }

        return true;
    }

    /**
     * Áp dụng voucher cho đơn hàng (tăng used_count)
     */
    public void applyVoucher(Voucher voucher) {
        if (voucher != null) {
            voucher.setUsedCount(voucher.getUsedCount() + 1);
            voucherRepository.save(voucher);
        }
    }

    /**
     * Tính discount cho toàn đơn
     */
    public Double calculateOrderDiscount(Voucher voucher, Double orderTotal) {
        if (!isVoucherValid(voucher, orderTotal)) {
            return 0.0;
        }
        return voucher.getDiscountValue();
    }

    public List<Voucher> findAllActiveVouchers() {
        List<Voucher> allVouchers = voucherRepository.findAll();
        List<Voucher> activeVouchers = new ArrayList<>();

        LocalDate today = LocalDate.now();

        for (Voucher voucher : allVouchers) {
            if ("ACTIVE".equals(voucher.getStatus())) {
                if (!today.isBefore(voucher.getStartDate()) &&
                        !today.isAfter(voucher.getEndDate())) {
                    activeVouchers.add(voucher);
                }
            }
        }
        return activeVouchers;
    }
}