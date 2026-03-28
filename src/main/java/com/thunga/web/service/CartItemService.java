package com.thunga.web.service;

import com.thunga.web.entity.Book;
import com.thunga.web.entity.CartItem;
import com.thunga.web.entity.User;
import com.thunga.web.repository.CartItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class CartItemService {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private BookService bookService;

    /**
     * Lấy tất cả CartItem của user
     */
    public List<CartItem> getCartItems(User user) {
        return cartItemRepository.findByUser(user);
    }

    /**
     * Thêm sách vào giỏ hàng
     */
    @Transactional
    public CartItem addBookToCart(User user, Integer bookId, Integer quantity) {
        Book book = bookService.findById(bookId);

        if (book == null)
            throw new IllegalArgumentException("Book not found!");

        if (book.getNumber_in_stock() == 0)
            throw new IllegalStateException("Book '" + book.getTitle() + "' is out of stock!");

        if (quantity <= 0)
            throw new IllegalArgumentException("Invalid quantity!");

        if (quantity > book.getNumber_in_stock())
            throw new IllegalStateException("Only " + book.getNumber_in_stock() + " copies available!");

        Optional<CartItem> existingItemOpt = cartItemRepository.findByUserAndBook(user, book);

        int currentQuantity = existingItemOpt.map(CartItem::getQuantity).orElse(0);
        int totalQuantity = currentQuantity + quantity;

        if (totalQuantity > book.getNumber_in_stock()) {
            int availableToAdd = book.getNumber_in_stock() - currentQuantity;
            throw new IllegalStateException(
                    "Cannot add " + quantity + " more. Only " + availableToAdd + " copies available!"
            );
        }

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(totalQuantity);
            existingItem.setUpdatedAt(new Date());
            return cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = new CartItem();
            newItem.setUser(user);
            newItem.setBook(book);
            newItem.setQuantity(quantity);
            newItem.setCreatedAt(new Date());
            newItem.setUpdatedAt(new Date());
            return cartItemRepository.save(newItem);
        }
    }

    public CartItem findByUserAndBook(User user, Book book) {
        return cartItemRepository.findByUserAndBook(user, book).orElse(null);
    }

    public CartItem save(CartItem cartItem) {
        return cartItemRepository.save(cartItem);
    }


    /**
     * Cập nhật số lượng sách trong giỏ
     */
    @Transactional
    public CartItem updateCartItem(User user, Integer bookId, Integer quantity) {
        Book book = bookService.findById(bookId);
        if (book == null)
            throw new IllegalArgumentException("Book not found!");

        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be at least 1!");

        Optional<CartItem> itemOpt = cartItemRepository.findByUserAndBook(user, book);
        if (itemOpt.isPresent()) {
            CartItem item = itemOpt.get();

            if (quantity > book.getNumber_in_stock())
                throw new IllegalStateException(
                        "Only " + book.getNumber_in_stock() + " copies available!"
                );

            item.setQuantity(quantity);
            item.setUpdatedAt(new Date());
            return cartItemRepository.save(item);
        }
        return null;
    }

    /**
     * Xóa sách khỏi giỏ hàng
     */
    @Transactional
    public void removeCartItemByUserAndBook(User user, Integer bookId) {
        try {
            if (user != null && bookId != null) {
                cartItemRepository.deleteByUserAndBook(user.getId(), bookId);
            }
        } catch (Exception e) {
            System.out.println("Error removing cart item: " + e.getMessage());
            throw new RuntimeException("Failed to remove cart item", e);
        }
    }

    /**
     * Xóa toàn bộ giỏ hàng
     */
    @Transactional
    public void clearCart(User user) {
        cartItemRepository.deleteByUser(user);
    }

    /**
     * Tính tổng số tiền trong giỏ hàng
     */
    public Double calculateTotalAmount(User user) {
        return cartItemRepository.sumTotalAmountByUser(user);
    }

    /**
     * Tính tổng số sách trong giỏ hàng
     */
    public Integer calculateTotalBooks(User user) {
        return cartItemRepository.sumQuantityByUser(user);
    }

    /**
     * Kiểm tra giỏ hàng có rỗng không
     */
    public boolean isCartEmpty(User user) {
        return !cartItemRepository.existsByUser(user);
    }
}