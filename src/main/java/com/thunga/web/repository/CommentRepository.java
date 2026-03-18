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

    List<Comment> findByBook(Book book);

    Optional<Comment> findByIdAndUser(Integer commentId, User user);

    Optional<Comment> findByUserAndBook(User user, Book book);

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
}