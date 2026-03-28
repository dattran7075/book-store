package com.thunga.web.service;

import com.thunga.web.entity.*;
import com.thunga.web.repository.CommentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class CommentService {

    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private BookService bookService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private UserService userService;

    // =========================================================================
    // CUSTOMER: Save new comment
    // =========================================================================

    /**
     * Status rules:
     * - star-only (no content) → APPROVED immediately
     * - has content→ PENDING (admin must approve)
     * <p>
     * Hỗ trợ mua nhiều lần: user được review thêm nếu số lần mua > số lần đã review
     */
    public Comment save(Comment comment, HttpServletRequest request) {
        Integer bookId = Integer.valueOf(request.getParameter("bookId"));
        String username = request.getParameter("username");

        Account account = accountService.findByUsername(username);
        User user = userService.findByAccount(account);
        Book book = bookService.findById(bookId);

        long completedPurchaseCount = countCompletedPurchasesForBook(user, book);
        if (completedPurchaseCount == 0) {
            throw new IllegalStateException(
                    "You can only review books that you have purchased and received (Completed orders only)!");
        }

        long existingReviewCount = commentRepository.countByUserAndBook(user, book);

        if (existingReviewCount >= completedPurchaseCount) {
            throw new IllegalStateException(
                    "You have already reviewed this book for all your purchases. " +
                            "Buy the book again to leave another review!");
        }

        if (comment.getStar() == null || comment.getStar() < 1 || comment.getStar() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        String content = comment.getContent();
        if (content != null && content.trim().length() > 5000) {
            throw new IllegalArgumentException("Comment content cannot exceed 5000 characters");
        }

        OrderDetail completedOrderDetail = findUnreviewedCompletedOrderDetail(user, book);

        boolean hasContent = content != null && !content.trim().isEmpty();
        comment.setBook(book);
        comment.setUser(user);
        comment.setOrderDetail(completedOrderDetail);
        comment.setContent(hasContent ? content.trim() : "");
        comment.setCreated_at(new Date());
        comment.setUpdated_at(new Date());
        comment.setStatus(hasContent ? "PENDING" : "APPROVED");

        return commentRepository.save(comment);
    }

    // =========================================================================
    // CUSTOMER: Update existing comment
    // =========================================================================

    /**
     * If previously APPROVED and now has new content → PENDING again.
     * "Edited after approval" is detected via: status=PENDING && updated_at > created_at.
     */
    public Comment updateComment(Integer commentId, String content, Integer star,
                                 HttpServletRequest request) {
        String username = request.getRemoteUser();
        if (username == null || username.isEmpty()) throw new IllegalStateException("User not authenticated");

        Account account = accountService.findByUsername(username);
        if (account == null) throw new IllegalStateException("Account not found");

        User currentUser = userService.findByAccount(account);
        if (currentUser == null) throw new IllegalStateException("User not found");

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalStateException("Comment not found"));

        if (!comment.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("You can only edit your own reviews!");
        }

        if (star == null || star < 1 || star > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (content != null && content.trim().length() > 5000) {
            throw new IllegalArgumentException("Comment content cannot exceed 5000 characters");
        }

        boolean hasContent = content != null && !content.trim().isEmpty();
        String prevStatus = comment.getStatus();

        comment.setContent(hasContent ? content.trim() : "");
        comment.setStar(star);
        comment.setUpdated_at(new Date());

        if (!hasContent) {
            comment.setStatus("APPROVED");
        } else if ("APPROVED".equals(prevStatus)) {
            comment.setStatus("PENDING");
        }

        return commentRepository.save(comment);
    }

    // =========================================================================
    // ADMIN: list / update status
    // =========================================================================

    public Page<Comment> findAllPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return commentRepository.findAll(pageable);
    }

    public Page<Comment> findByStatusPaged(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return commentRepository.findByStatus(status, pageable);
    }

    /**
     * Admin changes status.
     * Rules:
     * PENDING  → HIDDEN   ✓
     * PENDING  → APPROVED ✓
     * APPROVED → PENDING  ✓  (only if updated_at > created_at, i.e. customer edited it)
     * APPROVED → HIDDEN   ✓
     * HIDDEN   → APPROVED ✓
     * HIDDEN   → PENDING  ✗
     */
    public Comment adminUpdateStatus(Integer commentId, String newStatus) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalStateException("Comment not found"));

        String current = comment.getStatus();

        boolean wasEdited = comment.getUpdated_at() != null
                && comment.getCreated_at() != null
                && comment.getUpdated_at().after(comment.getCreated_at());

        boolean allowed = switch (current) {
            case "PENDING" -> "APPROVED".equals(newStatus) || "HIDDEN".equals(newStatus);
            case "APPROVED" -> "HIDDEN".equals(newStatus) || ("PENDING".equals(newStatus) && wasEdited);
            case "HIDDEN" -> "APPROVED".equals(newStatus) || "PENDING".equals(newStatus);
            default -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Cannot change status from " + current + " to " + newStatus);
        }

        comment.setStatus(newStatus);
        return commentRepository.save(comment);
    }

    /**
     * Only APPROVED comments – shown to all customers
     */
    public List<Comment> getApprovedCommentsByBook(Book book) {
        try {
            if (book == null || book.getId() == null) return new ArrayList<>();
            return commentRepository.findByBookAndStatus(book, "APPROVED");
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * TẤT CẢ comments của user cho cuốn sách (dùng cho tab "My Review")
     * Sắp xếp theo created_at DESC để comment mới nhất lên đầu
     */
    public List<Comment> getAllMyCommentsForBook(User user, Book book) {
        if (user == null || book == null) return new ArrayList<>();
        return commentRepository.findByUserAndBookOrderByCreatedAtDesc(user, book);
    }

    /**
     * Comment MỚI NHẤT của user cho cuốn sách (backward compatibility)
     */
    public Optional<Comment> getMyCommentForBook(User user, Book book) {
        List<Comment> comments = getAllMyCommentsForBook(user, book);
        return comments.isEmpty() ? Optional.empty() : Optional.of(comments.get(0));
    }

    /**
     * Kiểm tra user có thể review thêm không:
     * canReview = true nếu số lần mua (Completed) > số lần đã review
     */
    public boolean canUserReview(User user, Book book) {
        if (user == null || book == null) return false;
        long purchaseCount = countCompletedPurchasesForBook(user, book);
        if (purchaseCount == 0) return false;
        long reviewCount = commentRepository.countByUserAndBook(user, book);
        return reviewCount < purchaseCount;
    }

    public boolean hasUserCommented(User user, Book book) {
        if (user == null || book == null) return false;
        return commentRepository.existsByUserAndBook(user, book);
    }


    public Double getAverageRating(Book book) {
        try {
            List<Comment> comments = getApprovedCommentsByBook(book);
            if (comments == null || comments.isEmpty()) return 0.0;
            return comments.stream()
                    .filter(c -> c.getStar() != null)
                    .mapToInt(Comment::getStar)
                    .average()
                    .orElse(0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public List<Comment> getTopRatedComments(int limit) {
        try {
            List<Comment> all = commentRepository.findByStatus("APPROVED",
                    PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "star"))).getContent();
            return all.size() > limit ? all.subList(0, limit) : all;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Đếm số lần user đã mua và nhận cuốn sách này (đơn Completed)
     */
    private long countCompletedPurchasesForBook(User user, Book book) {
        if (user == null || book == null) return 0;
        return commentRepository.countCompletedOrdersWithBook(user, book);
    }

    /**
     * Tìm OrderDetail của đơn Completed chưa được gắn review (dùng khi tạo comment mới)
     */
    private OrderDetail findUnreviewedCompletedOrderDetail(User user, Book book) {
        if (user == null || user.getOrderList() == null) return null;

        List<Comment> existingComments = commentRepository
                .findByUserAndBookOrderByCreatedAtDesc(user, book);
        List<Integer> reviewedOrderDetailIds = new ArrayList<>();
        for (Comment c : existingComments) {
            if (c.getOrderDetail() != null) {
                reviewedOrderDetailIds.add(c.getOrderDetail().getId());
            }
        }
        for (Order order : user.getOrderList()) {
            if ("Completed".equals(order.getStatus()) && "PAID".equals(order.getPayment_status())
                    && order.getOrderDetailList() != null) {

                for (OrderDetail od : order.getOrderDetailList()) {
                    if (od.getBook().getId().equals(book.getId())
                            && !reviewedOrderDetailIds.contains(od.getId())) {
                        return od;
                    }
                }
            }
        }
        for (Order order : user.getOrderList()) {
            if ("Completed".equals(order.getStatus()) && "PAID".equals(order.getPayment_status())
                    && order.getOrderDetailList() != null) {
                for (OrderDetail od : order.getOrderDetailList()) {
                    if (od.getBook().getId().equals(book.getId()))
                        return od;
                }
            }
        }
        return null;
    }
}