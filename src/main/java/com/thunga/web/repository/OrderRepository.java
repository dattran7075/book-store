package com.thunga.web.repository;

import com.thunga.web.entity.Order;
import com.thunga.web.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    Page<Order> findByUser(User user, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.admin.id = :adminId")
    Page<Order> findByAdminId(@Param("adminId") Integer adminId, Pageable pageable);

    // Filter by status
    Page<Order> findByStatus(String status, Pageable pageable);

    // Filter by payment status
    @Query("SELECT o FROM Order o WHERE o.payment_status = :paymentStatus")
    Page<Order> findByPaymentStatus(@Param("paymentStatus") String paymentStatus, Pageable pageable);

    // Filter by both status and payment status
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.payment_status = :paymentStatus")
    Page<Order> findByStatusAndPaymentStatus(@Param("status") String status,
                                             @Param("paymentStatus") String paymentStatus,
                                             Pageable pageable);

    // Count by status
    Long countByStatus(String status);
}