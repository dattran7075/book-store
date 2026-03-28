package com.thunga.web.repository;

import com.thunga.web.entity.Book;
import com.thunga.web.entity.Comment;
import com.thunga.web.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    boolean existsByUserAndBook(User user, Book book);

    // ── Đếm số lượng comment của user cho một cuốn sách (mọi trạng thái)
    long countByUserAndBook(User user, Book book);

    List<Comment> findByBook(Book book);

    Optional<Comment> findByIdAndUser(Integer commentId, User user);

    Optional<Comment> findByUserAndBook(User user, Book book);

    // ── Lấy comment mới nhất của user cho một cuốn sách
    @Query("SELECT c FROM Comment c WHERE c.user = :user AND c.book = :book ORDER BY c.created_at DESC")
    List<Comment> findByUserAndBookOrderByCreatedAtDesc(@Param("user") User user, @Param("book") Book book);

    // ── Admin management ──────────────────────────────────────────────────────

    /**
     * All comments paged (admin list)
     */
    Page<Comment> findAll(Pageable pageable);

    /**
     * Filter by status
     */
    Page<Comment> findByStatus(String status, Pageable pageable);

    /**
     * Public-facing: only APPROVED comments
     */
    List<Comment> findByBookAndStatus(Book book, String status);

    /**
     * A user's own comments (any status) – for "My Reviews" tab
     */
    @Query("SELECT c FROM Comment c WHERE c.user = :user ORDER BY c.created_at DESC")
    List<Comment> findByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT COUNT(DISTINCT o) FROM Order o " +
            "JOIN o.orderDetailList od " +
            "WHERE o.user = :user " +
            "AND od.book = :book " +
            "AND o.status = 'Completed' " +
            "AND o.payment_status = 'PAID'")
    int countCompletedOrdersWithBook(@Param("user") User user, @Param("book") Book book);
}