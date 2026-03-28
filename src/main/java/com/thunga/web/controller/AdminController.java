package com.thunga.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thunga.web.entity.*;
import com.thunga.web.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

@Controller
public class AdminController {

    @Autowired
    AuthorService authorService;
    @Autowired
    BookService bookService;
    @Autowired
    CategoryService categoryService;
    @Autowired
    UserService userService;
    @Autowired
    OrderService orderService;
    @Autowired
    AdminService adminService;
    @Autowired
    private VoucherService voucherService;
    @Autowired
    private StatisticService statisticService;
    @Autowired
    private LanguageService languageService;
    @Autowired
    private PublisherService publisherService;
    @Autowired
    private SeriesService seriesService;
    @Autowired
    private TranslatorService translatorService;
    @Autowired
    private BookTranslatorService bookTranslatorService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CommentService commentService;

    private static final String SPAM_CONFIG_PATH = "src/main/resources/static/json/spam-config.json";

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

    // =====================================================
    // USER MANAGEMENT
    // =====================================================

    @GetMapping("/admin/manage-user")
    public String manageUser(Model model,
                             @RequestParam(required = false) String page,
                             @RequestParam(required = false) String sortBy) {
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

        Page<User> userPage = userService.findByLimit(currentPage - 1, 10, sortBy);
        if (invalidPage || currentPage > userPage.getTotalPages()) {
            currentPage = 1;
            userPage = userService.findByLimit(0, 10, sortBy);
        }

        // Count active orders for each user
        Map<Integer, Integer> activeOrdersMap = new HashMap<>();
        for (User user : userPage.getContent()) {
            activeOrdersMap.put(user.getId(), userService.countActiveOrders(user));
        }

        model.addAttribute("userList", userPage.getContent());
        model.addAttribute("totalPage", userPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("activeOrdersMap", activeOrdersMap);
        return "admin/user_ad";
    }

    @GetMapping("/admin/view-user")
    public String viewUser(Model model, @RequestParam(required = false) Integer id) {
        User user = userService.findById(id);
        int activeOrders = userService.countActiveOrders(user);
        model.addAttribute("user", user);
        model.addAttribute("activeOrders", activeOrders);
        return "admin/user_detail_ad";
    }

    @GetMapping("/admin/edit-user")
    public String editUser(Model model, @RequestParam(required = false) Integer id) {
        User user = userService.findById(id);
        int activeOrders = userService.countActiveOrders(user);
        model.addAttribute("user", user);
        model.addAttribute("activeOrders", activeOrders);
        model.addAttribute("editMode", true);   // flag phân biệt
        return "admin/user_detail_ad";
    }

    @PostMapping("/admin/save-user-ad")
    public String saveUser(Model model, @ModelAttribute User user,
                           @RequestParam(required = false) String accountStatus,
                           HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {
        try {
            User existingUser = userService.findById(user.getId());
            Account account = existingUser.getAccount();

            // Check if trying to deactivate
            if (accountStatus != null && "INACTIVE".equals(accountStatus)) {
                int activeOrders = userService.countActiveOrders(existingUser);
                if (activeOrders > 0) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Cannot deactivate user with " + activeOrders + " active order(s). " +
                                    "Active orders must be completed, cancelled, or returned first.");
                    return "redirect:/admin/edit-user?id=" + user.getId();
                }
                account.setStatus("INACTIVE");
            } else if (accountStatus != null && "ACTIVE".equals(accountStatus)) {
                account.setStatus("ACTIVE");
            }

            // Update user info
            existingUser.setName(user.getName());
            existingUser.setPhone(user.getPhone());
            existingUser.setAddress(user.getAddress());
            account.setEmail(request.getParameter("email"));
            account.setUpdated_at(new Date());

            userService.save(existingUser);
            accountService.save(account);

            redirectAttributes.addFlashAttribute("successMessage", "User updated successfully");
            return "redirect:/admin/manage-user";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error updating user: " + e.getMessage());
            return "redirect:/admin/edit-user?id=" + user.getId();
        }
    }

    // =====================================================
    // ADMIN PRODUCT MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-product")
    public String adminManageProduct(Model model,
                                     @RequestParam(required = false) String page,
                                     @RequestParam(required = false) String sortBy) {
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

        List<String> validSortBy = Arrays.asList("title", "title_desc", "price", "price_desc");
        if (sortBy != null) {
            sortBy = sortBy.trim();
            if (sortBy.isEmpty() || !validSortBy.contains(sortBy)) {
                sortBy = null;
            }
        }

        Page<Book> bookPage = bookService.getFilteredAndSortedBooks(
                null, null, null, null, sortBy, currentPage - 1, 10);

        if (invalidPage || currentPage > bookPage.getTotalPages()) {
            bookPage = bookService.getFilteredAndSortedBooks(null, null, null, null, sortBy, 0, 10);
            currentPage = 1;
        }

        model.addAttribute("bookList", bookPage.getContent());
        model.addAttribute("totalPage", bookPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("sortBy", sortBy);
        return "admin/products_ad";
    }

    @GetMapping("/admin/view-product")
    public String adminViewProduct(Model model, @RequestParam(required = false) Integer id) {
        model.addAttribute("categoryList", categoryService.findAll());
        model.addAttribute("authorList", authorService.findAll());
        model.addAttribute("languageList", languageService.findAll());
        model.addAttribute("publisherList", publisherService.findAll());
        model.addAttribute("seriesList", seriesService.findAll());
        model.addAttribute("translatorList", translatorService.findAll());

        Book book;
        if (id != null) {
            book = bookService.findById(id);
            model.addAttribute("action", "view");
        } else {
            book = new Book();
            book.setNewBook(true);
            model.addAttribute("action", "create");
        }
        model.addAttribute("book", book);
        return "admin/productdetail_ad";
    }

    @GetMapping("/admin/edit-product")
    public String adminEditProduct(Model model, @RequestParam Integer id) {
        model.addAttribute("categoryList", categoryService.findAll());
        model.addAttribute("authorList", authorService.findAll());
        model.addAttribute("languageList", languageService.findAll());
        model.addAttribute("publisherList", publisherService.findAll());
        model.addAttribute("seriesList", seriesService.findAll());
        model.addAttribute("translatorList", translatorService.findAll());

        Book book = bookService.findById(id);
        model.addAttribute("book", book);
        model.addAttribute("action", "edit");

        List<Integer> selectedTranslatorIds = book.getBookTranslatorList() != null
                ? book.getBookTranslatorList().stream()
                .map(bt -> bt.getTranslator().getId())
                .collect(java.util.stream.Collectors.toList())
                : new ArrayList<>();
        model.addAttribute("selectedTranslatorIds", selectedTranslatorIds);

        return "admin/productdetail_ad";
    }

    @GetMapping("/admin/add-product")
    public String adminAddProduct(Model model) {
        model.addAttribute("categoryList", categoryService.findAll());
        model.addAttribute("authorList", authorService.findAll());
        model.addAttribute("languageList", languageService.findAll());
        model.addAttribute("publisherList", publisherService.findAll());
        model.addAttribute("seriesList", seriesService.findAll());
        model.addAttribute("translatorList", translatorService.findAll());

        Book book = new Book();
        book.setNewBook(true);
        model.addAttribute("book", book);
        model.addAttribute("action", "create");
        return "admin/productdetail_ad";
    }

    @PostMapping("/admin/save-product")
    public String adminSaveProduct(Model model, @ModelAttribute Book book,
                                   HttpServletRequest request) throws IOException {

        book.setAuthor(authorService.findByName(request.getParameter("authorInfo")));
        book.setCategory(categoryService.findByName(request.getParameter("categoryInfo")));

        String languageParam = request.getParameter("languageInfo");
        book.setLanguage((languageParam != null && !languageParam.isEmpty())
                ? languageService.findByName(languageParam) : null);

        String publisherParam = request.getParameter("publisherInfo");
        book.setPublisher((publisherParam != null && !publisherParam.isEmpty())
                ? publisherService.findByName(publisherParam) : null);

        String seriesParam = request.getParameter("seriesInfo");
        book.setSeries((seriesParam != null && !seriesParam.isEmpty())
                ? seriesService.findByName(seriesParam) : null);

        boolean isEdit = book.getId() != null;
        String action = isEdit ? "edit" : "create";

        if (!bookService.validateBook(book)) {
            model.addAttribute("error", bookService.getBookValidationErrorMessage());
            model.addAttribute("book", book);
            model.addAttribute("action", action);
            model.addAttribute("categoryList", categoryService.findAll());
            model.addAttribute("authorList", authorService.findAll());
            model.addAttribute("languageList", languageService.findAll());
            model.addAttribute("publisherList", publisherService.findAll());
            model.addAttribute("seriesList", seriesService.findAll());
            model.addAttribute("translatorList", translatorService.findAll());
            return "admin/productdetail_ad";
        }

        Book updateBook = bookService.updateInfo(book, request, null);

        String[] translatorIds = request.getParameterValues("translatorIds");

        if (updateBook.getId() != null) {
            bookTranslatorService.deleteByBookId(updateBook.getId());
        }

        if (translatorIds != null && translatorIds.length > 0) {
            for (String translatorIdStr : translatorIds) {
                if (translatorIdStr != null && !translatorIdStr.trim().isEmpty()) {
                    try {
                        Integer translatorId = Integer.parseInt(translatorIdStr);
                        Translator translator = translatorService.findById(translatorId);

                        if (translator != null) {
                            BookTranslator bookTranslator = new BookTranslator();
                            bookTranslator.setBook(updateBook);
                            bookTranslator.setTranslator(translator);
                            bookTranslator.setRole("Main Translator");
                            bookTranslator.setCreated_at(new Date());
                            bookTranslatorService.save(bookTranslator);
                        }
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
            }
        }

        if (isEdit) {
            return "redirect:/admin/edit-product?id=" + updateBook.getId();
        } else {
            return "redirect:/admin/view-product?id=" + updateBook.getId();
        }
    }

    @GetMapping("/admin/delete-product")
    public String adminDeleteProduct(@RequestParam Integer id) {
        bookService.deleteById(id);
        return "redirect:/admin/manage-product";
    }

    // =====================================================
    // ADMIN STAFF MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-staff")
    public String adminManageStaff(Model model, @RequestParam(required = false) String page) {
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

        Page<Admin> adminPage = adminService.findByLimit(currentPage - 1, 10, null);
        if (invalidPage || currentPage > adminPage.getTotalPages()) {
            currentPage = 1;
            adminPage = adminService.findByLimit(0, 10, null);
        }

        Map<Integer, Integer> activeOrdersMap = new HashMap<>();
        for (Admin admin : adminPage.getContent()) {
            activeOrdersMap.put(admin.getId(), adminService.countActiveOrders(admin));
        }

        model.addAttribute("staffList", adminPage.getContent());
        model.addAttribute("totalPage", adminPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("activeOrdersMap", activeOrdersMap);
        return "admin/staff_ad";
    }

    @PostMapping("/admin/create-staff")
    @ResponseBody
    public Map<String, Object> adminCreateStaff(@RequestBody Map<String, String> requestData) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Validate all fields
            Map<String, String> errors = adminService.validateStaff(null,
                    requestData.get("username"), requestData.get("password"),
                    requestData.get("confirmPassword"), requestData.get("name"),
                    requestData.get("email"), requestData.get("phone"),
                    requestData.get("address"));

            if (!errors.isEmpty()) {
                response.put("success", false);
                response.put("message", errors.values().iterator().next());
                return response;
            }

            // Create staff directly without OTP verification
            adminService.createNewStaff(
                    requestData.get("username"),
                    requestData.get("password"),
                    requestData.get("name"),
                    requestData.get("email"),
                    requestData.get("phone"),
                    requestData.get("address")
            );

            response.put("success", true);
            response.put("message", "Staff created successfully and credentials sent to email");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "An error occurred: " + e.getMessage());
        }

        return response;
    }

    @GetMapping("/admin/edit-staff")
    public String adminEditStaff(Model model, @RequestParam Integer id) {
        model.addAttribute("admin", adminService.findById(id));
        return "admin/staff_detail_ad";
    }

    @PostMapping("/admin/save-staff")
    public String adminSaveStaff(Model model, @ModelAttribute Admin admin,
                                 HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            Integer adminId = admin.getId();
            String name = request.getParameter("name");
            String phone = request.getParameter("phone");
            String address = request.getParameter("address");
            String email = request.getParameter("email");

            Map<String, String> errors = adminService.validateStaff(adminId, null, null, null, name, email, phone, address);
            if (!errors.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", errors.values().iterator().next());
                return "redirect:/admin/edit-staff?id=" + adminId;
            }

            boolean emailChanged = !email.trim().equalsIgnoreCase(adminService.findById(adminId).getAccount().getEmail());
            adminService.updateStaff(adminId, name, phone, address, email);
            redirectAttributes.addFlashAttribute("successMessage",
                    emailChanged ? "Staff updated successfully. Email notification sent." : "Staff updated successfully");
            return "redirect:/admin/edit-staff?id=" + adminId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred: " + e.getMessage());
            return "redirect:/admin/edit-staff?id=" + admin.getId();
        }
    }

