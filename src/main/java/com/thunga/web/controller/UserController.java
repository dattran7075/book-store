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
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
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

    @Autowired
    private VNPayService vnPayService;

    /**
     * Helper method to add cart info to model
     */
    private void addCartInfoToModel(Model model, User user, List<CartItem> cartItems) {
        if (user != null && cartItems != null) {
            Integer totalBooks = cartItemService.calculateTotalBooks(user);
            model.addAttribute("cartTotalBooks", totalBooks);
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
                if (star == null || star < 1 || star > 5) {
                    redirectAttributes.addFlashAttribute("error", "Rating must be between 1 and 5 stars");
                    return "redirect:/detail-product?id=" + bookId;
                }

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
                if (content != null && content.trim().length() > 5000) {
                    redirectAttributes.addFlashAttribute("error", "Review content cannot exceed 5000 characters");
                    return "redirect:/detail-product?id=" + bookId;
                }

                try {
                    Comment comment = new Comment();
                    comment.setStar(star);
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

            addCartInfoToModel(model, user, cartItems);

            return "user/cart";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/";
        }
    }

    @GetMapping("/clear-cart")
    public String clearCart(RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getCurrentUserFromContext();
            if (user == null) return "redirect:/login";

            cartItemService.clearCart(user);
            redirectAttributes.addFlashAttribute("success", "Cart cleared successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error clearing cart!");
        }
        return "redirect:/view-cart";
    }

    @GetMapping("/remove-book")
    public String removeBook(@RequestParam Integer id,
                             RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUserFromContext();
        if (user == null) return "redirect:/login";

        cartItemService.removeCartItemByUserAndBook(user, id);
        return "redirect:/view-cart";
    }

    @PostMapping("/add-to-cart")
    public String addToCart(Model model,
                            @RequestParam Integer id,
                            @RequestParam Integer quantity,
                            RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getCurrentUserFromContext();
            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "Please login!");
                return "redirect:/login";
            }

            cartItemService.addBookToCart(user, id, quantity);
            redirectAttributes.addFlashAttribute("success",
                    "Added " + quantity + " copy(ies) to cart!");

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
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
    public String purchase(HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUserFromContext();
        if (user == null) return "redirect:/login";
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

            List<Voucher> allVouchers = voucherService.findAllActiveVouchers();
            model.addAttribute("allPromotions", allVouchers);
            model.addAttribute("appliedPromotion", null);
            model.addAttribute("discount", 0.0);

            return "user/purchase_user";
        }

        request.getSession().setAttribute("selectedItems", selectedItems);

        for (int i = 0; i < cartItems.size(); i++) {
            String quantityParam = request.getParameter("quantity" + i);
            if (quantityParam != null) {
                try {
                    Integer quantity = Integer.valueOf(quantityParam);
                    CartItem item = cartItems.get(i);
                    cartItemService.updateCartItem(user, item.getBook().getId(), quantity);
                } catch (NumberFormatException e) {
                    redirectAttributes.addFlashAttribute("error", "Invalid quantity");
                    return "redirect:/view-cart";
                } catch (IllegalArgumentException | IllegalStateException e) {
                    redirectAttributes.addFlashAttribute("error", e.getMessage());
                    return "redirect:/view-cart";
                }
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

            List<Voucher> allVouchers = voucherService.findAllActiveVouchers();
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

            List<Voucher> allVouchers = voucherService.findAllActiveVouchers();
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

    /**
     * Handle payment method selection
     * Stores selected payment method and items in session
     */
    @PostMapping("/payment-method")
    public String selectPaymentMethod(
            HttpServletRequest request,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String promotionCode,
            RedirectAttributes redirectAttributes) {

        User user = userService.getCurrentUser(request);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            String[] selectedItems = request.getParameterValues("selectedItems");

            if (selectedItems == null || selectedItems.length == 0) {
                redirectAttributes.addFlashAttribute("error", "Please select at least one product!");
                return "redirect:/view-cart";
            }

            if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
                request.getSession().setAttribute("selectedItems", selectedItems);
                redirectAttributes.addFlashAttribute("error", "Please select a payment method!");
                return "redirect:/purchase";
            }

            // Store selected payment method and items in session
            request.getSession().setAttribute("selectedPaymentMethod", paymentMethod);
            request.getSession().setAttribute("selectedItems", selectedItems);
            if (promotionCode != null && !promotionCode.trim().isEmpty()) {
                request.getSession().setAttribute("promotionCode", promotionCode);
            }

            redirectAttributes.addFlashAttribute("success", "Payment method selected!");
            return "redirect:/purchase";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error selecting payment method!");
            return "redirect:/view-cart";
        }
    }

    /**
     * Create VNPay payment link and redirect user to payment gateway
     * This endpoint:
     * 1. Validates customer information
     * 2. Creates an Order with status=Pending, payment_status=UNPAID
     * 3. Generates VNPay payment link with signature
     * 4. Redirects user to VNPay gateway
     */
    @PostMapping("/vnpay-payment")
    public String vnpayPayment(
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        User user = userService.getCurrentUser(request);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            String[] selectedItems = (String[]) request.getSession().getAttribute("selectedItems");
            String promotionCode = (String) request.getSession().getAttribute("promotionCode");
            if (promotionCode == null || promotionCode.trim().isEmpty()) {
                Voucher appliedVoucher = (Voucher) request.getSession().getAttribute("appliedPromotion");
                if (appliedVoucher != null) {
                    promotionCode = appliedVoucher.getCode();
                }
            }

            if (selectedItems == null || selectedItems.length == 0) {
                redirectAttributes.addFlashAttribute("error", "No items selected!");
                return "redirect:/view-cart";
            }

            // Get customer information from request
            String fullname = request.getParameter("fullname");
            String phone = request.getParameter("phone");
            String address = request.getParameter("address");
            String shippingMethod = request.getParameter("shippingMethod");

            // Validate customer information
            try {
                orderService.validateCustomerInfo(fullname, phone, address);
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", e.getMessage());
                request.getSession().setAttribute("selectedItems", selectedItems);
                return "redirect:/purchase";
            }

            // Get cart items
            List<CartItem> cartItems = cartItemService.getCartItems(user);
            List<CartItem> selectedCartItems = new ArrayList<>();
            List<Integer> selectedBookIds = new ArrayList<>();

            // Filter only selected items
            for (String selectedId : selectedItems) {
                Integer bookId = Integer.valueOf(selectedId);
                selectedBookIds.add(bookId);
                for (CartItem item : cartItems) {
                    if (item.getBook().getId().equals(bookId)) {
                        selectedCartItems.add(item);
                        break;
                    }
                }
            }

            // Detect if user is purchasing complete series
            Map<Integer, Integer> seriesSetsMap = orderService.detectCompleteSeriesWithSets(selectedCartItems);

            // Create order (not yet confirmed payment)
            Order order = orderService.createOrderFromSelectedItems(
                    selectedCartItems,
                    fullname,
                    phone,
                    address,
                    promotionCode,
                    "VNPAY",
                    shippingMethod,
                    seriesSetsMap
            );

            // Save order to database
            order = orderService.save(order);

            // Clear session
            request.getSession().removeAttribute("appliedPromotion");
            request.getSession().removeAttribute("discount");
            request.getSession().removeAttribute("promotionCode");
            request.getSession().removeAttribute("selectedPaymentMethod");
            request.getSession().removeAttribute("selectedItems");

            // Create VNPay payment link
            String baseUrl = request.getScheme() + "://" + request.getServerName() +
                    (request.getServerPort() != 80 && request.getServerPort() != 443 ?
                            ":" + request.getServerPort() : "");

            String paymentLink = vnPayService.createPaymentLink(order, baseUrl, request);

            // Redirect to VNPay gateway
            return "redirect:" + paymentLink;

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error creating payment link!");
            return "redirect:/view-cart";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/purchase";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error processing payment!");
            return "redirect:/view-cart";
        }
    }

    /**
     * Handle VNPay callback (Return URL)
     * VNPay redirects user back to this endpoint after payment processing
     * <p>
     * Flow:
     * 1. Receive all callback parameters from VNPay
     * 2. Verify signature to ensure callback is from VNPay
     * 3. Check response code (00 = success, others = fail)
     * 4. Update order status and payment status accordingly
     * 5. Display result page to user
     */
    @GetMapping("/vnpay-return")
    public String vnpayReturn(
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // Collect all parameters from VNPay callback
            Map<String, String> fields = new HashMap<>();
            Enumeration<String> params = request.getParameterNames();

            while (params.hasMoreElements()) {
                String fieldName = params.nextElement();
                String fieldValue = request.getParameter(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    fields.put(fieldName, fieldValue);
                }
            }

            // Verify callback signature
            boolean isValidSignature = vnPayService.verifyPaymentResponse(fields);

            if (!isValidSignature) {
                model.addAttribute("paymentStatus", "FAILED");
                model.addAttribute("message", "Signature verification failed! Possible tampering detected.");
                return "user/vnpay-result";
            }

            // Extract key information from callback
            String vnp_ResponseCode = request.getParameter("vnp_ResponseCode");
            String vnp_TxnRef = request.getParameter("vnp_TxnRef");
            String vnp_Amount = request.getParameter("vnp_Amount");
            String vnp_TransactionNo = request.getParameter("vnp_TransactionNo");

            // Parse Order ID from transaction reference
            Integer orderId = vnPayService.parseOrderId(vnp_TxnRef);

            if (orderId == null) {
                model.addAttribute("paymentStatus", "FAILED");
                model.addAttribute("message", "Order not found!");
                return "user/vnpay-result";
            }

            // Retrieve order from database
            Order order = orderService.findById(orderId);

            if (order == null) {
                model.addAttribute("paymentStatus", "FAILED");
                model.addAttribute("message", "Order does not exist!");
                return "user/vnpay-result";
            }

            // Check payment result
            if ("00".equals(vnp_ResponseCode)) {
                // Payment successful!
                order.setStatus("Approved");
                order.setPayment_status("PAID");
                order.setPayment_note("VNPay Transaction #" + vnp_TransactionNo);
                order.setUpdated_at(LocalDate.now());

                orderService.save(order);

                // Remove cart items for this user
                User user = order.getUser();
                if (user != null) {
                    for (OrderDetail detail : order.getOrderDetailList()) {
                        cartItemService.removeCartItemByUserAndBook(user, detail.getBook().getId());
                    }
                }

                model.addAttribute("paymentStatus", "SUCCESS");
                model.addAttribute("orderId", orderId);
                model.addAttribute("message", "Payment successful!");
                model.addAttribute("transactionNo", vnp_TransactionNo);
                model.addAttribute("amount", Long.parseLong(vnp_Amount) / 100);

            } else {
                // Payment failed
                String errorMessage = vnPayService.getPaymentStatusMessage(vnp_ResponseCode);

                order.setStatus("Cancelled");
                order.setPayment_status("UNPAID");
                order.setUpdated_at(LocalDate.now());

                // Restore stock to inventory
                if (order.getOrderDetailList() != null) {
                    for (OrderDetail detail : order.getOrderDetailList()) {
                        Book book = detail.getBook();
                        book.setNumber_in_stock(book.getNumber_in_stock() + detail.getNumber());
                        book.setNumber_sold(Math.max(0, book.getNumber_sold() - detail.getNumber()));
                        book.setUpdated_at(new Date());
                        bookService.save(book);
                    }
                }

                orderService.save(order);

                model.addAttribute("paymentStatus", "FAILED");
                model.addAttribute("orderId", orderId);
                model.addAttribute("message", errorMessage);
            }

            return "user/vnpay-result";

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            model.addAttribute("paymentStatus", "FAILED");
            model.addAttribute("message", "Error processing payment data!");
            return "user/vnpay-result";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("paymentStatus", "FAILED");
            model.addAttribute("message", "System error!");
            return "user/vnpay-result";
        }
    }

    /**
     * View payment result (optional endpoint for direct access)
     */
    @GetMapping("/payment-result")
    public String paymentResult(
            @RequestParam(required = false) Integer orderId,
            Model model,
            HttpServletRequest request) {

        try {
            User user = userService.getCurrentUser(request);
            if (user == null) {
                return "redirect:/login";
            }

            if (orderId != null) {
                Order order = orderService.findById(orderId);
                if (order != null && order.getUser().getId().equals(user.getId())) {
                    model.addAttribute("order", order);
                    model.addAttribute("paymentStatus", "PAID".equals(order.getPayment_status()) ? "SUCCESS" : "FAILED");
                }
            }

            List<CartItem> cartItems = cartItemService.getCartItems(user);
            Integer totalBooks = cartItemService.calculateTotalBooks(user);
            addCartInfoToModel(model, user, cartItems);

            return "user/order_user";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/";
        }
    }

    /**
     * Updated confirm endpoint to support both COD and VNPAY payment methods
     * <p>
     * If payment method is VNPAY:
     * - Redirect to /vnpay-payment to create payment link
     * If payment method is COD:
     * - Process order normally (existing logic)
     */
    @PostMapping("/confirm")
    public String confirm(
            HttpServletRequest request,
            Model model,
            @RequestParam(required = false) String promotionCode,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String shippingMethod,
            RedirectAttributes redirectAttributes) {

        // If VNPAY payment method selected, redirect to VNPay endpoint
        if ("VNPAY".equals(paymentMethod)) {
            return vnpayPayment(request, redirectAttributes);
        }

        // Handle COD (Cash On Delivery) payment
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
                redirectAttributes.addFlashAttribute("error", "Please select at least one product!");
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
                if (errorMsg.contains("name")) {
                    model.addAttribute("fullnameError", errorMsg);
                } else if (errorMsg.contains("phone")) {
                    model.addAttribute("phoneError", errorMsg);
                } else if (errorMsg.contains("address")) {
                    model.addAttribute("addressError", errorMsg);
                }

                model.addAttribute("cartItems", cartItems);
                return "user/purchase_user";
            }

            List<CartItem> selectedCartItems = new ArrayList<>();
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

                        selectedCartItems.add(item);
                        break;
                    }
                }
            }

            Map<Integer, Integer> seriesSetsMap = orderService.detectCompleteSeriesWithSets(selectedCartItems);

            try {
                Order order = orderService.createOrderFromSelectedItems(
                        selectedCartItems,
                        fullname,
                        phone,
                        address,
                        promotionCode,
                        paymentMethod,
                        normalizedMethod,
                        seriesSetsMap
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
                redirectAttributes.addFlashAttribute("error", "Error processing order: " + e.getMessage());
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
                    "Cancel request submitted successfully!");

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error submitting cancel request!");
        }
        return "redirect:/orders";
    }
}