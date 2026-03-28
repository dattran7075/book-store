package com.thunga.web.service;

import com.thunga.web.entity.*;
import com.thunga.web.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BookService bookService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private SeriesService seriesService;

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Order findById(Integer id) {
        return orderRepository.findById(id).get();
    }

    // ========== SHIPPING CALCULATION METHODS ==========

    public double calculateOrderShipping(double orderAmount, String shippingType, double weightKg) {
        if (orderAmount < 0) {
            throw new IllegalArgumentException("Order amount must be greater than or equal to 0");
        }
        if (weightKg <= 0) {
            throw new IllegalArgumentException("Weight must be greater than 0 kg");
        }
        if (shippingType == null || shippingType.isEmpty()) {
            throw new IllegalArgumentException("Shipping type must not be null or empty");
        }

        double shippingFee;

        switch (shippingType.toUpperCase().trim()) {
            case "STANDARD":
                shippingFee = 15000 + weightKg * 5000;
                break;
            case "EXPRESS":
                shippingFee = 25000 + weightKg * 8000;
                break;
            case "SAME-DAY":
                shippingFee = 40000 + weightKg * 12000;
                break;
            default:
                throw new IllegalArgumentException("Shipping type must be STANDARD, EXPRESS, or SAME-DAY");
        }

        if (orderAmount >= 500000) {
            shippingFee = 0;
        }

        return shippingFee;
    }

    public double getMaxWeight(String shippingMethod) {
        if (shippingMethod == null) return 0;

        switch (shippingMethod.toUpperCase().trim()) {
            case "STANDARD":
                return 50.0;
            case "EXPRESS":
                return 30.0;
            case "SAME-DAY":
                return 20.0;
            default:
                return 0;
        }
    }

    // ========== SERIES DETECTION METHODS ==========

    public Map<Integer, Integer> detectCompleteSeriesWithSets(List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return new HashMap<>();
        }

        Map<Integer, Integer> seriesSetsMap = new HashMap<>();

        Map<Integer, List<CartItem>> itemsBySeries = new HashMap<>();
        for (CartItem item : cartItems) {
            if (item.getBook().getSeries() != null) {
                Integer seriesId = item.getBook().getSeries().getId();
                itemsBySeries.computeIfAbsent(seriesId, k -> new ArrayList<>()).add(item);
            }
        }

        for (Map.Entry<Integer, List<CartItem>> entry : itemsBySeries.entrySet()) {
            Integer seriesId = entry.getKey();
            List<CartItem> seriesItems = entry.getValue();

            try {
                if (!seriesService.canPurchaseFullSeries(seriesId)) {
                    continue;
                }

                List<Book> requiredBooks = seriesService.getAvailableBooksInSeries(seriesId);
                Set<Integer> requiredBookIds = requiredBooks.stream()
                        .map(Book::getId)
                        .collect(Collectors.toSet());

                Map<Integer, Integer> bookQuantityMap = new HashMap<>();
                for (CartItem item : seriesItems) {
                    Integer bookId = item.getBook().getId();
                    bookQuantityMap.put(bookId, item.getQuantity());
                }

                int numberOfCompleteSets = Integer.MAX_VALUE;
                for (Integer requiredBookId : requiredBookIds) {
                    int quantityInCart = bookQuantityMap.getOrDefault(requiredBookId, 0);
                    numberOfCompleteSets = Math.min(numberOfCompleteSets, quantityInCart);
                }

                if (numberOfCompleteSets > 0) {
                    seriesSetsMap.put(seriesId, numberOfCompleteSets);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return seriesSetsMap;
    }

    private double calculateSeriesDiscountPercentage(int numberOfSets) {
        if (numberOfSets >= 2) {
            return 0.10;
        } else if (numberOfSets == 1) {
            return 0.05;
        }
        return 0.0;
    }

    public Double calculateSeriesBundleDiscount(
            List<CartItem> selectedCartItems,
            Map<Integer, Integer> seriesSetsMap) {
        if (seriesSetsMap == null || seriesSetsMap.isEmpty()) {
            return 0.0;
        }

        double totalDiscount = 0.0;

        Map<Integer, List<CartItem>> itemsBySeries = new HashMap<>();
        for (CartItem item : selectedCartItems) {
            if (item.getBook().getSeries() != null) {
                Integer seriesId = item.getBook().getSeries().getId();
                if (seriesSetsMap.containsKey(seriesId)) {
                    itemsBySeries.computeIfAbsent(seriesId, k -> new ArrayList<>()).add(item);
                }
            }
        }

        for (Map.Entry<Integer, Integer> entry : seriesSetsMap.entrySet()) {
            Integer seriesId = entry.getKey();
            Integer numberOfSets = entry.getValue();

            double discountPercentage = calculateSeriesDiscountPercentage(numberOfSets);

            List<CartItem> seriesItems = itemsBySeries.get(seriesId);
            if (seriesItems == null) continue;

            double seriesSubtotal = 0.0;
            for (CartItem item : seriesItems) {
                int discountedQuantity = Math.min(item.getQuantity(), numberOfSets);
                seriesSubtotal += item.getBook().getPrice() * discountedQuantity;
            }

            totalDiscount += seriesSubtotal * discountPercentage;
        }

        return Math.round(totalDiscount * 10.0) / 10.0;
    }

    // ========== CREATE ORDER METHOD ==========

    @Transactional
    public Order createOrderFromSelectedItems(
            List<CartItem> selectedItems,
            String fullname,
            String phone,
            String address,
            String voucherCode,
            String paymentMethod,
            String shippingMethod,
            Map<Integer, Integer> seriesSetsMap) {

        if (selectedItems == null || selectedItems.isEmpty()) {
            throw new IllegalStateException("No items selected!");
        }

        validateCustomerInfo(fullname, phone, address);

        if (shippingMethod == null || shippingMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("Shipping method cannot be empty");
        }

        String normalizedMethod = shippingMethod.toUpperCase().trim();
        if (!normalizedMethod.equals("STANDARD") &&
                !normalizedMethod.equals("EXPRESS") &&
                !normalizedMethod.equals("SAME-DAY")) {
            throw new IllegalArgumentException(
                    "Invalid shipping method: " + shippingMethod +
                            ". Must be STANDARD, EXPRESS, or SAME-DAY");
        }

        // ========== RÀNG BUỘC: ĐƠN > 1 TRIỆU PHẢI DÙNG VNPAY ==========
        double orderTotalForPaymentCheck = 0.0;
        for (CartItem item : selectedItems) {
            orderTotalForPaymentCheck += item.getBook().getPrice() * item.getQuantity();
        }

        double discountForCheck = 0.0;
        if (!seriesSetsMap.isEmpty()) {
            discountForCheck = calculateSeriesBundleDiscount(selectedItems, seriesSetsMap);
        }

        double netTotalForCheck = orderTotalForPaymentCheck - discountForCheck;

        if (netTotalForCheck > 1_000_000 && !"VNPAY".equalsIgnoreCase(paymentMethod)) {
            throw new IllegalStateException(
                    "Orders over 1,000,000 VND must be paid via VNPay"
            );
        }

        Order order = new Order();
        order.setUser(selectedItems.get(0).getUser());
        order.setCustomer_name(fullname);
        order.setPhone(phone);
        order.setAddress(address);
        order.setStatus("Pending");
        order.setCreatedAt(LocalDate.now());
        order.setPayment_method(paymentMethod != null ? paymentMethod : "COD");
        order.setPayment_status("UNPAID");
        order.setShipping_method(normalizedMethod);

        double totalCost = 0.0;

        boolean isSeriesBundle = !seriesSetsMap.isEmpty();

        Voucher voucher = null;
        boolean voucherApplied = false;

        if (!isSeriesBundle && voucherCode != null && !voucherCode.trim().isEmpty()) {
            voucher = voucherService.findByCode(voucherCode);

            if (voucher == null) {
                throw new IllegalStateException("Voucher code not found!");
            }

            if (!"ACTIVE".equals(voucher.getStatus())) {
                throw new IllegalStateException("Voucher is not active!");
            }

            if (voucher.getUsedCount() >= voucher.getMaxUsage()) {
                throw new IllegalStateException("Voucher has reached its usage limit!");
            }
        } else if (isSeriesBundle && voucherCode != null && !voucherCode.trim().isEmpty()) {
            int maxSets = Collections.max(seriesSetsMap.values());
            String discountMsg = maxSets >= 2 ? "10%" : "5%";
            throw new IllegalStateException(
                    "Vouchers cannot be used with series bundles. " +
                            "You are already receiving a " + discountMsg + " series discount!");
        }

        if (isSeriesBundle) {
            Map<Integer, List<CartItem>> itemsBySeries = new HashMap<>();
            List<CartItem> nonSeriesItems = new ArrayList<>();

            for (CartItem item : selectedItems) {
                if (item.getBook().getSeries() != null &&
                        seriesSetsMap.containsKey(item.getBook().getSeries().getId())) {
                    Integer seriesId = item.getBook().getSeries().getId();
                    itemsBySeries.computeIfAbsent(seriesId, k -> new ArrayList<>()).add(item);
                } else {
                    nonSeriesItems.add(item);
                }
            }

            for (Map.Entry<Integer, Integer> seriesEntry : seriesSetsMap.entrySet()) {
                Integer seriesId = seriesEntry.getKey();
                Integer numberOfSets = seriesEntry.getValue();

                Series series = seriesService.findById(seriesId);
                double discountPercentage = calculateSeriesDiscountPercentage(numberOfSets);

                List<CartItem> seriesCartItems = itemsBySeries.get(seriesId);
                if (seriesCartItems == null) continue;

                double seriesSubtotalForDiscount = 0.0;
                for (CartItem item : seriesCartItems) {
                    int discountedQuantity = Math.min(item.getQuantity(), numberOfSets);
                    seriesSubtotalForDiscount += item.getBook().getPrice() * discountedQuantity;
                }

                double totalSeriesDiscount = seriesSubtotalForDiscount * discountPercentage;
                double remainingDiscount = totalSeriesDiscount;

                for (int i = 0; i < seriesCartItems.size(); i++) {
                    CartItem cartItem = seriesCartItems.get(i);
                    Book book = bookService.findById(cartItem.getBook().getId());
                    Integer totalQuantity = cartItem.getQuantity();

                    if (book.getNumber_in_stock() < totalQuantity) {
                        throw new IllegalStateException(
                                "Book '" + book.getTitle() + "' only has " +
                                        book.getNumber_in_stock() + " copies left!");
                    }

                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setBook(book);
                    orderDetail.setNumber(totalQuantity);

                    double itemTotal = book.getPrice() * totalQuantity;
                    orderDetail.setTotal_cost(itemTotal);

                    int discountedQuantity = Math.min(totalQuantity, numberOfSets);
                    double discountableAmount = book.getPrice() * discountedQuantity;

                    double itemDiscount;
                    if (i == seriesCartItems.size() - 1) {
                        itemDiscount = remainingDiscount;
                    } else {
                        itemDiscount = (discountableAmount / seriesSubtotalForDiscount) * totalSeriesDiscount;
                        itemDiscount = Math.round(itemDiscount * 10.0) / 10.0;
                        remainingDiscount -= itemDiscount;
                    }

                    orderDetail.setSeries(series);
                    orderDetail.setIsFullSeries(true);
                    orderDetail.setCreated_at(new Date());
                    orderDetail.setUpdated_at(new Date());

                    order.addOrderDetail(orderDetail);
                    totalCost += itemTotal;
                }
            }

            for (CartItem cartItem : nonSeriesItems) {
                Book book = bookService.findById(cartItem.getBook().getId());
                Integer quantity = cartItem.getQuantity();

                if (book.getNumber_in_stock() < quantity) {
                    throw new IllegalStateException(
                            "Book '" + book.getTitle() + "' only has " +
                                    book.getNumber_in_stock() + " copies left!");
                }

                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setBook(book);
                orderDetail.setNumber(quantity);

                double itemTotal = book.getPrice() * quantity;
                orderDetail.setTotal_cost(itemTotal);
                orderDetail.setSeries(null);
                orderDetail.setIsFullSeries(false);
                orderDetail.setCreated_at(new Date());
                orderDetail.setUpdated_at(new Date());

                order.addOrderDetail(orderDetail);
                totalCost += itemTotal;
            }

            Double seriesBundleDiscount = calculateSeriesBundleDiscount(selectedItems, seriesSetsMap);
            order.setDiscount_amount(seriesBundleDiscount);

        } else if (voucher != null) {
            double orderTotalBeforeDiscount = 0.0;
            for (CartItem cartItem : selectedItems) {
                Book book = bookService.findById(cartItem.getBook().getId());
                orderTotalBeforeDiscount += book.getPrice() * cartItem.getQuantity();
            }

            if (orderTotalBeforeDiscount < voucher.getMinOrder()) {
                throw new IllegalStateException(
                        "Your order total (" + String.format("%.1f", orderTotalBeforeDiscount) +
                                " VND) must be at least " + String.format("%.1f", voucher.getMinOrder()) +
                                " VND to use this voucher!");
            }

            double orderDiscount = voucherService.calculateOrderDiscount(voucher, orderTotalBeforeDiscount);

            if (orderDiscount > 0) {
                double remainingDiscount = orderDiscount;

                for (int i = 0; i < selectedItems.size(); i++) {
                    CartItem cartItem = selectedItems.get(i);
                    Book book = bookService.findById(cartItem.getBook().getId());
                    Integer quantity = cartItem.getQuantity();

                    if (book.getNumber_in_stock() < quantity) {
                        throw new IllegalStateException(
                                "Book '" + book.getTitle() + "' only has " +
                                        book.getNumber_in_stock() + " copies left!");
                    }

                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setBook(book);
                    orderDetail.setNumber(quantity);

                    double itemTotal = book.getPrice() * quantity;
                    orderDetail.setTotal_cost(itemTotal);

                    double itemDiscount;
                    if (i == selectedItems.size() - 1) {
                        itemDiscount = remainingDiscount;
                    } else {
                        double itemDiscountRatio = itemTotal / orderTotalBeforeDiscount;
                        itemDiscount = itemDiscountRatio * orderDiscount;
                        itemDiscount = Math.round(itemDiscount * 10.0) / 10.0;
                        remainingDiscount -= itemDiscount;
                    }

                    orderDetail.setSeries(null);
                    orderDetail.setIsFullSeries(false);
                    orderDetail.setCreated_at(new Date());
                    orderDetail.setUpdated_at(new Date());

                    order.addOrderDetail(orderDetail);
                    totalCost += itemTotal;
                }

                voucherApplied = true;
                order.setDiscount_amount(orderDiscount);
            }

            if (!voucherApplied) {
                throw new IllegalStateException("Cannot apply this voucher to your order!");
            }

        } else {
            for (CartItem cartItem : selectedItems) {
                Book book = bookService.findById(cartItem.getBook().getId());
                Integer quantity = cartItem.getQuantity();

                if (book.getNumber_in_stock() < quantity) {
                    throw new IllegalStateException(
                            "Book '" + book.getTitle() + "' only has " +
                                    book.getNumber_in_stock() + " copies left!");
                }

                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setBook(book);
                orderDetail.setNumber(quantity);

                double itemTotal = book.getPrice() * quantity;
                orderDetail.setTotal_cost(itemTotal);
                orderDetail.setSeries(null);
                orderDetail.setIsFullSeries(false);
                orderDetail.setCreated_at(new Date());
                orderDetail.setUpdated_at(new Date());

                order.addOrderDetail(orderDetail);
                totalCost += itemTotal;
            }

            order.setDiscount_amount(0.0);
        }

        if (voucher != null) {
            order.setVoucher(voucher);
        }

        double totalWeight = order.calculateTotalWeight();

        if (totalWeight <= 0) {
            throw new IllegalStateException("Unable to calculate order weight. Please ensure all books have weight information.");
        }

        double maxWeight = getMaxWeight(normalizedMethod);
        if (totalWeight > maxWeight) {
            throw new IllegalStateException(
                    "Order weight (" + String.format("%.2f", totalWeight) + "kg) exceeds limit for " +
                            normalizedMethod + " (" + maxWeight + "kg max). Please choose a different shipping method.");
        }

        double shippingFee = calculateOrderShipping(totalCost, normalizedMethod, totalWeight);

        order.setShipping_fee(shippingFee);
        order.setTotal_cost(Math.round(totalCost * 10.0) / 10.0);

        return order;
    }

    @Transactional
    public Order save(Order order) {
        boolean isNewOrder = (order.getId() == null);

        if (isNewOrder) {
            if (order.getOrderDetailList() != null && !order.getOrderDetailList().isEmpty()) {
                for (OrderDetail orderDetail : order.getOrderDetailList()) {
                    Book book = bookService.findById(orderDetail.getBook().getId());
                    Integer quantity = orderDetail.getNumber();

                    if (quantity == null || quantity <= 0) {
                        throw new IllegalStateException("Invalid quantity for book: " + book.getTitle());
                    }
                }
            }

            Order savedOrder = orderRepository.save(order);

            if (savedOrder.getOrderDetailList() != null) {
                for (OrderDetail orderDetail : savedOrder.getOrderDetailList()) {
                    Book book = orderDetail.getBook();
                    Integer quantity = orderDetail.getNumber();

                    book.setNumber_in_stock(book.getNumber_in_stock() - quantity);
                    book.setNumber_sold(book.getNumber_sold() + quantity);
                    book.setUpdated_at(new Date());
                    bookService.save(book);
                }
            }

            if (savedOrder.getVoucher() != null) {
                voucherService.applyVoucher(savedOrder.getVoucher());
                voucherService.save(savedOrder.getVoucher());
            }

            return savedOrder;
        } else {
            return orderRepository.save(order);
        }
    }


    @Transactional
    public void cancelOrder(Integer orderId) {
        Order order = findById(orderId);

        // Cho phép cancel cả Pending và Assigned
        if (!"Pending".equals(order.getStatus()) && !"Assigned".equals(order.getStatus())) {
            throw new IllegalStateException("Only pending or assigned orders can be cancelled!");
        }

        order.setStatus("Cancelled");
        order.setUpdated_at(LocalDate.now());

        if (order.getOrderDetailList() != null) {
            for (OrderDetail orderDetail : order.getOrderDetailList()) {
                Book book = bookService.findById(orderDetail.getBook().getId());
                Integer quantity = orderDetail.getNumber();

                book.setNumber_in_stock(book.getNumber_in_stock() + quantity);
                book.setNumber_sold(book.getNumber_sold() - quantity);
                book.setUpdated_at(new Date());
                bookService.save(book);
            }
        }

        orderRepository.save(order);
    }
    // ========== CANCEL REQUEST METHODS ==========

    @Transactional
    public void requestCancelOrder(Integer orderId) {
        Order order = findById(orderId);

        if (!"Approved".equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Cancel requests can only be submitted for Approved orders. " +
                            "Current status: " + order.getStatus());
        }

        order.setStatus("Request");
        order.setUpdated_at(LocalDate.now());
        orderRepository.save(order);
    }

    @Transactional
    public void approveCancelRequest(Integer orderId) {
        Order order = findById(orderId);

        if (!"Request".equals(order.getStatus())) {
            throw new IllegalStateException("This order has no pending cancel request.");
        }

        // Restore stock
        if (order.getOrderDetailList() != null) {
            for (OrderDetail detail : order.getOrderDetailList()) {
                Book book = bookService.findById(detail.getBook().getId());
                book.setNumber_in_stock(book.getNumber_in_stock() + detail.getNumber());
                book.setNumber_sold(Math.max(0, book.getNumber_sold() - detail.getNumber()));
                book.setUpdated_at(new Date());
                bookService.save(book);
            }
        }

        // Reverse voucher usage nếu có
        if (order.getVoucher() != null) {
            Voucher voucher = order.getVoucher();
            voucher.setUsedCount(Math.max(0, voucher.getUsedCount() - 1));
            voucherService.save(voucher);
        }

        order.setStatus("Cancelled");
        order.setUpdated_at(LocalDate.now());
        orderRepository.save(order);
    }

    @Transactional
    public void rejectCancelRequest(Integer orderId) {
        Order order = findById(orderId);

        if (!"Request".equals(order.getStatus())) {
            throw new IllegalStateException("This order has no pending cancel request.");
        }

        order.setStatus("Approved");
        order.setUpdated_at(LocalDate.now());
        orderRepository.save(order);
    }

    // ========== PAGINATION ==========

    public Page<Order> findByLimit(Integer page, Integer limit, String sortBy) {
        Pageable paging = PageRequest.of(page, limit);
        if (sortBy != null) paging = PageRequest.of(page, limit, Sort.by(sortBy));
        return orderRepository.findAll(paging);
    }

    public Page<Order> findByAdminLimit(Integer adminId, int page, int size, String sortBy) {
        Pageable pageable;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));
        } else {
            pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return orderRepository.findByAdminId(adminId, pageable);
    }

    public Page<Order> findByUser(User user, int page, int limit) {
        return orderRepository.findByUser(
                user,
                PageRequest.of(page, limit, Sort.by("createdAt").descending())
        );
    }

    // ========== VALIDATION ==========

    public void validateCustomerInfo(String fullname, String phone, String address) {
        if (fullname == null || fullname.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty");
        }

        if (fullname.length() < 2 || fullname.length() > 50) {
            throw new IllegalArgumentException("Full name must be 2-50 characters");
        }

        if (!fullname.matches("^([A-Z][a-z]+)(\\s[A-Z][a-z]+)*$")) {
            throw new IllegalArgumentException("Full name must be capitalized (ex: Nguyen Van A)");
        }

        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone cannot be empty");
        }

        if (!phone.matches("^0[1-9]\\d{8}$")) {
            throw new IllegalArgumentException("Phone must have 10 digits, start with 0");
        }

        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address cannot be empty");
        }

        if (!address.matches("^(?!.*[.,#/^()'\\-]{2,})(?!-)[A-Za-z0-9\\s.,#/^()'\\-]{2,50}$")) {
            throw new IllegalArgumentException("Invalid address");
        }
    }

    @Transactional
    public void validateStatusTransition(Integer orderId, String newStatus) {
        Order order = findById(orderId);
        String currentStatus = order.getStatus();

        if (currentStatus.equals(newStatus)) {
            throw new IllegalStateException(
                    "Order is already in '" + newStatus + "' status. No changes needed.");
        }

        boolean isValidTransition = false;

        switch (currentStatus) {
            case "Pending":
                isValidTransition = "Approved".equals(newStatus) || "Cancelled".equals(newStatus);
                break;
            case "Assigned":
                isValidTransition = "Approved".equals(newStatus) || "Cancelled".equals(newStatus);
                break;
            case "Approved":
                isValidTransition = "In Delivery".equals(newStatus) || "Cancelled".equals(newStatus)
                        || "Request".equals(newStatus);
                break;
            case "Request":
                isValidTransition = "Approved".equals(newStatus) || "Cancelled".equals(newStatus);
                break;
            case "In Delivery":
                isValidTransition = "Completed".equals(newStatus) || "Returned".equals(newStatus);
                break;
            case "Completed":
            case "Cancelled":
            case "Returned":
                isValidTransition = false;
                break;
        }

        if (!isValidTransition) {
            throw new IllegalArgumentException(
                    "Cannot change status from '" + currentStatus + "' to '" + newStatus + "'");
        }

        if ("Cancelled".equals(newStatus) || "Returned".equals(newStatus)) {
            if (order.getOrderDetailList() != null && !order.getOrderDetailList().isEmpty()) {
                for (OrderDetail orderDetail : order.getOrderDetailList()) {
                    Book book = orderDetail.getBook();
                    Integer quantity = orderDetail.getNumber();

                    book.setNumber_in_stock(book.getNumber_in_stock() + quantity);
                    book.setNumber_sold(Math.max(0, book.getNumber_sold() - quantity));
                    book.setUpdated_at(new Date());

                    bookService.save(book);
                }
            }
        }

        order.setStatus(newStatus);

        if ("Returned".equals(newStatus)) {
            order.setPayment_status("UNPAID");
        }

        order.setUpdated_at(LocalDate.now());
        orderRepository.save(order);
    }

    @Transactional
    public void confirmPayment(Integer orderId, String paymentNote) {
        Order order = findById(orderId);

        if (!"Completed".equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Payment can only be confirmed for Completed orders. " +
                            "Current status: " + order.getStatus());
        }

        if ("PAID".equals(order.getPayment_status())) {
            throw new IllegalStateException(
                    "This order payment has already been confirmed! " +
                            "Payment status is already PAID.");
        }

        order.setPayment_status("PAID");
        order.setPayment_note(paymentNote != null ? paymentNote.trim() :
                "Shipper confirmed customer paid cash upon delivery");
        order.setUpdated_at(LocalDate.now());

        orderRepository.save(order);
    }

    public Page<Order> findByLimitWithFilters(Integer page, Integer limit, String sortBy,
                                              String statusFilter, String paymentFilter) {
        Pageable paging;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            paging = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, sortBy));
        } else {
            paging = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        // Apply filters
        if ((statusFilter != null && !statusFilter.trim().isEmpty()) &&
                (paymentFilter != null && !paymentFilter.trim().isEmpty())) {
            return orderRepository.findByStatusAndPaymentStatus(statusFilter, paymentFilter, paging);
        } else if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            return orderRepository.findByStatus(statusFilter, paging);
        } else if (paymentFilter != null && !paymentFilter.trim().isEmpty()) {
            return orderRepository.findByPaymentStatus(paymentFilter, paging);
        } else {
            return orderRepository.findAll(paging);
        }
    }

    public Long countByStatus(String status) {
        return orderRepository.countByStatus(status);
    }

    public Map<String, Long> getOrderCountsByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Pending", orderRepository.countByStatus("Pending"));
        counts.put("Approved", orderRepository.countByStatus("Approved"));
        counts.put("In Delivery", orderRepository.countByStatus("In Delivery"));
        counts.put("Completed", orderRepository.countByStatus("Completed"));
        counts.put("Cancelled", orderRepository.countByStatus("Cancelled"));
        counts.put("Returned", orderRepository.countByStatus("Returned"));
        return counts;
    }
}