    @GetMapping("/admin/delete-staff")
    public String adminDeleteStaff(@RequestParam(required = true) Integer id, RedirectAttributes redirectAttributes) {
        Admin admin = adminService.findById(id);
        if (admin == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Staff not found");
            return "redirect:/admin/manage-staff";
        }
        int activeOrders = adminService.countActiveOrders(admin);
        if (activeOrders > 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete staff with " + activeOrders + " active order(s)");
            return "redirect:/admin/manage-staff";
        }
        adminService.delete(admin);
        redirectAttributes.addFlashAttribute("successMessage", "Staff deleted successfully");
        return "redirect:/admin/manage-staff";
    }

    @PostMapping("/admin/assign-order")
    public String adminAssignOrder(@RequestParam Integer orderId, @RequestParam Integer adminId,
                                   RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.findById(orderId);
            if (order == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Order not found");
                return "redirect:/admin/manage-order";
            }

            if (!("Pending".equals(order.getStatus()) || "Assigned".equals(order.getStatus())
                    || ("Approved".equals(order.getStatus()) && "PAID".equals(order.getPayment_status())))) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Order #" + orderId + " cannot be reassigned (current status: " + order.getStatus() + ")");
                return "redirect:/admin/manage-order";
            }

            Admin admin = adminService.findById(adminId);
            if (admin == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Staff not found");
                return "redirect:/admin/manage-order";
            }

            boolean isReassignment = order.getAdmin() != null && order.getAdmin().getId().equals(adminId);

