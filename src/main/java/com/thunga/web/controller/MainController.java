package com.thunga.web.controller;

import com.thunga.web.entity.*;
import com.thunga.web.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Controller
public class MainController implements ErrorController {

    @Autowired
    private BookService bookService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserService userService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CartItemService cartItemService;

    @Autowired
    private CommentService commentService;


    @Autowired
    private SeriesService seriesService;

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

    @ModelAttribute
    public void addCategoriesToModel(Model model) {
        Map<Category, List<Category>> categoryHierarchy = categoryService.getCategoryHierarchyForMenu();
        model.addAttribute("categoryHierarchy", categoryHierarchy);

        List<Category> allCategories = categoryService.findAll();
        model.addAttribute("allCategories", allCategories);
    }

    @GetMapping("/")
    public String homePage(Model model, HttpServletRequest request, Authentication authentication) {
        // Books you should know
        Page<Book> bookPage = bookService.getFilteredAndSortedBooks(
                null, null, null, null, null, 0, 6);
        model.addAttribute("bookList", bookPage.toList());

        // Ngay sau: model.addAttribute("bookList", bookPage.toList());
        List<Book> bookListItems = bookPage.toList();
        Map<Integer, Double> bookListRatings = new HashMap<>();
        for (Book book : bookListItems) {
            bookListRatings.put(book.getId(), commentService.getAverageRating(book));
        }
        model.addAttribute("bookListRatings", bookListRatings);

        // Best sold book
        Book bestSoldBook = null;
        try {
            bestSoldBook = bookService.findBestSoldBook();
        } catch (Exception e) {
            e.printStackTrace();
        }
        model.addAttribute("bestSoldBook", bestSoldBook);

        // New Arrivals
        List<Book> newArrivals = new ArrayList<>();
        try {
            Page<Book> newArrivalsPage = bookService.getFilteredAndSortedBooks(
                    null, null, null, null, "newest", 0, 8);
            newArrivals = newArrivalsPage.toList();
        } catch (Exception e) {
            e.printStackTrace();
        }
        model.addAttribute("newArrivals", newArrivals);

        // ✅ Calculate ratings for New Arrivals using Map
        Map<Integer, Double> newArrivalsRatings = new HashMap<>();
        for (Book book : newArrivals) {
            Double avgRating = commentService.getAverageRating(book);
            newArrivalsRatings.put(book.getId(), avgRating);
        }
        model.addAttribute("newArrivalsRatings", newArrivalsRatings);

        // Top Rated Books with Reviews
        List<Comment> topRatings = new ArrayList<>();
        try {
            topRatings = commentService.getTopRatedComments(3);
        } catch (Exception e) {
            e.printStackTrace();
        }
        model.addAttribute("topRatings", topRatings);

        // Recommended for you
        List<Book> recommendedBooks = new ArrayList<>();
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                String username = authentication.getName();
                Account account = accountService.findByUsername(username);
                if (account != null) {
                    User user = userService.findByAccount(account);
                    if (user != null) {
                        recommendedBooks = bookService.getRecommendedBooksForUser(user.getId(), 10);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // If no recommendations, get best-sellers
        if (recommendedBooks.isEmpty()) {
            try {
                Page<Book> bestsellersPage = bookService.getFilteredAndSortedBooks(
                        null, null, null, null, "bestseller", 0, 10);
                recommendedBooks = bestsellersPage.toList();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        model.addAttribute("recommendedBooks", recommendedBooks);

        // ✅ Calculate ratings for Recommended Books using Map
        Map<Integer, Double> recommendedRatings = new HashMap<>();
        for (Book book : recommendedBooks) {
            Double avgRating = commentService.getAverageRating(book);
            recommendedRatings.put(book.getId(), avgRating);
        }
        model.addAttribute("recommendedRatings", recommendedRatings);

        addCartInfoToModel(model);

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            Account account = accountService.findByUsername(username);

            if (account != null) {
                String role = account.getRole();

                if ("ADMIN".equals(role)) {
                    Admin admin = adminService.findByAccount(account);
                    if (admin != null) {
                        model.addAttribute("currentUserName", admin.getName());
                        model.addAttribute("currentAdmin", admin);
                    }
                } else if ("STAFF".equals(role)) {
                    Admin admin = adminService.findByAccount(account);
                    if (admin != null) {
                        model.addAttribute("currentUserName", admin.getName());
                        model.addAttribute("currentStaff", admin);
                    }
                } else {
                    User user = userService.findByAccount(account);
                    if (user != null) {
                        model.addAttribute("currentUserName", user.getName());
                        model.addAttribute("currentUser", user);
                    }
                }
            }
        }

        return "home";
    }

    // ==================== SIGNUP ENDPOINTS ====================

    @GetMapping("/signup")
    public String signup(Model model) {
        return "signup";
    }

    @PostMapping("/signup-otp")
    @ResponseBody
    public Map<String, Object> signupOTP(@RequestBody Map<String, String> requestData) {
        Map<String, Object> response = new HashMap<>();

        try {
            String action = requestData.get("action");

            if ("send".equals(action)) {
                String userName = requestData.get("userName");
                String password = requestData.get("password");
                String confirmPassword = requestData.get("confirmPassword");
                String fullName = requestData.get("fullName");
                String email = requestData.get("email");
                String address = requestData.get("address");

                Map<String, String> errors = accountService.validateAccountRegistration(
                        userName, password, confirmPassword, fullName, email, address
                );

                if (!errors.isEmpty()) {
                    response.put("success", false);
                    response.put("errors", errors);
                    response.put("message", errors.values().iterator().next());
                    return response;
                }

                Map<String, String> formData = new HashMap<>();
                formData.put("userName", userName);
                formData.put("password", password);
                formData.put("fullName", fullName);
                formData.put("email", email);
                formData.put("address", address);

                accountService.storeFormData(email, formData);
                accountService.generateAndSendOTP(email, "signup");

                response.put("success", true);
                response.put("message", "OTP sent to email successfully");
            } else if ("verify".equals(action)) {
                String email = requestData.get("email");
                String otp = requestData.get("otp");

                if (!accountService.verifyOTP(email, otp, "signup")) {
                    response.put("success", false);
                    response.put("message", "Invalid or expired OTP");
                    return response;
                }

                Map<String, String> formData = accountService.getFormData(email);
                if (formData == null) {
                    response.put("success", false);
                    response.put("message", "Form data expired. Please start registration again.");
                    return response;
                }

                String userName = formData.get("userName");
                String password = formData.get("password");
                String fullName = formData.get("fullName");
                String address = formData.get("address");

                Account account = new Account();
                account.setUsername(userName);
                account.setEmail(email);
                account.setPassword(passwordEncoder.encode(password));
                account.setRole("USER");
                account.setCreated_at(new Date());
                account = accountService.save(account);

                User user = new User();
                user.setId(account.getId());
                user.setName(fullName);
                user.setAddress(address);
                user.setAccount(account);
                userService.save(user);

                accountService.clearFormData(email);

                response.put("success", true);
                response.put("message", "Registration successful");
                response.put("redirect", "/login");
            } else if ("resend".equals(action)) {
                String email = requestData.get("email");
                accountService.resendOTP(email, "signup");

                response.put("success", true);
                response.put("message", "OTP resent successfully");
            } else {
                response.put("success", false);
                response.put("message", "Invalid action");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "An error occurred: " + e.getMessage());
        }

        return response;
    }

    // ==================== LOGIN ENDPOINTS ====================

    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        return "login";
    }

    // ==================== FORGOT PASSWORD ENDPOINTS ====================

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        return "forgot_password";
    }

    @PostMapping("/forgot-password-otp")
    @ResponseBody
    public Map<String, Object> forgotPasswordOTP(@RequestBody Map<String, String> requestData) {
        Map<String, Object> response = new HashMap<>();

        try {
            String action = requestData.get("action");

            if ("send".equals(action)) {
                String email = requestData.get("email");

                if (email == null || email.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Email must not be empty");
                    return response;
                }

                email = email.trim().toLowerCase();

                if (!accountService.emailExistsForReset(email)) {
                    response.put("success", false);
                    response.put("message", "Email not found in system");
                    return response;
                }

                accountService.generateAndSendOTP(email, "password_reset");

                response.put("success", true);
                response.put("message", "OTP sent to email successfully");
                response.put("expiryMinutes", 5);
                response.put("email", email);

            } else if ("verify".equals(action)) {
                String email = requestData.get("email");
                String otp = requestData.get("otp");

                if (email == null || email.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Email must not be empty");
                    return response;
                }

                if (otp == null || otp.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "OTP must not be empty");
                    return response;
                }

                email = email.trim().toLowerCase();
                otp = otp.trim();

                if (!otp.matches("^\\d{6}$")) {
                    response.put("success", false);
                    response.put("message", "OTP must be exactly 6 digits");
                    return response;
                }

                if (!accountService.verifyOTP(email, otp, "password_reset")) {
                    response.put("success", false);
                    response.put("message", "Invalid or expired OTP");
                    return response;
                }

                String resetToken = accountService.generateResetToken(email);

                response.put("success", true);
                response.put("message", "OTP verified successfully");
                response.put("resetToken", resetToken);
                response.put("email", email);

            } else if ("reset".equals(action)) {
                String email = requestData.get("email");
                String resetToken = requestData.get("resetToken");
                String newPassword = requestData.get("newPassword");
                String confirmPassword = requestData.get("confirmPassword");

                if (email == null || email.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Email must not be empty");
                    return response;
                }

                if (resetToken == null || resetToken.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Invalid session. Please start the password reset process again");
                    return response;
                }

                email = email.trim().toLowerCase();

                if (!accountService.verifyResetToken(email, resetToken)) {
                    response.put("success", false);
                    response.put("message", "Invalid or expired reset token");
                    return response;
                }

                Map<String, String> passwordErrors = accountService.validatePassword(newPassword, confirmPassword);
                if (!passwordErrors.isEmpty()) {
                    response.put("success", false);
                    response.put("errors", passwordErrors);
                    response.put("message", passwordErrors.values().iterator().next());
                    return response;
                }

                accountService.updatePassword(email, newPassword);
                accountService.clearResetToken(email);

                response.put("success", true);
                response.put("message", "Password reset successfully");
                response.put("redirect", "/login");

            } else if ("resend".equals(action)) {
                String email = requestData.get("email");

                if (email == null || email.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Email must not be empty");
                    return response;
                }

                email = email.trim().toLowerCase();

                if (!accountService.emailExistsForReset(email)) {
                    response.put("success", false);
                    response.put("message", "Email not found in system");
                    return response;
                }

                accountService.resendOTP(email, "password_reset");

                response.put("success", true);
                response.put("message", "OTP resent successfully");
                response.put("expiryMinutes", 5);
                response.put("email", email);

            } else {
                response.put("success", false);
                response.put("message", "Invalid action");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "An error occurred: " + e.getMessage());
        }

        return response;
    }

