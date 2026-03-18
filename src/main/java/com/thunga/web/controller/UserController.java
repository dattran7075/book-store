package com.thunga.web.controller;

import com.thunga.web.entity.*;
import com.thunga.web.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
public class UserController {
    @Autowired
    AuthorService authorService;

    @Autowired
    private BookService bookService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private CartItemService cartItemService;

    @Autowired
    private SeriesService seriesService;

    // ========== SHIPPING FEE CONSTANT ==========
    private static final Double DEFAULT_SHIPPING_FEE = 20000.0;

    /**
     * Helper method to add cart info to model
     */
    private void addCartInfoToModel(Model model) {
        User user = userService.getCurrentUserFromContext();
        if (user != null) {
            Integer totalBooks = cartItemService.calculateTotalBooks(user);
            model.addAttribute("cartTotalBooks", totalBooks);

            List<CartItem> cartItems = cartItemService.getCartItems(user);
            model.addAttribute("cartItems", cartItems);
        } else {
            model.addAttribute("cartTotalBooks", 0);
            model.addAttribute("cartItems", new ArrayList<>());
        }
    }

    @PostMapping("/save-comment")
    public String saveOrUpdateComment(
            @RequestParam(required = false) Integer commentId,
            @RequestParam Integer bookId,
            @RequestParam(name = "star", required = false) Integer star,
            @RequestParam(name = "content", required = false) String content,
            @RequestParam(name = "username", required = false) String username,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            // ========== CASE 1: UPDATE EXISTING COMMENT ==========
            if (commentId != null && commentId > 0) {
                // Validate star (bắt buộc)
                if (star == null || star < 1 || star > 5) {
                    redirectAttributes.addFlashAttribute("error", "Rating must be between 1 and 5 stars");
                    return "redirect:/detail-product?id=" + bookId;
                }

                // Content là tuỳ chọn, chỉ validate độ dài nếu có nhập
                if (content != null && content.trim().length() > 5000) {
                    redirectAttributes.addFlashAttribute("error", "Review content cannot exceed 5000 characters");
                    return "redirect:/detail-product?id=" + bookId;
                }

                try {
                    Comment updatedComment = commentService.updateComment(commentId, content, star, request);
                    redirectAttributes.addFlashAttribute("success", "Review updated successfully!");
                    return "redirect:/detail-product?id=" + bookId;

                } catch (IllegalStateException e) {
                    redirectAttributes.addFlashAttribute("error", e.getMessage());
                    return "redirect:/detail-product?id=" + bookId;
                }
            }

            // ========== CASE 2: CREATE NEW COMMENT ==========
            else {
                // Validate star (bắt buộc)
                if (star == null || star < 1 || star > 5) {
                    redirectAttributes.addFlashAttribute("error", "Rating must be between 1 and 5 stars");
                    return "redirect:/detail-product?id=" + bookId;
                }

                // Content là tuỳ chọn, chỉ validate độ dài nếu có nhập
                if (content != null && content.trim().length() > 5000) {
                    redirectAttributes.addFlashAttribute("error", "Review content cannot exceed 5000 characters");
                    return "redirect:/detail-product?id=" + bookId;
                }

                try {
                    Comment comment = new Comment();
                    comment.setStar(star);
                    // Cho phép content rỗng (chỉ đánh sao)
                    comment.setContent(content != null ? content.trim() : "");

                    comment = commentService.save(comment, request);

                    redirectAttributes.addFlashAttribute("success", "Thank you for your review!");
                    return "redirect:/detail-product?id=" + comment.getBook().getId();

                } catch (IllegalStateException e) {
                    redirectAttributes.addFlashAttribute("error", e.getMessage());
                    return "redirect:/detail-product?id=" + bookId;
                } catch (Exception e) {
                    e.printStackTrace();
                    redirectAttributes.addFlashAttribute("error", "Error submitting review. Please try again.");
                    return "redirect:/detail-product?id=" + bookId;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error processing review: " + e.getMessage());
            return "redirect:/detail-product?id=" + bookId;
        }
    }

    @GetMapping("/view-cart")
    public String viewCart(Model model, HttpServletRequest request) {
        try {
            User user = userService.getCurrentUserFromContext();
            if (user == null) {
                return "redirect:/login";
            }

            List<CartItem> cartItems = cartItemService.getCartItems(user);
            model.addAttribute("cartItems", cartItems);

            // ========== DETECT COMPLETE SERIES WITH SETS ==========
            Map<Integer, Integer> seriesSetsMap = orderService.detectCompleteSeriesWithSets(cartItems);
            Map<Integer, Series> seriesBundlesInCart = new HashMap<>();
            Map<Integer, Series> detectedSeriesMap = new HashMap<>();

            for (Integer seriesId : seriesSetsMap.keySet()) {
                try {
                    Series series = seriesService.findById(seriesId);
                    if (series != null) {
                        seriesBundlesInCart.put(seriesId, series);
                        detectedSeriesMap.put(seriesId, series);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            model.addAttribute("seriesBundlesInCart", seriesBundlesInCart);
            model.addAttribute("detectedSeriesMap", detectedSeriesMap);
            model.addAttribute("seriesSetsMap", seriesSetsMap);

            addCartInfoToModel(model);

            return "user/cart";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/";
        }
    }

    @GetMapping("/clear-cart")
    public String clearCart(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getCurrentUser(request);
            cartItemService.clearCart(user);
            redirectAttributes.addFlashAttribute("success", "Cart cleared successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error clearing cart!");
        }
        return "redirect:/view-cart";
    }

    @GetMapping("/remove-book")
    public String removeBook(HttpServletRequest request,
                             @RequestParam Integer id) {
        User user = userService.getCurrentUser(request);
        cartItemService.removeCartItemByUserAndBook(user, id);

        return "redirect:/view-cart";
    }

    @PostMapping("/add-to-cart")
    public String addToCart(HttpServletRequest request, Model model,
                            @RequestParam Integer id,
                            @RequestParam Integer quantity,
                            RedirectAttributes redirectAttributes) {
        Book book = bookService.findById(id);

        if (book == null) {
            redirectAttributes.addFlashAttribute("error", "Book not found!");
            return "redirect:/products";
        }

        if (book.getNumber_in_stock() == 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Book '" + book.getTitle() + "' is out of stock!");
            return "redirect:/detail-product?id=" + id;
        }

        if (quantity <= 0) {
            redirectAttributes.addFlashAttribute("error", "Invalid quantity!");
            return "redirect:/detail-product?id=" + id;
        }

        if (quantity > book.getNumber_in_stock()) {
            redirectAttributes.addFlashAttribute("error",
                    "Only " + book.getNumber_in_stock() + " copies available!");
            return "redirect:/detail-product?id=" + id;
        }

        User user = userService.getCurrentUser(request);
        List<CartItem> cartItems = cartItemService.getCartItems(user);

        CartItem existingItem = null;
        for (CartItem item : cartItems) {
            if (item.getBook().getId().equals(id)) {
                existingItem = item;
                break;
            }
        }

        int totalQuantity = quantity;
        if (existingItem != null) {
            totalQuantity += existingItem.getQuantity();
        }

        if (totalQuantity > book.getNumber_in_stock()) {
            int availableToAdd = book.getNumber_in_stock() -
                    (existingItem != null ? existingItem.getQuantity() : 0);
            redirectAttributes.addFlashAttribute("error",
                    "Cannot add " + quantity + " more. Only " + availableToAdd + " copies available!");
            return "redirect:/detail-product?id=" + id;
        }

        cartItemService.addBookToCart(user, id, quantity);
        redirectAttributes.addFlashAttribute("success",
                "Added " + quantity + " copy(ies) to cart!");

        return "redirect:/detail-product?id=" + id;
    }

    @PostMapping("/add-series-to-cart")
    public String addSeriesToCart(@RequestParam Integer seriesId,
                                  RedirectAttributes redirectAttributes) {
        try {
            seriesService.validateSeriesBundlePurchase(seriesId);

            User user = userService.getCurrentUserFromContext();
            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "Please login to add items to cart");
                return "redirect:/login";
            }

            List<Book> availableBooks = seriesService.getAvailableBooksInSeries(seriesId);

            if (availableBooks.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "No books available in this series");
                return "redirect:/products";
            }

            int addedCount = 0;
            int updatedCount = 0;

            for (Book book : availableBooks) {
                CartItem existingItem = cartItemService.findByUserAndBook(user, book);

                if (existingItem != null) {
                    existingItem.setQuantity(existingItem.getQuantity() + 1);
                    existingItem.setUpdatedAt(new Date());
                    cartItemService.save(existingItem);
                    updatedCount++;
                } else {
                    CartItem newItem = new CartItem();
                    newItem.setUser(user);
                    newItem.setBook(book);
                    newItem.setQuantity(1);
                    newItem.setCreatedAt(new Date());
                    newItem.setUpdatedAt(new Date());
                    cartItemService.save(newItem);
                    addedCount++;
                }
            }

            Series series = seriesService.findById(seriesId);

            StringBuilder message = new StringBuilder();
            if (addedCount > 0 && updatedCount > 0) {
                message.append("Added ").append(addedCount).append(" new volumes and updated ")
                        .append(updatedCount).append(" existing volumes of \"")
                        .append(series.getName()).append("\"! ");
            } else if (addedCount > 0) {
                message.append("Added ").append(addedCount).append(" volumes of \"")
                        .append(series.getName()).append("\" to cart! ");
            } else if (updatedCount > 0) {
                message.append("Updated quantity for all volumes of \"")
                        .append(series.getName()).append("\"! ");
            }
            message.append("5% series discount will be applied at checkout.");

            redirectAttributes.addFlashAttribute("success", message.toString());

            return "redirect:/view-cart";

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/products";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error adding series to cart: " + e.getMessage());
            return "redirect:/products";
        }
    }

    @PostMapping("/purchase")
    public String purchase(HttpServletRequest request, Model model) {
        User user = userService.getCurrentUser(request);
        List<CartItem> cartItems = cartItemService.getCartItems(user);

        if (cartItemService.isCartEmpty(user)) {
            return "redirect:/view-cart";
        }

        String[] selectedItems = request.getParameterValues("selectedItems");

        if (selectedItems == null || selectedItems.length == 0) {
            model.addAttribute("error", "You haven't selected any products!");
            model.addAttribute("cartItems", cartItems);
            model.addAttribute("cartTotal", 0.0);
            model.addAttribute("cartTotalBooks", 0);
            model.addAttribute("user", user);

            List<Voucher> allVouchers = voucherService.findAll();
            model.addAttribute("allPromotions", allVouchers);
            model.addAttribute("appliedPromotion", null);
            model.addAttribute("discount", 0.0);
            model.addAttribute("shippingFee", DEFAULT_SHIPPING_FEE);

            return "user/purchase_user";
        }

        request.getSession().setAttribute("selectedItems", selectedItems);

        for (int i = 0; i < cartItems.size(); i++) {
            String sequence = "quantity" + i;
            String quantityParam = request.getParameter(sequence);
            if (quantityParam != null) {
                Integer quantity = Integer.valueOf(quantityParam);
                CartItem item = cartItems.get(i);
                cartItemService.updateCartItem(user, item.getBook().getId(), quantity);
            }
        }

        cartItems = cartItemService.getCartItems(user);

        Double cartTotal = 0.0;
        Integer totalBooks = 0;
        List<Integer> selectedBookIds = new ArrayList<>();
        List<CartItem> selectedCartItems = new ArrayList<>();

        for (String selectedId : selectedItems) {
            Integer bookId = Integer.valueOf(selectedId);
            selectedBookIds.add(bookId);

            for (CartItem item : cartItems) {
                if (item.getBook().getId().equals(bookId)) {
                    cartTotal += item.getBook().getPrice() * item.getQuantity();
                    totalBooks += item.getQuantity();
                    selectedCartItems.add(item);
                    break;
                }
            }
        }

        Map<Integer, Integer> seriesSetsMap = orderService.detectCompleteSeriesWithSets(selectedCartItems);
        boolean hasSeriesBundle = !seriesSetsMap.isEmpty();

        if (hasSeriesBundle) {
            Double seriesBundleDiscount = orderService.calculateSeriesBundleDiscount(selectedCartItems, seriesSetsMap);

            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");

            model.addAttribute("hasSeriesBundle", true);
            model.addAttribute("seriesSetsMap", seriesSetsMap);
            model.addAttribute("seriesBundleDiscount", seriesBundleDiscount);

            for (Map.Entry<Integer, Integer> entry : seriesSetsMap.entrySet()) {
                Integer seriesId = entry.getKey();
                Integer numberOfSets = entry.getValue();

                Series series = seriesService.findById(seriesId);
                if (series != null) {
                    model.addAttribute("seriesId", seriesId);
                    model.addAttribute("seriesName", series.getName());

                    double discountPercentage = numberOfSets >= 2 ? 0.10 : 0.05;
                    model.addAttribute("discountPercentage", discountPercentage);
                    break;
                }
            }
        } else {
            model.addAttribute("hasSeriesBundle", false);
            model.addAttribute("seriesBundleDiscount", 0.0);

            List<Voucher> allVouchers = voucherService.findAll();
            model.addAttribute("allPromotions", allVouchers);

            Voucher appliedVoucher = (Voucher) request.getSession().getAttribute("appliedPromotion");
            Double discount = (Double) request.getSession().getAttribute("discount");
            if (discount == null) discount = 0.0;

            model.addAttribute("appliedPromotion", appliedVoucher);
            model.addAttribute("discount", discount);
        }

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartTotal", cartTotal);
        model.addAttribute("cartTotalBooks", totalBooks);
        model.addAttribute("user", user);
        model.addAttribute("selectedBookIds", selectedBookIds);
        model.addAttribute("shippingFee", DEFAULT_SHIPPING_FEE);

        return "user/purchase_user";
    }

    @GetMapping("/purchase")
    public String getPurchase(HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser(request);
        List<CartItem> cartItems = cartItemService.getCartItems(user);

        if (cartItemService.isCartEmpty(user)) {
            return "redirect:/view-cart";
        }

        String[] selectedItems = (String[]) request.getSession().getAttribute("selectedItems");

        if (selectedItems == null || selectedItems.length == 0) {
            redirectAttributes.addFlashAttribute("error", "You haven't selected any products!");
            return "redirect:/view-cart";
        }

        Double cartTotal = 0.0;
        Integer totalBooks = 0;
        List<Integer> selectedBookIds = new ArrayList<>();
        List<CartItem> selectedCartItems = new ArrayList<>();

        for (String selectedId : selectedItems) {
            Integer bookId = Integer.valueOf(selectedId);
            selectedBookIds.add(bookId);

            for (CartItem item : cartItems) {
                if (item.getBook().getId().equals(bookId)) {
                    cartTotal += item.getBook().getPrice() * item.getQuantity();
                    totalBooks += item.getQuantity();
                    selectedCartItems.add(item);
                    break;
                }
            }
        }

        Map<Integer, Integer> seriesSetsMap = orderService.detectCompleteSeriesWithSets(selectedCartItems);
        boolean hasSeriesBundle = !seriesSetsMap.isEmpty();

        if (hasSeriesBundle) {
            Double seriesBundleDiscount = orderService.calculateSeriesBundleDiscount(selectedCartItems, seriesSetsMap);

            model.addAttribute("hasSeriesBundle", true);
            model.addAttribute("seriesSetsMap", seriesSetsMap);
            model.addAttribute("seriesBundleDiscount", seriesBundleDiscount);

            for (Map.Entry<Integer, Integer> entry : seriesSetsMap.entrySet()) {
                Integer seriesId = entry.getKey();
                Integer numberOfSets = entry.getValue();

                Series series = seriesService.findById(seriesId);
                if (series != null) {
                    model.addAttribute("seriesId", seriesId);
                    model.addAttribute("seriesName", series.getName());

                    double discountPercentage = numberOfSets >= 2 ? 0.10 : 0.05;
                    model.addAttribute("discountPercentage", discountPercentage);
                    break;
                }
            }
        } else {
            model.addAttribute("hasSeriesBundle", false);
            model.addAttribute("seriesBundleDiscount", 0.0);

            List<Voucher> allVouchers = voucherService.findAll();
            Voucher appliedVoucher = (Voucher) request.getSession().getAttribute("appliedPromotion");
            Double discount = (Double) request.getSession().getAttribute("discount");
            if (discount == null) discount = 0.0;

            model.addAttribute("allPromotions", allVouchers);
            model.addAttribute("appliedPromotion", appliedVoucher);
            model.addAttribute("discount", discount);
        }

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartTotal", cartTotal);
        model.addAttribute("cartTotalBooks", totalBooks);
        model.addAttribute("user", user);
        model.addAttribute("selectedBookIds", selectedBookIds);
        model.addAttribute("shippingFee", DEFAULT_SHIPPING_FEE);

        return "user/purchase_user";
    }

    @PostMapping("/apply-promotion")
    public String applyPromotion(HttpServletRequest request,
                                 @RequestParam(required = false) String promotionCode,
                                 RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser(request);
        List<CartItem> cartItems = cartItemService.getCartItems(user);

        if (cartItemService.isCartEmpty(user)) {
            redirectAttributes.addFlashAttribute("error", "Your cart is empty!");
            return "redirect:/view-cart";
        }

        String[] selectedItems = request.getParameterValues("selectedItems");

        if (selectedItems == null || selectedItems.length == 0) {
            redirectAttributes.addFlashAttribute("error", "You haven't selected any products!");
            return "redirect:/view-cart";
        }

        List<CartItem> selectedCartItems = new ArrayList<>();
        for (String selectedId : selectedItems) {
            Integer bookId = Integer.valueOf(selectedId);
            for (CartItem item : cartItems) {
                if (item.getBook().getId().equals(bookId)) {
                    selectedCartItems.add(item);
                    break;
                }
            }
        }

        Map<Integer, Integer> seriesSetsMap = orderService.detectCompleteSeriesWithSets(selectedCartItems);
        if (!seriesSetsMap.isEmpty()) {
            int maxSets = Collections.max(seriesSetsMap.values());
            String discountMsg = maxSets >= 2 ? "10%" : "5%";

            redirectAttributes.addFlashAttribute("error",
                    "Promotion codes cannot be used with series bundles. You are already receiving a " +
                            discountMsg + " series discount!");
            request.getSession().setAttribute("selectedItems", selectedItems);
            return "redirect:/purchase";
        }

        if (promotionCode == null || promotionCode.trim().isEmpty()) {
            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");
            request.getSession().setAttribute("selectedItems", selectedItems);
            redirectAttributes.addFlashAttribute("success", "Promotion removed!");
            return "redirect:/purchase";
        }

        Voucher voucher = voucherService.findByCode(promotionCode);

        if (voucher == null) {
            redirectAttributes.addFlashAttribute("error", "Promotion code not found!");
            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");
            request.getSession().setAttribute("selectedItems", selectedItems);
            return "redirect:/purchase";
        }

        if (!"ACTIVE".equals(voucher.getStatus())) {
            redirectAttributes.addFlashAttribute("error",
                    "This promotion is " + voucher.getStatus().toLowerCase() + " and cannot be used!");
            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");
            request.getSession().setAttribute("selectedItems", selectedItems);
            return "redirect:/purchase";
        }

        if (voucher.getUsedCount() >= voucher.getMaxUsage()) {
            redirectAttributes.addFlashAttribute("error",
                    "This promotion has reached its usage limit!");
            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");
            request.getSession().setAttribute("selectedItems", selectedItems);
            return "redirect:/purchase";
        }

        Double orderTotal = 0.0;
        for (String selectedId : selectedItems) {
            Integer bookId = Integer.valueOf(selectedId);
            for (CartItem item : cartItems) {
                if (item.getBook().getId().equals(bookId)) {
                    orderTotal += item.getBook().getPrice() * item.getQuantity();
                    break;
                }
            }
        }

        if (orderTotal < voucher.getMinOrder()) {
            redirectAttributes.addFlashAttribute("error",
                    "Your order total must be at least " + voucher.getMinOrder() + " VND to use this promotion!");
            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");
            request.getSession().setAttribute("selectedItems", selectedItems);
            return "redirect:/purchase";
        }

        Double totalDiscount = voucherService.calculateOrderDiscount(voucher, orderTotal);

        if (totalDiscount <= 0) {
            redirectAttributes.addFlashAttribute("error",
                    "This promotion cannot be applied to your order!");
            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");
            request.getSession().setAttribute("selectedItems", selectedItems);
            return "redirect:/purchase";
        }

        request.getSession().setAttribute("appliedPromotion", voucher);
        request.getSession().setAttribute("discount", totalDiscount);
        request.getSession().setAttribute("selectedItems", selectedItems);

        redirectAttributes.addFlashAttribute("success",
                "Promotion applied successfully! You saved " + totalDiscount + " VND");

        return "redirect:/purchase";
    }

    @PostMapping("/confirm")
    public String confirm(
            HttpServletRequest request,
            Model model,
            @RequestParam(required = false) String promotionCode,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String shippingMethod,
            RedirectAttributes redirectAttributes) {

        try {
            User user = userService.getCurrentUser(request);
            List<CartItem> cartItems = cartItemService.getCartItems(user);

            if (cartItemService.isCartEmpty(user)) {
                redirectAttributes.addFlashAttribute("error", "Your cart is empty!");
                return "redirect:/view-cart";
            }

            String[] selectedItems = request.getParameterValues("selectedItems");

            if (selectedItems == null || selectedItems.length == 0) {
                selectedItems = (String[]) request.getSession().getAttribute("selectedItems");
            }

            if (selectedItems == null || selectedItems.length == 0) {
                redirectAttributes.addFlashAttribute("error", "You haven't selected any products!");
                return "redirect:/view-cart";
            }

            if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
                request.getSession().setAttribute("selectedItems", selectedItems);
                model.addAttribute("error", "Please select a payment method!");
                model.addAttribute("cartItems", cartItems);
                return "user/purchase_user";
            }

            if (shippingMethod == null || shippingMethod.trim().isEmpty()) {
                request.getSession().setAttribute("selectedItems", selectedItems);
                model.addAttribute("error", "Please select a shipping method!");
                model.addAttribute("cartItems", cartItems);
                return "user/purchase_user";
            }

            String normalizedMethod = shippingMethod.toUpperCase().trim();
            if (!normalizedMethod.equals("STANDARD") &&
                    !normalizedMethod.equals("EXPRESS") &&
                    !normalizedMethod.equals("SAME-DAY")) {
                request.getSession().setAttribute("selectedItems", selectedItems);
                model.addAttribute("error", "Invalid shipping method!");
                model.addAttribute("cartItems", cartItems);
                return "user/purchase_user";
            }

            request.getSession().setAttribute("selectedItems", selectedItems);

            String fullname = request.getParameter("fullname");
            String phone = request.getParameter("phone");
            String address = request.getParameter("address");

            try {
                orderService.validateCustomerInfo(fullname, phone, address);
            } catch (IllegalArgumentException e) {
                request.getSession().setAttribute("selectedItems", selectedItems);
                model.addAttribute("fullnameValue", fullname);
                model.addAttribute("phoneValue", phone);
                model.addAttribute("addressValue", address);

                String errorMsg = e.getMessage();
                if (errorMsg.contains("Full name") || errorMsg.contains("name")) {
                    model.addAttribute("fullnameError", errorMsg);
                } else if (errorMsg.contains("Phone") || errorMsg.contains("phone")) {
                    model.addAttribute("phoneError", errorMsg);
                } else if (errorMsg.contains("Address") || errorMsg.contains("address")) {
                    model.addAttribute("addressError", errorMsg);
                }

                model.addAttribute("cartItems", cartItems);
                return "user/purchase_user";
            }

            List<CartItem> selectedCartItems = new ArrayList<>();
            List<Integer> cartItemIds = new ArrayList<>();
            for (String selectedId : selectedItems) {
                Integer bookId = Integer.valueOf(selectedId);
                for (CartItem item : cartItems) {
                    if (item.getBook().getId().equals(bookId)) {
                        Book book = bookService.findById(item.getBook().getId());

                        if (book == null) {
                            request.getSession().setAttribute("selectedItems", selectedItems);
                            redirectAttributes.addFlashAttribute("error", "Book not found!");
                            return "redirect:/purchase";
                        }

                        if (book.getNumber_in_stock() == 0) {
                            request.getSession().setAttribute("selectedItems", selectedItems);
                            redirectAttributes.addFlashAttribute("error",
                                    "Book '" + book.getTitle() + "' is out of stock!");
                            return "redirect:/purchase";
                        }

                        if (book.getNumber_in_stock() < item.getQuantity()) {
                            request.getSession().setAttribute("selectedItems", selectedItems);
                            redirectAttributes.addFlashAttribute("error",
                                    "Book '" + book.getTitle() + "' only has " +
                                            book.getNumber_in_stock() + " copies left!");
                            return "redirect:/purchase";
                        }

                        selectedCartItems.add(item);
                        cartItemIds.add(item.getId());
                        break;
                    }
                }
            }

            try {
                Order order = orderService.createOrderFromSelectedItems(
                        selectedCartItems,
                        fullname,
                        phone,
                        address,
                        promotionCode,
                        paymentMethod,
                        normalizedMethod
                );

                order = orderService.save(order);

                Integer orderId = order.getId();

                request.getSession().removeAttribute("appliedPromotion");
                request.getSession().removeAttribute("discount");
                request.getSession().removeAttribute("selectedItems");

                for (CartItem item : selectedCartItems) {
                    try {
                        cartItemService.removeCartItemByUserAndBook(user, item.getBook().getId());
                    } catch (Exception e) {
                        System.out.println("Warning: Could not delete cart item for book " + item.getBook().getId());
                    }
                }

                redirectAttributes.addFlashAttribute("showSuccessPopup", true);
                redirectAttributes.addFlashAttribute("orderId", orderId);

                return "redirect:/order-detail?id=" + orderId;

            } catch (IllegalStateException e) {
                request.getSession().setAttribute("selectedItems", selectedItems);
                redirectAttributes.addFlashAttribute("error", e.getMessage());
                return "redirect:/purchase";
            } catch (Exception e) {
                e.printStackTrace();
                request.getSession().setAttribute("selectedItems", selectedItems);
                redirectAttributes.addFlashAttribute("error",
                        "Error processing order: " + e.getMessage());
                return "redirect:/purchase";
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "An error occurred!");
            return "redirect:/view-cart";
        }
    }


    @GetMapping("/orders")
    public String Orders(Model model,
                         HttpServletRequest request,
                         @RequestParam(required = false) String page) {

        User user = userService.getCurrentUser(request);

        int currentPage = 1;
        boolean invalidPage = false;

        if (page != null && !page.trim().isEmpty()) {
            try {
                currentPage = Integer.parseInt(page.trim());
                if (currentPage < 1) currentPage = 1;
            } catch (NumberFormatException e) {
                invalidPage = true;
                currentPage = 1;
            }
        }

        Page<Order> orderPage = orderService.findByUser(user, currentPage - 1, 10);

        if (invalidPage || currentPage > orderPage.getTotalPages()) {
            currentPage = 1;
            orderPage = orderService.findByUser(user, 0, 10);
        }

        Map<Integer, String> orderPromotions = new HashMap<>();
        Map<Integer, Double> orderDiscounts = new HashMap<>();

        for (Order order : orderPage.getContent()) {
            if (order.getVoucher() != null) {
                orderPromotions.put(order.getId(), order.getVoucher().getCode());
            }

            Double discount = order.getDiscount_amount() != null ? order.getDiscount_amount() : 0.0;
            orderDiscounts.put(order.getId(), discount);
        }

        model.addAttribute("orderList", orderPage.getContent());
        model.addAttribute("page", currentPage);
        model.addAttribute("totalPage", orderPage.getTotalPages());
        model.addAttribute("user", user);
        model.addAttribute("orderPromotions", orderPromotions);
        model.addAttribute("orderDiscounts", orderDiscounts);

        List<CartItem> cartItems = cartItemService.getCartItems(user);
        Integer totalBooks = cartItemService.calculateTotalBooks(user);
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartTotalBooks", totalBooks);

        return "user/order_user";
    }

    @GetMapping("/order-detail")
    public String orderDetail(@RequestParam Integer id,
                              Model model,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        try {
            User currentUser = userService.getCurrentUser(request);

            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "Please login to view orders");
                return "redirect:/login";
            }

            Order order = orderService.findById(id);

            if (order == null) {
                redirectAttributes.addFlashAttribute("error", "Order not found!");
                return "redirect:/orders";
            }

            if (!order.getUser().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized access!");
                return "redirect:/orders";
            }

            Double orderDiscount = order.getDiscount_amount() != null ? order.getDiscount_amount() : 0.0;

            Double finalTotal = order.getFinalTotal();

            String promotionCode = null;
            if (order.getVoucher() != null) {
                promotionCode = order.getVoucher().getCode();
            }

            model.addAttribute("order", order);
            model.addAttribute("orderDiscount", orderDiscount);
            model.addAttribute("finalTotal", finalTotal != null ? finalTotal : 0.0);
            model.addAttribute("shippingFee", order.getShipping_fee() != null ? order.getShipping_fee() : 0.0);
            model.addAttribute("promotionCode", promotionCode);

            List<CartItem> cartItems = cartItemService.getCartItems(currentUser);
            Integer totalBooks = cartItemService.calculateTotalBooks(currentUser);

            model.addAttribute("cartItems", cartItems != null ? cartItems : new ArrayList<>());
            model.addAttribute("cartTotalBooks", totalBooks != null ? totalBooks : 0);
            model.addAttribute("user", currentUser);

            return "user/orderedDetail_user";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error loading order details");
            return "redirect:/orders";
        }
    }

    // ========== CANCEL ORDER ENDPOINTS ==========

    @GetMapping("/cancel-order")
    public String cancelOrder(@RequestParam Integer id,
                              RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.findById(id);

            if (order == null) {
                redirectAttributes.addFlashAttribute("error", "Order not found!");
                return "redirect:/orders";
            }

            User currentUser = userService.getCurrentUserFromContext();
            if (currentUser == null || !order.getUser().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized access!");
                return "redirect:/orders";
            }

            if ("Pending".equals(order.getStatus()) || "Assigned".equals(order.getStatus())) {
                // Direct cancel for Pending and Assigned orders
                orderService.cancelOrder(id);
                redirectAttributes.addFlashAttribute("success", "Order cancelled successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Only pending orders can be cancelled directly!");
            }

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error cancelling order!");
        }
        return "redirect:/orders";
    }

    @GetMapping("/request-cancel-order")
    public String requestCancelOrder(@RequestParam Integer id,
                                     RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.findById(id);

            if (order == null) {
                redirectAttributes.addFlashAttribute("error", "Order not found!");
                return "redirect:/orders";
            }

            User currentUser = userService.getCurrentUserFromContext();
            if (currentUser == null || !order.getUser().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized access!");
                return "redirect:/orders";
            }

            orderService.requestCancelOrder(id);
            redirectAttributes.addFlashAttribute("success",
                    "Cancel request submitted successfully! Waiting for admin review.");

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error submitting cancel request!");
        }
        return "redirect:/orders";
    }
}