            if (!isReassignment) {
                if (adminService.countActiveOrders(admin) >= AdminService.MAX_ACTIVE_ORDERS) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            admin.getName() + " has reached the maximum active-order limit (" +
                                    AdminService.MAX_ACTIVE_ORDERS + ")");
                    return "redirect:/admin/manage-order";
                }
            }

            order.setAdmin(admin);
            order.setStatus("Assigned");
            order.setUpdated_at(LocalDate.now());
            orderService.save(order);

            String message = isReassignment ?
                    "Order #" + orderId + " reassigned to " + admin.getName() :
                    "Order #" + orderId + " assigned to " + admin.getName();
            redirectAttributes.addFlashAttribute("successMessage", message);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred: " + e.getMessage());
        }
        return "redirect:/admin/manage-order";
    }

    // =====================================================
    // ADMIN ORDER MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-order")
    public String adminManageOrder(Model model, HttpServletRequest request,
                                   @RequestParam(required = false) String page,
                                   @RequestParam(required = false) String sortBy,
                                   @RequestParam(required = false) String statusFilter,
                                   @RequestParam(required = false) String paymentFilter) {
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

        Page<Order> orderPage = orderService.findByLimitWithFilters(
                currentPage - 1, 10, sortBy, statusFilter, paymentFilter);

        if (invalidPage || currentPage > orderPage.getTotalPages()) {
            currentPage = 1;
            orderPage = orderService.findByLimitWithFilters(0, 10, sortBy, statusFilter, paymentFilter);
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

        // Get order counts by status
        Map<String, Long> statusCounts = orderService.getOrderCountsByStatus();

        model.addAttribute("orderList", orderPage.getContent());
        model.addAttribute("totalPage", orderPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("paymentFilter", paymentFilter);
        model.addAttribute("orderPromotions", orderPromotions);
        model.addAttribute("eligibleStaff", adminService.findEligibleStaff());
        model.addAttribute("orderDiscounts", orderDiscounts);
        model.addAttribute("statusCounts", statusCounts);

        return "admin/orders_ad";
    }

    @GetMapping("/admin/manage-orderDetail")
    public String adminManageOrderDetail(Model model, HttpServletRequest request,
                                         @RequestParam(required = false) Integer id) {
        try {
            Order order = orderService.findById(id);

            if (order == null) {
                model.addAttribute("errorMessage", "Order not found");
                return "redirect:/admin/manage-order";
            }

            String promotionCode = null;
            if (order.getVoucher() != null) {
                promotionCode = order.getVoucher().getCode();
            }

            Double orderDiscount = order.getDiscount_amount() != null ? order.getDiscount_amount() : 0.0;
            Double finalTotal = order.getFinalTotal();

            model.addAttribute("order", order);
            model.addAttribute("promotionCode", promotionCode);
            model.addAttribute("orderDiscount", orderDiscount);
            model.addAttribute("finalTotal", finalTotal != null ? finalTotal : 0.0);
            model.addAttribute("shippingFee", order.getShipping_fee() != null ? order.getShipping_fee() : 0.0);

            return "admin/order_detail_ad";

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading order details");
            return "redirect:/admin/manage-order";
        }
    }

    @PostMapping("/admin/edit-order")
    public String adminEditOrder(@RequestParam Integer id,
                                 @RequestParam String status,
                                 RedirectAttributes redirectAttributes) {
        try {
            orderService.validateStatusTransition(id, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order #" + id + " status updated to '" + status + "' successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-order";
    }

    // =====================================================
    // CANCEL REQUEST — ADMIN
    // =====================================================

    @PostMapping("/admin/approve-cancel-request")
    public String adminApproveCancelRequest(@RequestParam Integer orderId,
                                            RedirectAttributes redirectAttributes) {
        try {
            orderService.approveCancelRequest(orderId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order #" + orderId + " cancel request approved. Order has been cancelled.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-order";
    }

    @PostMapping("/admin/reject-cancel-request")
    public String adminRejectCancelRequest(@RequestParam Integer orderId,
                                           RedirectAttributes redirectAttributes) {
        try {
            orderService.rejectCancelRequest(orderId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order #" + orderId + " cancel request rejected. Order restored to Approved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-order";
    }

    // =====================================================
    // CANCEL REQUEST — STAFF
    // =====================================================

    @PostMapping("/staff/approve-cancel-request")
    public String staffApproveCancelRequest(@RequestParam Integer orderId,
                                            Authentication authentication,
                                            RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            Account account = accountService.findByUsername(username);
            Order order = orderService.findById(orderId);

            if (order.getAdmin() == null || !order.getAdmin().getId().equals(account.getAdmin().getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized to manage this order.");
                return "redirect:/staff/orders";
            }

            orderService.approveCancelRequest(orderId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order #" + orderId + " cancel request approved. Order has been cancelled.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/orders";
    }

    @PostMapping("/staff/reject-cancel-request")
    public String staffRejectCancelRequest(@RequestParam Integer orderId,
                                           Authentication authentication,
                                           RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            Account account = accountService.findByUsername(username);
            Order order = orderService.findById(orderId);

            if (order.getAdmin() == null || !order.getAdmin().getId().equals(account.getAdmin().getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized to manage this order.");
                return "redirect:/staff/orders";
            }

            orderService.rejectCancelRequest(orderId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order #" + orderId + " cancel request rejected. Order restored to Approved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/orders";
    }

    // =====================================================
    // ADMIN CATALOG MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-author")
    public String adminManageAuthor(Model model, @RequestParam(required = false) String page) {
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

        Page<Author> authorPage = authorService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > authorPage.getTotalPages()) {
            currentPage = 1;
            authorPage = authorService.findByLimit(0, 10);
        }

        model.addAttribute("authorList", authorPage.getContent());
        model.addAttribute("totalPage", authorPage.getTotalPages());
        model.addAttribute("page", currentPage);
        return "admin/authors_ad";
    }

    @PostMapping("/admin/create-author")
    public String adminCreateAuthor(HttpServletRequest request, Model model,
                                    RedirectAttributes redirectAttributes) {
        String name = request.getParameter("name");
        String description = request.getParameter("description");

        try {
            authorService.validateNewAuthor(name);
            Author author = new Author();
            author.setName(name);
            author.setDescription(description);
            author.setCreated_at(new Date());
            authorService.save(author);
            redirectAttributes.addFlashAttribute("successMessage", "Author created successfully");
            return "redirect:/admin/manage-author";
        } catch (IllegalArgumentException e) {
            model.addAttribute("addNameError", e.getMessage());
            model.addAttribute("formName", name);
            model.addAttribute("formDescription", description);
            model.addAttribute("showAddModal", true);
            return adminManageAuthor(model, request.getParameter("page"));
        }
    }

    @PostMapping("/admin/edit-author")
    public String adminEditAuthor(Model model, HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        String name = request.getParameter("name");
        String description = request.getParameter("description");
        Integer id = null;

        try {
            id = Integer.valueOf(request.getParameter("id"));
            authorService.validateEditAuthor(id, name);
            Author author = authorService.findById(id);
            author.setName(name);
            author.setDescription(description);
            author.setUpdated_at(new Date());
            authorService.save(author);
            redirectAttributes.addFlashAttribute("successMessage", "Author updated successfully");
            return "redirect:/admin/manage-author";
        } catch (IllegalArgumentException e) {
            model.addAttribute("editNameError", e.getMessage());
            model.addAttribute("editAuthorId", String.valueOf(id));
            model.addAttribute("formName", name);
            model.addAttribute("formDescription", description);
            model.addAttribute("showEditModal", true);
            return adminManageAuthor(model, request.getParameter("page"));
        }
    }

    @GetMapping("/admin/delete-author")
    public String adminDeleteAuthor(@RequestParam(required = true) Integer id, RedirectAttributes redirectAttributes) {
        authorService.delete(authorService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Author deleted successfully");
        return "redirect:/admin/manage-author";
    }

    // =====================================================
    // ADMIN CATEGORY MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-category")
    public String adminManageCategory(Model model, @RequestParam(required = false) String page) {
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

        List<Category> categoryTree = categoryService.buildCategoryTree();
        List<Category> flattenedList = new ArrayList<>();
        categoryService.flattenCategoryTree(categoryTree, flattenedList);

        int totalCategories = flattenedList.size();
        int pageSize = 10;
        int totalPages = (int) Math.ceil((double) totalCategories / pageSize);
        if (currentPage > totalPages && totalPages > 0) currentPage = 1;

        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, totalCategories);

        model.addAttribute("categoryList", flattenedList.subList(start, end));
        model.addAttribute("totalPage", totalPages);
        model.addAttribute("page", currentPage);
        model.addAttribute("level0Categories", categoryService.getCategoriesByLevel(0));
        model.addAttribute("level1Categories", categoryService.getCategoriesByLevel(1));
        return "admin/category_book_ad";
    }

    @PostMapping("/admin/create-category")
    public String adminCreateCategory(HttpServletRequest request, Model model,
                                      RedirectAttributes redirectAttributes) {
        try {
            String name = request.getParameter("name");
            Integer level = null;
            String levelParam = request.getParameter("level");
            if (levelParam != null && !levelParam.trim().isEmpty()) level = Integer.parseInt(levelParam);
            Integer parentId = null;
            String parentIdParam = request.getParameter("parentId");
            if (parentIdParam != null && !parentIdParam.trim().isEmpty()) parentId = Integer.parseInt(parentIdParam);

            categoryService.createCategory(name, level, parentId);
            redirectAttributes.addFlashAttribute("successMessage", "Category created successfully at Level " + level);
            return "redirect:/admin/manage-category";

        } catch (Exception e) {
            model.addAttribute("addErrorMessage", e.getMessage());
            model.addAttribute("addFormName", request.getParameter("name"));
            model.addAttribute("addFormLevel", request.getParameter("level"));
            model.addAttribute("addFormParentId", request.getParameter("parentId"));
            model.addAttribute("showAddModal", true);
            return adminManageCategory(model, request.getParameter("page"));
        }
    }

    @PostMapping("/admin/edit-category")
    public String adminEditCategory(HttpServletRequest request, Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            categoryService.updateCategory(Integer.valueOf(request.getParameter("id")), request.getParameter("name"));
            redirectAttributes.addFlashAttribute("successMessage", "Category updated successfully");
            return "redirect:/admin/manage-category";

        } catch (Exception e) {
            model.addAttribute("editErrorMessage", e.getMessage());
            model.addAttribute("editCategoryId", request.getParameter("id"));
            model.addAttribute("editFormName", request.getParameter("name"));
            model.addAttribute("showEditModal", true);
            return adminManageCategory(model, request.getParameter("page"));
        }
    }

    @GetMapping("/admin/delete-category")
    public String adminDeleteCategory(@RequestParam Integer id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "Category deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-category";
    }

    // =====================================================
    // ADMIN LANGUAGE MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-language")
    public String adminManageLanguage(Model model, @RequestParam(required = false) String page) {
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

        Page<Language> languagePage = languageService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > languagePage.getTotalPages()) {
            currentPage = 1;
            languagePage = languageService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Language language : languagePage.getContent()) {
            bookCountMap.put(language.getId(), languageService.countBooksByLanguageId(language.getId()));
        }

        model.addAttribute("languageList", languagePage.getContent());
        model.addAttribute("totalPage", languagePage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        return "admin/language_ad";
    }

    @PostMapping("/admin/create-language")
    public String adminCreateLanguage(HttpServletRequest request, Model model,
                                      RedirectAttributes redirectAttributes) {
        String name = request.getParameter("name");
        String code = request.getParameter("code");

        Map<String, String> errors = languageService.validateNewLanguage(name, code);

        if (!errors.isEmpty()) {
            if (errors.containsKey("name")) model.addAttribute("addNameError", errors.get("name"));
            if (errors.containsKey("code")) model.addAttribute("addCodeError", errors.get("code"));
            model.addAttribute("formName", name);
            model.addAttribute("formCode", code);
            model.addAttribute("showAddModal", true);
            return adminManageLanguage(model, request.getParameter("page"));
        }

        languageService.createLanguage(name, code, null);
        redirectAttributes.addFlashAttribute("successMessage", "Language created successfully");
        return "redirect:/admin/manage-language";
    }

    @PostMapping("/admin/edit-language")
    public String adminEditLanguage(HttpServletRequest request, Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            String newName = request.getParameter("name");

            Map<String, String> errors = languageService.validateEditLanguage(id, newName);

            if (!errors.isEmpty()) {
                if (errors.containsKey("name")) model.addAttribute("editNameError", errors.get("name"));
                model.addAttribute("editLanguageId", String.valueOf(id));
                model.addAttribute("formName", newName);
                model.addAttribute("showEditModal", true);
                return adminManageLanguage(model, request.getParameter("page"));
            }

            languageService.updateLanguage(id, newName);
            redirectAttributes.addFlashAttribute("successMessage", "Language updated successfully");
            return "redirect:/admin/manage-language";

        } catch (NumberFormatException e) {
            model.addAttribute("errorMessage", "Invalid language ID");
            return adminManageLanguage(model, request.getParameter("page"));
        }
    }

    @GetMapping("/admin/delete-language")
    public String adminDeleteLanguage(@RequestParam Integer id, RedirectAttributes redirectAttributes) {
        try {
            languageService.deleteLanguage(id);
            redirectAttributes.addFlashAttribute("successMessage", "Language deleted successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-language";
    }

    // =====================================================
    // ADMIN PUBLISHER MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-publisher")
    public String adminManagePublisher(Model model, @RequestParam(required = false) String page) {
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

        Page<Publisher> publisherPage = publisherService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > publisherPage.getTotalPages()) {
            currentPage = 1;
            publisherPage = publisherService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Publisher publisher : publisherPage.getContent()) {
            bookCountMap.put(publisher.getId(), publisherService.countBooksByPublisherId(publisher.getId()));
        }

        model.addAttribute("publisherList", publisherPage.getContent());
        model.addAttribute("totalPage", publisherPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        return "admin/publisher_ad";
    }

    @PostMapping("/admin/create-publisher")
    public String adminCreatePublisher(HttpServletRequest request, Model model,
                                       RedirectAttributes redirectAttributes) {
        String name = request.getParameter("name");
        String country = request.getParameter("country");
        String website = request.getParameter("website");
        String description = request.getParameter("description");

        Map<String, String> errors = publisherService.validatePublisher(null, name, country, website);

        if (!errors.isEmpty()) {
            if (errors.containsKey("name"))
                model.addAttribute("addNameError", errors.get("name"));
            if (errors.containsKey("country"))
                model.addAttribute("addCountryError", errors.get("country"));
            if (errors.containsKey("website"))
                model.addAttribute("addWebsiteError", errors.get("website"));

            model.addAttribute("formName", name);
            model.addAttribute("formCountry", country);
            model.addAttribute("formWebsite", website);
            model.addAttribute("formDescription", description);
            model.addAttribute("showAddModal", true);
            return adminManagePublisher(model, request.getParameter("page"));
        }

        publisherService.createPublisher(name, country, website, description);
        redirectAttributes.addFlashAttribute("successMessage", "Publisher created successfully");
        return "redirect:/admin/manage-publisher";
    }

    @PostMapping("/admin/edit-publisher")
    public String adminEditPublisher(HttpServletRequest request, Model model,
                                     RedirectAttributes redirectAttributes) {
        try {
            Integer id = Integer.parseInt(request.getParameter("id"));
            String name = request.getParameter("name");
            String country = request.getParameter("country");
            String website = request.getParameter("website");
            String description = request.getParameter("description");

            Map<String, String> errors = publisherService.validatePublisher(id, name, country, website);

            if (!errors.isEmpty()) {
                if (errors.containsKey("name"))
                    model.addAttribute("editNameError", errors.get("name"));
                if (errors.containsKey("country"))
                    model.addAttribute("editCountryError", errors.get("country"));
                if (errors.containsKey("website"))
                    model.addAttribute("editWebsiteError", errors.get("website"));

                model.addAttribute("editPublisherId", String.valueOf(id));
                model.addAttribute("formName", name);
                model.addAttribute("formCountry", country);
                model.addAttribute("formWebsite", website);
                model.addAttribute("formDescription", description);
                model.addAttribute("showEditModal", true);
                return adminManagePublisher(model, request.getParameter("page"));
            }

            publisherService.updatePublisher(id, name, country, website, description);
            redirectAttributes.addFlashAttribute("successMessage", "Publisher updated successfully");
            return "redirect:/admin/manage-publisher";

        } catch (NumberFormatException e) {
            model.addAttribute("errorMessage", "Invalid publisher ID");
            return adminManagePublisher(model, request.getParameter("page"));
        }
    }

    @GetMapping("/admin/delete-publisher")
    public String adminDeletePublisher(@RequestParam Integer id, RedirectAttributes redirectAttributes) {
        try {
            publisherService.deletePublisher(id);
            redirectAttributes.addFlashAttribute("successMessage", "Publisher deleted successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-publisher";
    }

    @GetMapping("/api/countries")
    @ResponseBody
    public List<String> getCountries() {
        return Arrays.asList(
                "Afghanistan", "Albania", "Algeria", "Andorra", "Angola", "Argentina", "Armenia",
                "Australia", "Austria", "Azerbaijan", "Bahamas", "Bahrain", "Bangladesh", "Barbados",
                "Belarus", "Belgium", "Belize", "Benin", "Bhutan", "Bolivia", "Bosnia and Herzegovina",
                "Botswana", "Brazil", "Brunei", "Bulgaria", "Burkina Faso", "Burundi", "Cambodia",
                "Cameroon", "Canada", "Cape Verde", "Central African Republic", "Chad", "Chile", "China",
                "Colombia", "Comoros", "Congo", "Costa Rica", "Croatia", "Cuba", "Cyprus", "Czech Republic",
                "Denmark", "Djibouti", "Dominica", "Dominican Republic", "East Timor", "Ecuador", "Egypt",
                "El Salvador", "Equatorial Guinea", "Eritrea", "Estonia", "Ethiopia", "Fiji", "Finland",
                "France", "Gabon", "Gambia", "Georgia", "Germany", "Ghana", "Greece", "Grenada",
                "Guatemala", "Guinea", "Guinea-Bissau", "Guyana", "Haiti", "Honduras", "Hungary",
                "Iceland", "India", "Indonesia", "Iran", "Iraq", "Ireland", "Israel", "Italy", "Ivory Coast",
                "Jamaica", "Japan", "Jordan", "Kazakhstan", "Kenya", "Kiribati", "Kosovo", "Kuwait",
                "Kyrgyzstan", "Laos", "Latvia", "Lebanon", "Lesotho", "Liberia", "Libya", "Liechtenstein",
                "Lithuania", "Luxembourg", "Madagascar", "Malawi", "Malaysia", "Maldives", "Mali", "Malta",
                "Marshall Islands", "Mauritania", "Mauritius", "Mexico", "Micronesia", "Moldova", "Monaco",
                "Mongolia", "Montenegro", "Morocco", "Mozambique", "Myanmar", "Namibia", "Nauru", "Nepal",
                "Netherlands", "New Zealand", "Nicaragua", "Niger", "Nigeria", "North Korea", "North Macedonia",
                "Norway", "Oman", "Pakistan", "Palau", "Palestine", "Panama", "Papua New Guinea", "Paraguay",
                "Peru", "Philippines", "Poland", "Portugal", "Qatar", "Romania", "Russia", "Rwanda",
                "Saint Kitts and Nevis", "Saint Lucia", "Saint Vincent and the Grenadines", "Samoa",
                "San Marino", "Sao Tome and Principe", "Saudi Arabia", "Senegal", "Serbia", "Seychelles",
                "Sierra Leone", "Singapore", "Slovakia", "Slovenia", "Solomon Islands", "Somalia", "South Africa",
                "South Korea", "South Sudan", "Spain", "Sri Lanka", "Sudan", "Suriname", "Sweden", "Switzerland",
                "Syria", "Taiwan", "Tajikistan", "Tanzania", "Thailand", "Togo", "Tonga", "Trinidad and Tobago",
                "Tunisia", "Turkey", "Turkmenistan", "Tuvalu", "Uganda", "Ukraine", "United Arab Emirates",
                "United Kingdom", "United States", "Uruguay", "Uzbekistan", "Vanuatu", "Vatican City",
                "Venezuela", "Vietnam", "Yemen", "Zambia", "Zimbabwe"
        );
    }
    // =====================================================
    // ADMIN SERIES MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-series")
    public String adminManageSeries(Model model, @RequestParam(required = false) String page) {
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

        Page<Series> seriesPage = seriesService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > seriesPage.getTotalPages()) {
            currentPage = 1;
            seriesPage = seriesService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Series series : seriesPage.getContent()) {
            bookCountMap.put(series.getId(), seriesService.countBooksBySeriesId(series.getId()));
        }

        model.addAttribute("seriesList", seriesPage.getContent());
        model.addAttribute("totalPage", seriesPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        return "admin/series_ad";
    }

    @PostMapping("/admin/create-series")
    public String adminCreateSeries(HttpServletRequest request, Model model,
                                    RedirectAttributes redirectAttributes) {
        String name = request.getParameter("name");
        String description = request.getParameter("description");
        String totalVolumesParam = request.getParameter("totalVolumes");
        String status = request.getParameter("status");

        Integer totalVolumes = null;
        Map<String, String> errors = new LinkedHashMap<>();

        if (totalVolumesParam != null && !totalVolumesParam.trim().isEmpty()) {
            try {
                totalVolumes = Integer.valueOf(totalVolumesParam);
            } catch (NumberFormatException e) {
                errors.put("totalVolumes", "Total volumes must be a valid number");
            }
        }

        if (!errors.containsKey("totalVolumes")) {
            errors.putAll(seriesService.validateNewSeries(name, totalVolumes));
        }

        if (!errors.isEmpty()) {
            if (errors.containsKey("name")) model.addAttribute("addNameError", errors.get("name"));
            if (errors.containsKey("totalVolumes")) model.addAttribute("addVolumesError", errors.get("totalVolumes"));
            model.addAttribute("formName", name);
            model.addAttribute("formDescription", description);
            model.addAttribute("formTotalVolumes", totalVolumesParam);
            model.addAttribute("formStatus", status);
            model.addAttribute("showAddModal", true);
            return adminManageSeries(model, request.getParameter("page"));
        }

        seriesService.createSeries(name, description, totalVolumes, status);
        redirectAttributes.addFlashAttribute("successMessage", "Series created successfully");
        return "redirect:/admin/manage-series";
    }

    @PostMapping("/admin/edit-series")
    public String adminEditSeries(HttpServletRequest request, Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            String newName = request.getParameter("name");
            String description = request.getParameter("description");
            String totalVolumesParam = request.getParameter("totalVolumes");
            String status = request.getParameter("status");

            Integer totalVolumes = null;
            Map<String, String> errors = new LinkedHashMap<>();

            if (totalVolumesParam != null && !totalVolumesParam.trim().isEmpty()) {
                try {
                    totalVolumes = Integer.valueOf(totalVolumesParam);
                } catch (NumberFormatException e) {
                    errors.put("totalVolumes", "Total volumes must be a valid number");
                }
            }

            if (!errors.containsKey("totalVolumes")) {
                errors.putAll(seriesService.validateEditSeries(id, newName, totalVolumes));
            }

            if (!errors.isEmpty()) {
                if (errors.containsKey("name")) model.addAttribute("editNameError", errors.get("name"));
                if (errors.containsKey("totalVolumes"))
                    model.addAttribute("editVolumesError", errors.get("totalVolumes"));
                model.addAttribute("editSeriesId", String.valueOf(id));
                model.addAttribute("formName", newName);
                model.addAttribute("formDescription", description);
                model.addAttribute("formTotalVolumes", totalVolumesParam);
                model.addAttribute("formStatus", status);
                model.addAttribute("showEditModal", true);
                return adminManageSeries(model, request.getParameter("page"));
            }

            seriesService.updateSeries(id, newName, description, totalVolumes, status);
            redirectAttributes.addFlashAttribute("successMessage", "Series updated successfully");
            return "redirect:/admin/manage-series";

        } catch (NumberFormatException e) {
            model.addAttribute("errorMessage", "Invalid series ID");
            return adminManageSeries(model, request.getParameter("page"));
        }
    }

    @GetMapping("/admin/delete-series")
    public String adminDeleteSeries(@RequestParam Integer id, RedirectAttributes redirectAttributes) {
        try {
            seriesService.deleteSeries(id);
            redirectAttributes.addFlashAttribute("successMessage", "Series deleted successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-series";
    }

    // =====================================================
    // ADMIN TRANSLATOR MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-translator")
    public String adminManageTranslator(Model model, @RequestParam(required = false) String page) {
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

        Page<Translator> translatorPage = translatorService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > translatorPage.getTotalPages()) {
            currentPage = 1;
            translatorPage = translatorService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Translator translator : translatorPage.getContent()) {
            bookCountMap.put(translator.getId(), translatorService.countBooksByTranslatorId(translator.getId()));
        }

        model.addAttribute("translatorList", translatorPage.getContent());
        model.addAttribute("totalPage", translatorPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        return "admin/translator_ad";
    }

    @PostMapping("/admin/create-translator")
    public String adminCreateTranslator(HttpServletRequest request, Model model,
                                        RedirectAttributes redirectAttributes) {
        String name = request.getParameter("name");
        String bio = request.getParameter("bio");
        String page = request.getParameter("page");

        Map<String, String> errors = translatorService.validateNewTranslator(name);

        if (!errors.isEmpty()) {
            model.addAttribute("addNameError", errors.get("name"));
            model.addAttribute("formName", name);
            model.addAttribute("formBio", bio);
            model.addAttribute("showAddModal", true);
            return adminManageTranslator(model, page);
        }

        translatorService.createTranslator(name, bio);
        redirectAttributes.addFlashAttribute("successMessage", "Translator created successfully");
        return "redirect:/admin/manage-translator";
    }

    @PostMapping("/admin/edit-translator")
    public String adminEditTranslator(HttpServletRequest request, Model model,
                                      RedirectAttributes redirectAttributes) {
        String page = request.getParameter("page");
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            String name = request.getParameter("name");
            String bio = request.getParameter("bio");

            Map<String, String> errors = translatorService.validateEditTranslator(id, name);

            if (!errors.isEmpty()) {
                model.addAttribute("editNameError", errors.get("name"));
                model.addAttribute("editTranslatorId", String.valueOf(id));
                model.addAttribute("formName", name);
                model.addAttribute("formBio", bio);
                model.addAttribute("showEditModal", true);
                return adminManageTranslator(model, page);
            }

            translatorService.updateTranslator(id, name, bio);
            redirectAttributes.addFlashAttribute("successMessage", "Translator updated successfully");
            return "redirect:/admin/manage-translator";

        } catch (NumberFormatException e) {
            model.addAttribute("errorMessage", "Invalid translator ID");
            return adminManageTranslator(model, page);
        }
    }

    @GetMapping("/admin/delete-translator")
    public String adminDeleteTranslator(@RequestParam Integer id, RedirectAttributes redirectAttributes) {
        try {
            translatorService.deleteTranslator(id);
            redirectAttributes.addFlashAttribute("successMessage", "Translator deleted successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/manage-translator";
    }

    // =====================================================
    // ADMIN PROMOTION MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-promotion")
    public String adminManagePromotion(Model model,
                                       @RequestParam(required = false) String page,
                                       @RequestParam(required = false) String sortBy) {
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

        Page<Voucher> promotionPage = voucherService.findByLimit(currentPage - 1, 10, sortBy);
        if (invalidPage || currentPage > promotionPage.getTotalPages()) {
            promotionPage = voucherService.findByLimit(0, 10, sortBy);
            currentPage = 1;
        }

        model.addAttribute("promotionList", promotionPage.getContent());
        model.addAttribute("totalPage", promotionPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("sortBy", sortBy);
        return "admin/promotion_ad";
    }

    @PostMapping("/admin/create-promotion")
    public String adminCreatePromotion(HttpServletRequest request, Model model,
                                       RedirectAttributes redirectAttributes) {
        try {
            String code = request.getParameter("code");
            String name = request.getParameter("name");
            Double discountValue = Double.valueOf(request.getParameter("discountValue"));
            LocalDate startDate = LocalDate.parse(request.getParameter("startDate"));
            LocalDate endDate = LocalDate.parse(request.getParameter("endDate"));
            Integer maxUsage = Integer.valueOf(request.getParameter("maxUsage"));
            Double minOrder = Double.valueOf(request.getParameter("minOrder"));

            voucherService.validateNewVoucher(code, name, discountValue, startDate, endDate, maxUsage, minOrder);

            Voucher voucher = new Voucher();
            voucher.setCode(code.trim());
            voucher.setName(name.trim());
            voucher.setDiscountValue(discountValue);
            voucher.setStartDate(startDate);
            voucher.setEndDate(endDate);
            voucher.setMaxUsage(maxUsage);
            voucher.setUsedCount(0);
            voucher.setMinOrder(minOrder);
            voucher.setStatus("CREATED");
            voucherService.save(voucher);

            redirectAttributes.addFlashAttribute("successMessage", "Promotion created successfully");
            return "redirect:/admin/manage-promotion";

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("code")) {
                model.addAttribute("addCodeError", msg);
            } else {
                model.addAttribute("addGeneralError", msg);
            }
            model.addAttribute("showAddModal", true);

            model.addAttribute("addCode", request.getParameter("code"));
            model.addAttribute("addName", request.getParameter("name"));
            model.addAttribute("addMinOrder", request.getParameter("minOrder"));
            model.addAttribute("addDiscountValue", request.getParameter("discountValue"));
            model.addAttribute("addStartDate", request.getParameter("startDate"));
            model.addAttribute("addEndDate", request.getParameter("endDate"));
            model.addAttribute("addMaxUsage", request.getParameter("maxUsage"));

            return adminManagePromotion(model, request.getParameter("page"), request.getParameter("sortBy"));

        } catch (Exception e) {
            model.addAttribute("addGeneralError", "Invalid input data: " + e.getMessage());
            model.addAttribute("showAddModal", true);

            model.addAttribute("addCode", request.getParameter("code"));
            model.addAttribute("addName", request.getParameter("name"));
            model.addAttribute("addMinOrder", request.getParameter("minOrder"));
            model.addAttribute("addDiscountValue", request.getParameter("discountValue"));
            model.addAttribute("addStartDate", request.getParameter("startDate"));
            model.addAttribute("addEndDate", request.getParameter("endDate"));
            model.addAttribute("addMaxUsage", request.getParameter("maxUsage"));

            return adminManagePromotion(model, request.getParameter("page"), request.getParameter("sortBy"));
        }
    }

    @PostMapping("/admin/edit-promotion")
    public String adminEditPromotion(HttpServletRequest request, Model model,
                                     RedirectAttributes redirectAttributes) {
        try {
            Integer id = Integer.valueOf(request.getParameter("id"));
            String name = request.getParameter("name");
            Double discountValue = Double.valueOf(request.getParameter("discountValue"));
            LocalDate startDate = LocalDate.parse(request.getParameter("startDate"));
            LocalDate endDate = LocalDate.parse(request.getParameter("endDate"));
            Integer maxUsage = Integer.valueOf(request.getParameter("maxUsage"));
            Double minOrder = Double.valueOf(request.getParameter("minOrder"));

            voucherService.validateEditVoucher(id, name, discountValue, startDate, endDate, maxUsage, minOrder);

            Voucher voucher = voucherService.findById(id);
            if (voucher == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Promotion not found");
                return "redirect:/admin/manage-promotion";
            }

            voucher.setName(name.trim());
            voucher.setDiscountValue(discountValue);
            voucher.setStartDate(startDate);
            voucher.setEndDate(endDate);
            voucher.setMaxUsage(maxUsage);
            voucher.setMinOrder(minOrder);
            voucherService.save(voucher);

            redirectAttributes.addFlashAttribute("successMessage", "Promotion updated successfully");
            return "redirect:/admin/manage-promotion";

        } catch (IllegalArgumentException e) {
            model.addAttribute("editGeneralError", e.getMessage());
            model.addAttribute("editPromotionId", request.getParameter("id"));

            model.addAttribute("editName", request.getParameter("name"));
            model.addAttribute("editMinOrder", request.getParameter("minOrder"));
            model.addAttribute("editDiscountValue", request.getParameter("discountValue"));
            model.addAttribute("editStartDate", request.getParameter("startDate"));
            model.addAttribute("editEndDate", request.getParameter("endDate"));
            model.addAttribute("editMaxUsage", request.getParameter("maxUsage"));

            return adminManagePromotion(model, request.getParameter("page"), request.getParameter("sortBy"));

        } catch (Exception e) {
            model.addAttribute("editGeneralError", "Invalid input data: " + e.getMessage());
            model.addAttribute("editPromotionId", request.getParameter("id"));

            model.addAttribute("editName", request.getParameter("name"));
            model.addAttribute("editMinOrder", request.getParameter("minOrder"));
            model.addAttribute("editDiscountValue", request.getParameter("discountValue"));
            model.addAttribute("editStartDate", request.getParameter("startDate"));
            model.addAttribute("editEndDate", request.getParameter("endDate"));
            model.addAttribute("editMaxUsage", request.getParameter("maxUsage"));

            return adminManagePromotion(model, request.getParameter("page"), request.getParameter("sortBy"));
        }
    }

    @PostMapping("/admin/delete-promotion")
    public String adminDeletePromotion(@RequestParam(required = true) Integer id,
                                       RedirectAttributes redirectAttributes) {
        try {
            voucherService.deleteVoucher(id);
            redirectAttributes.addFlashAttribute("successMessage", "Promotion deleted successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred: " + e.getMessage());
        }
        return "redirect:/admin/manage-promotion";
    }

// =====================================================
    // ADMIN COMMENT MANAGEMENT (/admin/*)
    // =====================================================

    @GetMapping("/admin/manage-comment")
    public String adminManageComment(Model model,
                                     @RequestParam(required = false) String page,
                                     @RequestParam(required = false) String statusFilter) {
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

        Page<Comment> commentPage;
        if (statusFilter != null && !statusFilter.trim().isEmpty()
                && java.util.List.of("PENDING", "APPROVED", "HIDDEN").contains(statusFilter)) {
            statusFilter = statusFilter.trim().toUpperCase();
            commentPage = commentService.findByStatusPaged(statusFilter, currentPage - 1, 10);
        } else {
            statusFilter = null;
            commentPage = commentService.findAllPaged(currentPage - 1, 10);
        }

        if (invalidPage || (commentPage.getTotalPages() > 0 && currentPage > commentPage.getTotalPages())) {
            currentPage = 1;
            commentPage = (statusFilter != null)
                    ? commentService.findByStatusPaged(statusFilter, 0, 10)
                    : commentService.findAllPaged(0, 10);
        }

        model.addAttribute("commentList", commentPage.getContent());
        model.addAttribute("totalPage", commentPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("statusFilter", statusFilter);
        return "admin/comment_ad";
    }

    @PostMapping("/admin/update-comment-status")
    public String adminUpdateCommentStatus(@RequestParam Integer commentId,
                                           @RequestParam String newStatus,
                                           @RequestParam(required = false) String page,
                                           @RequestParam(required = false) String statusFilter,
                                           RedirectAttributes redirectAttributes) {
        try {
            commentService.adminUpdateStatus(commentId, newStatus.toUpperCase());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Comment #" + commentId + " status updated to " + newStatus);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        String redirect = "redirect:/admin/manage-comment";
        boolean hasParam = false;
        if (page != null) {
            redirect += "?page=" + page;
            hasParam = true;
        }
        if (statusFilter != null) {
            redirect += (hasParam ? "&" : "?") + "statusFilter=" + statusFilter;
        }
        return redirect;
    }

    @GetMapping("/admin/spam-config")
    @ResponseBody
    public Map<String, Object> getSpamConfig() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(Paths.get(SPAM_CONFIG_PATH).toFile(), Map.class);
        } catch (Exception e) {
            Map<String, Object> defaults = new HashMap<>();
            defaults.put("offensive", List.of("stupid", "useless", "trash", "scam"));
            defaults.put("adlink", List.of("buy now", "click here", "huge discount", "contact zalo"));
            defaults.put("irrelevant", List.of("casino", "betting", "forex", "crypto"));
            return defaults;
        }
    }

    @PostMapping("/admin/spam-config")
    @ResponseBody
    public Map<String, Object> saveSpamConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> response = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(Paths.get(SPAM_CONFIG_PATH).toFile(), config);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }


    // =====================================================
    // STATISTICS (/admin/*)
    // =====================================================

    @GetMapping("/admin/statistic")
    public String adminStatistic(Model model, HttpServletRequest request) {
        Map<String, Double> weeklyRevenue = statisticService.getWeeklyRevenue();
        Map<String, Double> monthlyRevenue = statisticService.getMonthlyRevenue();
        Map<String, Double> yearlyRevenue = statisticService.getYearlyRevenue();

        if (weeklyRevenue == null || weeklyRevenue.isEmpty()) weeklyRevenue = new LinkedHashMap<>();
        if (monthlyRevenue == null || monthlyRevenue.isEmpty()) monthlyRevenue = new LinkedHashMap<>();
        if (yearlyRevenue == null || yearlyRevenue.isEmpty()) yearlyRevenue = new LinkedHashMap<>();

        model.addAttribute("orderStats", statisticService.getOrderStatisticsByStatus());
        model.addAttribute("weeklyRevenue", weeklyRevenue);
        model.addAttribute("monthlyRevenue", monthlyRevenue);
        model.addAttribute("yearlyRevenue", yearlyRevenue);
        model.addAttribute("topBooks", statisticService.getTopSellingBooks());
        model.addAttribute("totalRevenue", statisticService.getTotalRevenue());
        model.addAttribute("totalOrders", statisticService.getTotalOrders());
        model.addAttribute("totalBooksSold", statisticService.getTotalBooksSold());

        return "admin/statistic_ad";
    }

    @GetMapping("/admin/statistic/range")
    @ResponseBody
    public Map<String, Object> adminStatisticByRange(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            return statisticService.getRevenueByDateRange(
                    LocalDate.parse(from), LocalDate.parse(to));
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    // =====================================================
    // STAFF BOOK MANAGEMENT (/staff/*)
    // =====================================================

    @GetMapping("/staff/books")
    public String staffBooks(Model model, Authentication authentication,
                             @RequestParam(required = false) String page,
                             @RequestParam(required = false) String sortBy,
                             @RequestParam(required = false) String keyword) {

        String username = authentication.getName();
        Account account = accountService.findByUsername(username);

        if (account == null || account.getAdmin() == null) {
            model.addAttribute("errorMessage", "Staff account not found");
            return "redirect:/";
        }

        Admin currentStaff = account.getAdmin();
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

        List<String> validSortBy = Arrays.asList("title", "title_desc", "price", "price_desc");
        if (sortBy != null) {
            sortBy = sortBy.trim();
            if (sortBy.isEmpty() || !validSortBy.contains(sortBy)) sortBy = null;
        }

        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) keyword = null;
        }

        Page<Book> bookPage = bookService.getFilteredAndSortedBooks(
                keyword, null, null, null, sortBy, currentPage - 1, 10);

        if (invalidPage || currentPage > bookPage.getTotalPages()) {
            bookPage = bookService.getFilteredAndSortedBooks(keyword, null, null, null, sortBy, 0, 10);
            currentPage = 1;
        }

        model.addAttribute("bookList", bookPage.getContent());
        model.addAttribute("totalPage", bookPage.getTotalPages());
        model.addAttribute("page", currentPage);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentStaff", currentStaff);

        return "staff/books_st";
    }

    @GetMapping("/staff/book-detail")
    public String staffBookDetail(Model model, Authentication authentication,
                                  @RequestParam(required = false) Integer id,
                                  @RequestParam(required = false, defaultValue = "view") String mode) {

        String username = authentication.getName();
        Account account = accountService.findByUsername(username);

        if (account == null || account.getAdmin() == null) {
            model.addAttribute("errorMessage", "Staff account not found");
            return "redirect:/";
        }

        Admin currentStaff = account.getAdmin();

        if (id == null) return "redirect:/staff/books";

        Book book = bookService.findById(id);
        if (book == null) {
            model.addAttribute("errorMessage", "Book not found");
            return "redirect:/staff/books";
        }

        model.addAttribute("categoryList", categoryService.findAll());
        model.addAttribute("authorList", authorService.findAll());
        model.addAttribute("languageList", languageService.findAll());
        model.addAttribute("publisherList", publisherService.findAll());
        model.addAttribute("seriesList", seriesService.findAll());
        model.addAttribute("translatorList", translatorService.findAll());
        model.addAttribute("book", book);
        model.addAttribute("currentStaff", currentStaff);
        model.addAttribute("mode", "view");

        return "staff/book_detail_st";
    }

    @GetMapping("/staff/edit-book")
    public String staffEditBookStock(Model model, Authentication authentication,
                                     @RequestParam(required = false) Integer id) {

        String username = authentication.getName();
        Account account = accountService.findByUsername(username);

        if (account == null || account.getAdmin() == null) {
            model.addAttribute("errorMessage", "Staff account not found");
            return "redirect:/";
        }

        Admin currentStaff = account.getAdmin();

        if (id == null) return "redirect:/staff/books";

        Book book = bookService.findById(id);
        if (book == null) {
            model.addAttribute("errorMessage", "Book not found");
            return "redirect:/staff/books";
        }

        model.addAttribute("categoryList", categoryService.findAll());
        model.addAttribute("authorList", authorService.findAll());
        model.addAttribute("languageList", languageService.findAll());
        model.addAttribute("publisherList", publisherService.findAll());
        model.addAttribute("seriesList", seriesService.findAll());
        model.addAttribute("translatorList", translatorService.findAll());
        model.addAttribute("book", book);
        model.addAttribute("currentStaff", currentStaff);
        model.addAttribute("mode", "edit");

        return "staff/book_detail_st";
    }

    @PostMapping("/staff/save")
    public String staffUpdateBookStock(@ModelAttribute Book book,
                                       HttpServletRequest request,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            Account account = accountService.findByUsername(username);

            if (account == null || account.getAdmin() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Staff account not found");
                return "redirect:/staff/books";
            }

            if (book.getId() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid book ID");
                return "redirect:/staff/books";
            }

            Book existingBook = bookService.findById(book.getId());
            if (existingBook == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Book not found");
                return "redirect:/staff/books";
            }

            String stockParam = request.getParameter("stock");
            if (stockParam != null && !stockParam.trim().isEmpty()) {
                try {
                    Integer newStock = Integer.valueOf(stockParam);
                    if (newStock < 0) {
                        redirectAttributes.addFlashAttribute("errorMessage", "Stock quantity cannot be negative");
                        return "redirect:/staff/edit-book-stock?id=" + book.getId();
                    }
                    existingBook.setNumber_in_stock(newStock);
                    existingBook.setUpdated_at(new Date());
                    bookService.save(existingBook);

                    redirectAttributes.addFlashAttribute("successMessage",
                            "Stock quantity updated successfully! New stock: " + newStock + " copies");
                } catch (NumberFormatException e) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Invalid stock quantity");
                    return "redirect:/staff/edit-book-stock?id=" + book.getId();
                }
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Please enter a stock quantity");
                return "redirect:/staff/edit-book-stock?id=" + book.getId();
            }

            return "redirect:/staff/book-detail?id=" + book.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred: " + e.getMessage());
            return "redirect:/staff/books";
        }
    }

    // =====================================================
    // STAFF ORDER MANAGEMENT (/staff/*)
    // =====================================================

    @GetMapping("/staff/orders")
    public String staffOrders(Model model, Authentication authentication,
                              @RequestParam(required = false) String page,
                              @RequestParam(required = false) String sortBy) {

        String username = authentication.getName();
        Account account = accountService.findByUsername(username);

        if (account == null || account.getAdmin() == null) {
            model.addAttribute("errorMessage", "Staff account not found");
            return "redirect:/";
        }

        Admin currentStaff = account.getAdmin();
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

        Page<Order> orderPage = orderService.findByAdminLimit(currentStaff.getId(), currentPage - 1, 10, sortBy);

        if (invalidPage || (orderPage.getTotalPages() > 0 && currentPage > orderPage.getTotalPages())) {
            currentPage = 1;
            orderPage = orderService.findByAdminLimit(currentStaff.getId(), 0, 10, sortBy);
        }

        Map<Integer, String> orderPromotions = new HashMap<>();
        Map<Integer, Double> orderDiscounts = new HashMap<>();
        Map<Integer, Double> orderFinalTotals = new HashMap<>();

        for (Order order : orderPage.getContent()) {
            if (order.getVoucher() != null) {
                orderPromotions.put(order.getId(), order.getVoucher().getCode());
            }
            Double discount = order.getDiscount_amount() != null ? order.getDiscount_amount() : 0.0;
            orderDiscounts.put(order.getId(), discount);

            Double finalTotal = order.getFinalTotal();
            orderFinalTotals.put(order.getId(), finalTotal != null ? finalTotal : 0.0);
        }

        model.addAttribute("orderList", orderPage.getContent());
        model.addAttribute("totalPage", Math.max(orderPage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("orderPromotions", orderPromotions);
        model.addAttribute("orderDiscounts", orderDiscounts);
        model.addAttribute("orderFinalTotals", orderFinalTotals);
        model.addAttribute("currentStaff", currentStaff);

        return "staff/orders_st";
    }

    @GetMapping("/staff/order-detail")
    public String staffOrderDetail(Model model, Authentication authentication,
                                   @RequestParam(required = false) Integer id) {

        if (id == null) return "redirect:/staff/orders";

        String username = authentication.getName();
        Account account = accountService.findByUsername(username);

        if (account == null || account.getAdmin() == null) {
            model.addAttribute("errorMessage", "Staff account not found");
            return "redirect:/";
        }

        Admin currentStaff = account.getAdmin();
        Order order = orderService.findById(id);

        if (order == null) {
            model.addAttribute("errorMessage", "Order not found");
            return "redirect:/staff/orders";
        }

        if (order.getAdmin() == null || !order.getAdmin().getId().equals(currentStaff.getId())) {
            model.addAttribute("errorMessage", "You are not authorized to view this order");
            return "redirect:/staff/orders";
        }

        String promotionCode = null;
        if (order.getVoucher() != null) promotionCode = order.getVoucher().getCode();

        Double orderDiscount = order.getDiscount_amount() != null ? order.getDiscount_amount() : 0.0;
        Double finalTotal = order.getFinalTotal();

        model.addAttribute("order", order);
        model.addAttribute("currentStaff", currentStaff);
        model.addAttribute("promotionCode", promotionCode);
        model.addAttribute("orderDiscount", orderDiscount);
        model.addAttribute("finalTotal", finalTotal != null ? finalTotal : 0.0);
        model.addAttribute("shippingFee", order.getShipping_fee() != null ? order.getShipping_fee() : 0.0);

        return "staff/order_detail_st";
    }

    @PostMapping("/staff/update-order")
    public String staffUpdateOrder(HttpServletRequest request, Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            int id = Integer.valueOf(request.getParameter("id"));
            String newStatus = request.getParameter("status");

            String username = authentication.getName();
            Account account = accountService.findByUsername(username);

            if (account == null || account.getAdmin() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Staff account not found");
                return "redirect:/staff/orders";
            }

            Admin currentStaff = account.getAdmin();
            Order order = orderService.findById(id);

            if (order == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Order not found");
                return "redirect:/staff/orders";
            }

            if (order.getAdmin() == null || !order.getAdmin().getId().equals(currentStaff.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to update this order");
                return "redirect:/staff/orders";
            }

            orderService.validateStatusTransition(id, newStatus);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order #" + id + " status updated to '" + newStatus + "' successfully");
            return "redirect:/staff/orders";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/staff/orders";
        }
    }

    @PostMapping("/staff/confirm-payment")
    public String staffConfirmPayment(@RequestParam Integer orderId,
                                      @RequestParam(required = false) String paymentNote,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            Account account = accountService.findByUsername(username);

            if (account == null || account.getAdmin() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Staff account not found");
                return "redirect:/staff/orders";
            }

            Admin currentStaff = account.getAdmin();
            Order order = orderService.findById(orderId);

            if (order == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Order not found");
                return "redirect:/staff/orders";
            }

            if (order.getAdmin() == null || !order.getAdmin().getId().equals(currentStaff.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to confirm payment for this order");
                return "redirect:/staff/orders";
            }

            orderService.confirmPayment(orderId,
                    paymentNote != null ? paymentNote : "Shipper confirmed customer paid cash upon delivery");

            redirectAttributes.addFlashAttribute("successMessage",
                    "Payment confirmed successfully for Order #" + orderId);
            return "redirect:/staff/order-detail?id=" + orderId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/staff/orders";
        }
    }

    // =====================================================
    // STAFF VIEW-ONLY SECTIONS (/staff/*)
    // =====================================================

    @GetMapping("/staff/languages")
    public String staffLanguages(Model model, Authentication authentication,
                                 @RequestParam(required = false) String page) {
        String username = authentication.getName();
        Account account = accountService.findByUsername(username);
        if (account == null || account.getAdmin() == null) return "redirect:/";
        Admin currentStaff = account.getAdmin();

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

        Page<Language> languagePage = languageService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > languagePage.getTotalPages()) {
            currentPage = 1;
            languagePage = languageService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Language lang : languagePage.getContent()) {
            bookCountMap.put(lang.getId(), languageService.countBooksByLanguageId(lang.getId()));
        }

        model.addAttribute("languageList", languagePage.getContent());
        model.addAttribute("totalPage", Math.max(languagePage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        model.addAttribute("currentStaff", currentStaff);
        return "staff/language_st";
    }

    @GetMapping("/staff/categories")
    public String staffCategories(Model model, Authentication authentication,
                                  @RequestParam(required = false) String page) {
        String username = authentication.getName();
        Account account = accountService.findByUsername(username);
        if (account == null || account.getAdmin() == null) return "redirect:/";
        Admin currentStaff = account.getAdmin();

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

        Page<Category> categoryPage = categoryService.findByLimit(currentPage - 1, 10, null);
        if (invalidPage || currentPage > categoryPage.getTotalPages()) {
            currentPage = 1;
            categoryPage = categoryService.findByLimit(0, 10, null);
        }

        model.addAttribute("categoryList", categoryPage.getContent());
        model.addAttribute("totalPage", Math.max(categoryPage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("currentStaff", currentStaff);
        return "staff/category_st";
    }

    @GetMapping("/staff/authors")
    public String staffAuthors(Model model, Authentication authentication,
                               @RequestParam(required = false) String page) {
        String username = authentication.getName();
        Account account = accountService.findByUsername(username);
        if (account == null || account.getAdmin() == null) return "redirect:/";
        Admin currentStaff = account.getAdmin();

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

        Page<Author> authorPage = authorService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > authorPage.getTotalPages()) {
            currentPage = 1;
            authorPage = authorService.findByLimit(0, 10);
        }

        model.addAttribute("authorList", authorPage.getContent());
        model.addAttribute("totalPage", Math.max(authorPage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("currentStaff", currentStaff);
        return "staff/author_st";
    }

    @GetMapping("/staff/promotions")
    public String staffPromotions(Model model, Authentication authentication,
                                  @RequestParam(required = false) String page) {
        String username = authentication.getName();
        Account account = accountService.findByUsername(username);
        if (account == null || account.getAdmin() == null) return "redirect:/";
        Admin currentStaff = account.getAdmin();

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

        Page<Voucher> promotionPage = voucherService.findByLimit(currentPage - 1, 10, null);
        if (invalidPage || currentPage > promotionPage.getTotalPages()) {
            currentPage = 1;
            promotionPage = voucherService.findByLimit(0, 10, null);
        }

        model.addAttribute("promotionList", promotionPage.getContent());
        model.addAttribute("totalPage", Math.max(promotionPage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("currentStaff", currentStaff);
        return "staff/promotion_st";
    }

    @GetMapping("/staff/series")
    public String staffSeries(Model model, Authentication authentication,
                              @RequestParam(required = false) String page) {
        String username = authentication.getName();
        Account account = accountService.findByUsername(username);
        if (account == null || account.getAdmin() == null) return "redirect:/";
        Admin currentStaff = account.getAdmin();

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

        Page<Series> seriesPage = seriesService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > seriesPage.getTotalPages()) {
            currentPage = 1;
            seriesPage = seriesService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Series s : seriesPage.getContent()) {
            bookCountMap.put(s.getId(), seriesService.countBooksBySeriesId(s.getId()));
        }

        model.addAttribute("seriesList", seriesPage.getContent());
        model.addAttribute("totalPage", Math.max(seriesPage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        model.addAttribute("currentStaff", currentStaff);
        return "staff/series_st";
    }

    @GetMapping("/staff/publishers")
    public String staffPublishers(Model model, Authentication authentication,
                                  @RequestParam(required = false) String page) {
        String username = authentication.getName();
        Account account = accountService.findByUsername(username);
        if (account == null || account.getAdmin() == null) return "redirect:/";
        Admin currentStaff = account.getAdmin();

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

        Page<Publisher> publisherPage = publisherService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > publisherPage.getTotalPages()) {
            currentPage = 1;
            publisherPage = publisherService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Publisher p : publisherPage.getContent()) {
            bookCountMap.put(p.getId(), publisherService.countBooksByPublisherId(p.getId()));
        }

        model.addAttribute("publisherList", publisherPage.getContent());
        model.addAttribute("totalPage", Math.max(publisherPage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        model.addAttribute("currentStaff", currentStaff);
        return "staff/publisher_st";
    }

    @GetMapping("/staff/translators")
    public String staffTranslators(Model model, Authentication authentication,
                                   @RequestParam(required = false) String page) {
        String username = authentication.getName();
        Account account = accountService.findByUsername(username);
        if (account == null || account.getAdmin() == null) return "redirect:/";
        Admin currentStaff = account.getAdmin();

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

        Page<Translator> translatorPage = translatorService.findByLimit(currentPage - 1, 10);
        if (invalidPage || currentPage > translatorPage.getTotalPages()) {
            currentPage = 1;
            translatorPage = translatorService.findByLimit(0, 10);
        }

        Map<Integer, Long> bookCountMap = new HashMap<>();
        for (Translator t : translatorPage.getContent()) {
            bookCountMap.put(t.getId(), translatorService.countBooksByTranslatorId(t.getId()));
        }

        model.addAttribute("translatorList", translatorPage.getContent());
        model.addAttribute("totalPage", Math.max(translatorPage.getTotalPages(), 1));
        model.addAttribute("page", currentPage);
        model.addAttribute("bookCountMap", bookCountMap);
        model.addAttribute("currentStaff", currentStaff);
        return "staff/translator_st";
    }
}