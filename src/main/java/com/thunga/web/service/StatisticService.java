package com.thunga.web.service;

import com.thunga.web.entity.Order;
import com.thunga.web.entity.OrderDetail;
import com.thunga.web.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StatisticService {

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Check if order should be counted as revenue
     * Conditions: Status = "Completed" AND Payment_Status = "PAID"
     * WITH null-safety checks
     */
    private boolean isRevenueOrder(Order order) {
        if (order == null ||
                order.getStatus() == null ||
                order.getPayment_status() == null ||
                order.getCreatedAt() == null) {
            return false;
        }

        return "Completed".equalsIgnoreCase(order.getStatus().trim()) &&
                "PAID".equalsIgnoreCase(order.getPayment_status().trim());
    }

    // =====================================================
    // ORDER STATISTICS
    // =====================================================

    /**
     * Order statistics by status
     * Returns: Map<Status, Count>
     */
    public Map<String, Integer> getOrderStatisticsByStatus() {
        List<Order> allOrders = orderRepository.findAll();

        Map<String, Integer> statusMap = new LinkedHashMap<>();
        statusMap.put("Pending", 0);
        statusMap.put("Approved", 0);
        statusMap.put("In Delivery", 0);
        statusMap.put("Completed", 0);
        statusMap.put("Cancelled", 0);
        statusMap.put("Returned", 0);

        for (Order order : allOrders) {
            if (order.getStatus() != null && statusMap.containsKey(order.getStatus())) {
                statusMap.merge(order.getStatus(), 1, Integer::sum);
            }
        }

        return statusMap;
    }

    // =====================================================
    // REVENUE STATISTICS - WEEKLY
    // =====================================================

    /**
     * Weekly revenue (last 7 days)
     * Format: "Mon 5" -> revenue
     * Only counts: Status = "Completed" AND Payment_Status = "PAID"
     * Returns raw values - formatting handled in view
     */
    public Map<String, Double> getWeeklyRevenue() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        List<Order> allRevenueOrders = orderRepository.findAll().stream()
                .filter(this::isRevenueOrder)
                .collect(Collectors.toList());

        Map<String, Double> weeklyRevenue = new LinkedHashMap<>();

        // Initialize 7 days
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dayName = date.getDayOfWeek().toString().substring(0, 3).toLowerCase();
            String key = dayName + " " + date.getDayOfMonth();
            weeklyRevenue.put(key, 0.0);
        }

        // Calculate revenue for each day
        for (Order order : allRevenueOrders) {
            LocalDate orderDate = order.getCreatedAt();

            if (orderDate != null &&
                    !orderDate.isBefore(sevenDaysAgo) &&
                    !orderDate.isAfter(today)) {

                String dayName = orderDate.getDayOfWeek().toString().substring(0, 3).toLowerCase();
                String key = dayName + " " + orderDate.getDayOfMonth();

                double revenue = calculateOrderRevenue(order);

                if (weeklyRevenue.containsKey(key)) {
                    weeklyRevenue.merge(key, revenue, Double::sum);
                }
            }
        }

        return weeklyRevenue;
    }

    // =====================================================
    // REVENUE STATISTICS - MONTHLY
    // =====================================================

    /**
     * Monthly revenue (last 12 months)
     * Format: "jan 2025" -> revenue
     * Only counts: Status = "Completed" AND Payment_Status = "PAID"
     */
    public Map<String, Double> getMonthlyRevenue() {
        LocalDate today = LocalDate.now();
        LocalDate twelveMonthsAgo = today.minusMonths(11);

        List<Order> allRevenueOrders = orderRepository.findAll().stream()
                .filter(this::isRevenueOrder)
                .collect(Collectors.toList());

        Map<String, Double> monthlyRevenue = new LinkedHashMap<>();

        // Initialize 12 months
        for (int i = 11; i >= 0; i--) {
            LocalDate date = today.minusMonths(i);
            String monthName = date.getMonth().toString().substring(0, 3).toLowerCase();
            String key = monthName + " " + date.getYear();
            monthlyRevenue.put(key, 0.0);
        }

        // Calculate revenue for each month
        for (Order order : allRevenueOrders) {
            LocalDate orderDate = order.getCreatedAt();

            if (orderDate != null &&
                    !orderDate.isBefore(twelveMonthsAgo) &&
                    !orderDate.isAfter(today)) {

                String monthName = orderDate.getMonth().toString().substring(0, 3).toLowerCase();
                String key = monthName + " " + orderDate.getYear();

                double revenue = calculateOrderRevenue(order);
                if (monthlyRevenue.containsKey(key)) {
                    monthlyRevenue.merge(key, revenue, Double::sum);
                }
            }
        }

        return monthlyRevenue;
    }

    // =====================================================
    // REVENUE STATISTICS - YEARLY
    // =====================================================

    /**
     * Yearly revenue (last 5 years)
     * Format: "2023" -> revenue
     * Only counts: Status = "Completed" AND Payment_Status = "PAID"
     */
    public Map<String, Double> getYearlyRevenue() {
        int currentYear = LocalDate.now().getYear();

        List<Order> allRevenueOrders = orderRepository.findAll().stream()
                .filter(this::isRevenueOrder)
                .collect(Collectors.toList());

        Map<String, Double> yearlyRevenue = new LinkedHashMap<>();

        // Initialize 5 years
        for (int i = 4; i >= 0; i--) {
            String year = String.valueOf(currentYear - i);
            yearlyRevenue.put(year, 0.0);
        }

        // Calculate revenue for each year
        for (Order order : allRevenueOrders) {
            LocalDate orderDate = order.getCreatedAt();

            if (orderDate != null && orderDate.getYear() >= currentYear - 4) {
                String year = String.valueOf(orderDate.getYear());

                double revenue = calculateOrderRevenue(order);
                if (yearlyRevenue.containsKey(year)) {
                    yearlyRevenue.merge(year, revenue, Double::sum);
                }
            }
        }

        return yearlyRevenue;
    }

    // =====================================================
    // BOOK STATISTICS
    // =====================================================

    /**
     * Top 10 best-selling books
     * Only counts: Status = "Completed"
     */
    public Map<String, Integer> getTopSellingBooks() {
        List<Order> completedOrders = orderRepository.findAll().stream()
                .filter(o -> o != null && o.getStatus() != null &&
                        "Completed".equalsIgnoreCase(o.getStatus().trim()))
                .collect(Collectors.toList());

        Map<String, Integer> bookSales = new HashMap<>();

        for (Order order : completedOrders) {
            if (order.getOrderDetailList() != null && !order.getOrderDetailList().isEmpty()) {
                for (OrderDetail detail : order.getOrderDetailList()) {
                    if (detail.getBook() != null &&
                            detail.getNumber() != null &&
                            detail.getNumber() > 0) {

                        String bookTitle = detail.getBook().getTitle();
                        bookSales.merge(bookTitle, detail.getNumber(), Integer::sum);
                    }
                }
            }
        }

        return bookSales.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    // =====================================================
    // TOTAL STATISTICS
    // =====================================================

    /**
     * Total revenue (Completed + PAID orders)
     * Formula: Total Cost (after discount) + Shipping Fee
     */
    public Double getTotalRevenue() {
        return orderRepository.findAll().stream()
                .filter(this::isRevenueOrder)
                .mapToDouble(this::calculateOrderRevenue)
                .sum();
    }

    /**
     * Total number of orders (all statuses)
     */
    public Long getTotalOrders() {
        return (long) orderRepository.findAll().size();
    }

    /**
     * Total books sold (Completed orders only)
     */
    public Long getTotalBooksSold() {
        return orderRepository.findAll().stream()
                .filter(o -> o != null && o.getStatus() != null &&
                        "Completed".equalsIgnoreCase(o.getStatus().trim()))
                .flatMap(o -> o.getOrderDetailList() != null ? o.getOrderDetailList().stream() : Stream.empty())
                .mapToLong(detail -> detail.getNumber() != null ? detail.getNumber() : 0)
                .sum();
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Calculate order revenue
     * Formula: total_cost (after discount) + shipping_fee
     */
    private double calculateOrderRevenue(Order order) {
        double subtotal = order.getTotal_cost() != null ? order.getTotal_cost() : 0;
        double shipping = order.getShipping_fee() != null ? order.getShipping_fee() : 0;
        return subtotal + shipping;
    }

    public Map<String, Object> getRevenueByDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to))
            throw new IllegalArgumentException("Invalid date range");

        List<Order> orders = orderRepository.findAll().stream()
                .filter(this::isRevenueOrder)
                .filter(o -> {
                    LocalDate d = o.getCreatedAt();
                    return d != null && !d.isBefore(from) && !d.isAfter(to);
                })
                .collect(Collectors.toList());

        Map<String, Double> dailyRevenue = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1))
            dailyRevenue.put(d.toString(), 0.0);

        for (Order o : orders)
            dailyRevenue.merge(o.getCreatedAt().toString(), calculateOrderRevenue(o), Double::sum);

        long booksSold = orders.stream()
                .flatMap(o -> o.getOrderDetailList() != null
                        ? o.getOrderDetailList().stream() : Stream.empty())
                .mapToLong(d -> d.getNumber() != null ? d.getNumber() : 0).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dailyRevenue", dailyRevenue);
        result.put("totalRevenue", orders.stream().mapToDouble(this::calculateOrderRevenue).sum());
        result.put("orderCount", (long) orders.size());
        result.put("booksSold", booksSold);
        return result;
    }
}