    // ==================== OTHER EXISTING ENDPOINTS ====================

    @GetMapping("/bookImage")
    public void productImage(HttpServletRequest request, HttpServletResponse response,
                             @RequestParam("id") Integer id) throws IOException {
        Book book = null;
        try {
            book = bookService.findById(id);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (book != null && book.getImage() != null && !book.getImage().isEmpty()) {
            String imageUrl = book.getImage();

            String contentType = "image/jpeg";
            if (imageUrl.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (imageUrl.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (imageUrl.toLowerCase().endsWith(".jpg") || imageUrl.toLowerCase().endsWith(".jpeg")) {
                contentType = "image/jpeg";
            }

            response.setContentType(contentType);
            response.setHeader("Cache-Control", "public, max-age=3600");

            try {
                response.sendRedirect(imageUrl);
            } catch (IOException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @GetMapping("/products")
    public String products(
            Model model,
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) String pricePreset) {

        if (page < 1) page = 1;

        if (sortBy != null) {
            sortBy = sortBy.trim();
            if (sortBy.isEmpty()) sortBy = null;
        }

        if (pricePreset != null && !pricePreset.isEmpty() && priceMin == null && priceMax == null) {
            switch (pricePreset) {
                case "0-150000":
                    priceMin = 0.0;
                    priceMax = 150000.0;
                    break;
                case "150000-300000":
                    priceMin = 150000.0;
                    priceMax = 300000.0;
                    break;
                case "300000-500000":
                    priceMin = 300000.0;
                    priceMax = 500000.0;
                    break;
                case "500000-700000":
                    priceMin = 500000.0;
                    priceMax = 700000.0;
                    break;
                case "700000+":
                    priceMin = 700000.0;
                    priceMax = null;
                    break;
            }
        }

        Page<Book> bookPage = bookService.getFilteredAndSortedBooks(
                null,          // keyword = null (no search)
                categoryId,    // category filter
                priceMin,
                priceMax,
                sortBy,
                page - 1,      // Convert to 0-based index
                limit
        );

        // Handle page out of range
        if (page > bookPage.getTotalPages() && bookPage.getTotalPages() > 0) {
            page = 1;
            bookPage = bookService.getFilteredAndSortedBooks(
                    null,
                    categoryId,
                    priceMin,
                    priceMax,
                    sortBy,
                    0,
                    limit
            );
        }

        model.addAttribute("bookList", bookPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", bookPage.getTotalPages());
        model.addAttribute("totalElements", bookPage.getTotalElements());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("priceMin", priceMin);
        model.addAttribute("priceMax", priceMax);
        model.addAttribute("pricePreset", pricePreset);
        model.addAttribute("categoryId", categoryId);

        addCartInfoToModel(model);

        // Get sidebar categories
        List<Category> sidebarCategories = categoryService.getCategoriesForSidebar(categoryId);
        model.addAttribute("sidebarCategories", sidebarCategories);
        model.addAttribute("selectedCategoryId", categoryId);

        return "products";
    }

    @GetMapping("/search")
    public String searchProduct(
            Model model,
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(value = "page", required = false) String pageParam,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) String pricePreset) {

        if (keyword == null || keyword.trim().isEmpty()) {
            return "redirect:/products";
        }

        keyword = keyword.trim();

        int page = 1;
        int pageSize = 12;
        try {
            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            page = 1;
        }

        if (page < 1) {
            page = 1;
        }

        if (sortBy != null) {
            sortBy = sortBy.trim();
            if (sortBy.isEmpty()) sortBy = null;
        }

        // ✅ Handle price preset
        if (pricePreset != null && !pricePreset.isEmpty() && priceMin == null && priceMax == null) {
            switch (pricePreset) {
                case "0-150000":
                    priceMin = 0.0;
                    priceMax = 150000.0;
                    break;
                case "150000-300000":
                    priceMin = 150000.0;
                    priceMax = 300000.0;
                    break;
                case "300000-500000":
                    priceMin = 300000.0;
                    priceMax = 500000.0;
                    break;
                case "500000-700000":
                    priceMin = 500000.0;
                    priceMax = 700000.0;
                    break;
                case "700000+":
                    priceMin = 700000.0;
                    priceMax = null;
                    break;
            }
        }

        Page<Book> bookPage = bookService.getFilteredAndSortedBooks(
                keyword,       // keyword search
                null,          // categoryId = null (no category filter)
                priceMin,
                priceMax,
                sortBy,
                page - 1,      // Convert to 0-based index
                pageSize
        );

        // Handle page out of range
        if (page > bookPage.getTotalPages() && bookPage.getTotalPages() > 0) {
            page = 1;
            bookPage = bookService.getFilteredAndSortedBooks(
                    keyword,
                    null,
                    priceMin,
                    priceMax,
                    sortBy,
                    0,
                    pageSize
            );
        }

        model.addAttribute("bookList", bookPage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("searchResultCount", bookPage.getTotalElements());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("priceMin", priceMin);
        model.addAttribute("priceMax", priceMax);
        model.addAttribute("pricePreset", pricePreset);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", bookPage.getTotalPages());
        model.addAttribute("totalElements", bookPage.getTotalElements());

        addCartInfoToModel(model);
        return "products";  // Reuse template
    }

    @GetMapping("/detail-product")
    public String detailProduct(@RequestParam Integer id, Model model, HttpServletRequest request) {
        Book book = bookService.findById(id);

        if (book != null) {
            if (book.getPublisher() != null) book.getPublisher().getName();
            if (book.getSeries() != null) book.getSeries().getName();
            if (book.getLanguage() != null) book.getLanguage().getName();
            if (book.getBookTranslatorList() != null && !book.getBookTranslatorList().isEmpty()) {
                for (BookTranslator bt : book.getBookTranslatorList()) bt.getTranslator().getName();
            }
        }

        model.addAttribute("book", book);

        // ── Comment attributes ───────────────────────────────────────────────
        String username = request.getRemoteUser();
        boolean canReview = false;
        boolean hasCommented = false;
        Comment myComment = null;
        boolean isUserRole = false;

        if (username != null) {
            Account account = accountService.findByUsername(username);
            User user = userService.findByAccount(account);
            model.addAttribute("username", username);

            // Only USER role gets tabs — ADMIN sees guest view
            isUserRole = "USER".equals(account.getRole());
            canReview = bookService.hasCompletedOrderWithBook(user, book);
            hasCommented = commentService.hasUserCommented(user, book);

            // My comment (any status) – for My Review tab
            myComment = commentService.getMyCommentForBook(user, book).orElse(null);

            // If already has a comment, cannot write a new one
            if (myComment != null) canReview = false;
        }

        // Approved comments – visible to everyone
        model.addAttribute("approvedComments", commentService.getApprovedCommentsByBook(book));
        model.addAttribute("myComment", myComment);
        model.addAttribute("hasCommented", hasCommented);
        model.addAttribute("canReview", canReview);
        model.addAttribute("isUserRole", isUserRole);

        // Empty Comment object for the write-review form
        model.addAttribute("comment", new Comment());

        addCartInfoToModel(model);

        // ── Sidebar categories ───────────────────────────────────────────────
        List<Category> categoryList = categoryService.findAll();
        model.addAttribute("categoryList", categoryList);

        Integer categoryId = book.getCategory() != null ? book.getCategory().getId() : null;
        List<Category> sidebarCategories = categoryService.getCategoriesForSidebar(categoryId);
        model.addAttribute("sidebarCategories", sidebarCategories);
        model.addAttribute("selectedCategoryId", categoryId);

        // ── Series books (related products) ─────────────────────────────────
        List<Book> seriesBooks = new ArrayList<>();
        if (book.getSeries() != null) {
            seriesBooks = bookService.findBySeriesExcludingCurrent(book.getSeries().getId(), book.getId());
            seriesBooks.sort((b1, b2) -> {
                if (b1.getVolumeNumber() == null && b2.getVolumeNumber() == null) return 0;
                if (b1.getVolumeNumber() == null) return 1;
                if (b2.getVolumeNumber() == null) return -1;
                return b1.getVolumeNumber().compareTo(b2.getVolumeNumber());
            });
        }
        model.addAttribute("seriesBooks", seriesBooks);

        // ── Series bundle section ────────────────────────────────────────────
        if (book.getSeries() != null) {
            Integer seriesId = book.getSeries().getId();
            Series series = book.getSeries();

            List<Book> allBooksInSeries = seriesService.getBooksInSeries(seriesId);
            List<Book> availableBooks = seriesService.getAvailableBooksInSeries(seriesId);
            List<Book> outOfStockBooks = seriesService.getOutOfStockBooksInSeries(seriesId);

            Double originalPrice = seriesService.calculateSeriesOriginalPrice(seriesId);
            Double discountedPrice = seriesService.calculateSeriesDiscountedPrice(seriesId);
            Double discountAmount = seriesService.calculateSeriesDiscountAmount(seriesId);

            boolean canBuyFullSeries = seriesService.canPurchaseFullSeries(seriesId);
            String unavailableReason = seriesService.getUnavailableReason(seriesId);

            model.addAttribute("seriesId", seriesId);
            model.addAttribute("seriesName", series.getName());
            model.addAttribute("seriesStatus", series.getStatus());
            model.addAttribute("seriesTotalVolumes", series.getTotalVolumes());
            model.addAttribute("seriesReleasedVolumes", allBooksInSeries.size());
            model.addAttribute("seriesAvailableBooks", availableBooks);
            model.addAttribute("seriesOutOfStockBooks", outOfStockBooks);
            model.addAttribute("seriesOriginalPrice", originalPrice);
            model.addAttribute("seriesDiscountedPrice", discountedPrice);
            model.addAttribute("seriesDiscountAmount", discountAmount);
            model.addAttribute("canBuyFullSeries", canBuyFullSeries);
            model.addAttribute("unavailableReason", unavailableReason);
        }

        return "productdetail";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return "redirect:/login?error=Not logged in!";
            }

            String username = auth.getName();
            if (username == null || username.isEmpty()) {
                return "redirect:/login?error=Not logged in!";
            }

            Account account = accountService.findByUsername(username);
            if (account == null) {
                return "redirect:/login?error=Account does not exist!";
            }

            String message = (String) request.getSession().getAttribute("message");
            if (message != null) {
                model.addAttribute("message", message);
                request.getSession().removeAttribute("message");
            }

            String roleVal = account.getRole();
            if (roleVal == null) roleVal = "USER";
            if (!roleVal.startsWith("ROLE_")) {
                roleVal = "ROLE_" + roleVal;
            }

            if ("ROLE_ADMIN".equals(roleVal)) {
                Admin admin = adminService.findByAccount(account);
                if (admin != null) {
                    model.addAttribute("admin", admin);
                    return "admin/profile_ad";
                } else {
                    return "redirect:/login?error=Invalid admin role!";
                }
            } else if ("ROLE_STAFF".equals(roleVal)) {
                Admin admin = adminService.findByAccount(account);
                if (admin != null) {
                    model.addAttribute("admin", admin);
                    return "staff/profile_st";
                } else {
                    return "redirect:/login?error=Staff information not found!";
                }
            } else {
                User user = userService.findByAccount(account);
                if (user != null) {
                    model.addAttribute("user", user);
                    addCartInfoToModel(model);
                    return "user/profile_user";
                } else {
                    return "redirect:/login?error=User information not found!";
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return "redirect:/login?error=Internal error while loading profile";
        }
    }

    @PostMapping("/save-user")
    public String saveUser(Model model, @ModelAttribute User user, HttpServletRequest request) {
        User updateUser = userService.update(user, request);
        model.addAttribute("user", updateUser);
        return "redirect:/profile";
    }

    @PostMapping("/save-admin")
    public String saveAdmin(Model model, @ModelAttribute Admin admin, HttpServletRequest request) {
        Admin updateAdmin = adminService.update(admin, request);
        model.addAttribute("admin", updateAdmin);
        return "redirect:/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(Model model, HttpServletRequest request) {
        String message = userService.changePassword(request);
        request.getSession().setAttribute("message", message);
        return "redirect:/profile";
    }

    @PostMapping("/verify-old-password")
    @ResponseBody
    public Map<String, Boolean> verifyOldPassword(@RequestBody Map<String, String> requestData) {
        Map<String, Boolean> response = new HashMap<>();

        try {
            String oldPassword = requestData.get("oldPassword");

            if (oldPassword == null || oldPassword.isEmpty()) {
                response.put("isValid", false);
                return response;
            }

            String username = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                username = auth.getName();
            }

            if (username == null) {
                response.put("isValid", false);
                return response;
            }

            Account account = accountService.findByUsername(username);
            if (account == null) {
                response.put("isValid", false);
                return response;
            }

            boolean isValid = passwordEncoder.matches(oldPassword, account.getPassword());
            response.put("isValid", isValid);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("isValid", false);
        }

        return response;
    }

    @GetMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "error/error404";
            }

            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return "error/error403";
            }

            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                return "error/error500";
            }
        }

        return "error/error404";
    }
}