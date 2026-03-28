package com.thunga.web.service;

import com.thunga.web.entity.Account;
import com.thunga.web.entity.Admin;
import com.thunga.web.repository.AccountRepository;
import com.thunga.web.repository.AdminRepository;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    public static final int MAX_ACTIVE_ORDERS = 5;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AccountService accountService;

    private EmailValidator emailValidator = EmailValidator.getInstance();

    private static final List<String> ACTIVE_STATUSES =
            Arrays.asList("Pending", "Assigned", "Approved", "In Delivery");

    // =====================================================
    // CRUD METHODS
    // =====================================================

    public Admin findById(Integer id) {
        return adminRepository.findById(id).orElse(null);
    }

    public Admin findByAccount(Account account) {
        return adminRepository.findByAccount(account);
    }

    public List<Admin> findAll() {
        return adminRepository.findAll();
    }

    public Page<Admin> findByLimit(int page, int size, String sortBy) {
        Pageable pageable;
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortBy));
        } else {
            pageable = PageRequest.of(page, size);
        }
        return adminRepository.findAllStaff(pageable);
    }

    public Admin save(Admin admin) {
        return adminRepository.save(admin);
    }

    @Transactional
    public void delete(Admin admin) {
        if (admin == null) {
            throw new IllegalArgumentException("Staff not found");
        }
        Account account = admin.getAccount();
        adminRepository.delete(admin);

        if (account != null) {
            accountRepository.delete(account);
        }
    }

    // =====================================================
    // ADMIN-SPECIFIC METHODS
    // =====================================================

    public Admin update(Admin admin, HttpServletRequest request) {
        Admin updateAdmin = adminRepository.findById(admin.getId()).orElse(null);
        if (updateAdmin == null) {
            throw new IllegalArgumentException("Admin not found");
        }
        updateAdmin.setAddress(admin.getAddress());
        updateAdmin.setName(admin.getName());
        updateAdmin.setPhone(admin.getPhone());
        updateAdmin.getAccount().setEmail(request.getParameter("email"));
        updateAdmin.getAccount().setUpdated_at(new Date());
        return adminRepository.save(updateAdmin);
    }

    public String changePassword(HttpServletRequest request) {
        String username = request.getRemoteUser();
        Account account = accountService.findByUsername(username);
        String oldPassword = request.getParameter("oldPassword");

        if (!passwordEncoder.matches(oldPassword, account.getPassword())) {
            return "Old password is incorrect";
        }

        String newPassword = request.getParameter("newPassword");
        if (newPassword == null || newPassword.isEmpty()) {
            return "New password cannot be empty";
        }

        if (newPassword.length() < 6) {
            return "New password must be at least 6 characters";
        } else if (newPassword.length() > 20) {
            return "Password must not exceed 20 characters";
        } else if (!newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            return "Password must contain at least one letter and one number";
        }

        String confirmNewPassword = request.getParameter("confirmNewPassword");
        if (confirmNewPassword == null || confirmNewPassword.isEmpty()) {
            return "Confirm password cannot be empty";
        }

        if (!confirmNewPassword.equals(newPassword)) {
            return "Confirm password does not match";
        }

        if (oldPassword.equals(newPassword)) {
            return "New password must be different from old password";
        }

        account.setPassword(passwordEncoder.encode(newPassword));
        account.setUpdated_at(new Date());
        accountService.save(account);

        return "Success";
    }

    // =====================================================
    // STAFF-SPECIFIC METHODS (For staff admins)
    // =====================================================

    public int countActiveOrders(Admin admin) {
        if (admin == null || admin.getOrderList() == null) {
            return 0;
        }
        return (int) admin.getOrderList().stream()
                .filter(order -> order.getStatus() != null
                        && ACTIVE_STATUSES.contains(order.getStatus()))
                .count();
    }

    public List<Admin> findEligibleStaff() {
        return adminRepository.findAll().stream()
                .filter(admin ->
                        "STAFF".equals(admin.getAccount().getRole()) &&
                                countActiveOrders(admin) < MAX_ACTIVE_ORDERS
                )
                .collect(Collectors.toList());
    }

    public Map<String, String> validateStaff(Integer adminId, String username, String password,
                                             String confirmPassword, String name, String email,
                                             String phone, String address) {
        Map<String, String> errors = new HashMap<>();
        boolean isUpdate = (adminId != null);

        if (!isUpdate) {
            if (username == null || username.trim().isEmpty()) {
                errors.put("username", "Username must not be empty");
            } else if (username.length() < 5 || username.length() > 20) {
                errors.put("username", "Username must be between 3 and 20 characters");
            } else if (!username.matches("^(?=.*[A-Za-z])[A-Za-z0-9]+$")) {
                errors.put("username", "Username can only contain letters, numbers");
            } else {
                Account existingAccount = accountRepository.findByUsername(username);
                if (existingAccount != null) {
                    errors.put("username", "Username is already in use");
                }
            }
            if (password == null || password.trim().isEmpty()) {
                errors.put("password", "Password must not be empty");
            } else if (password.length() < 6) {
                errors.put("password", "Password must be at least 6 characters long");
            } else if (password.length() > 20) {
                errors.put("password", "Password must not exceed 20 characters");
            } else if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
                errors.put("password", "Password must contain at least one letter and one number");
            }
            if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
                errors.put("confirmPassword", "Confirm password must not be empty");
            } else if (!confirmPassword.equals(password)) {
                errors.put("confirmPassword", "The verification password does not match");
            }
        }

        if (name == null || name.trim().isEmpty()) {
            errors.put("name", "Full name must not be empty");
        } else if (name.length() < 2 || name.length() > 50) {
            errors.put("name", "Full name must be between 2 and 50 characters");
        } else if (!name.matches("^[A-Z][a-z]+(\\s[A-Z][a-z]+)*(\\s[A-Z])?$")) {
            errors.put("name", "Full name must be capitalized each word (ex: Nguyen Van A)");
        }

        if (email == null || email.trim().isEmpty()) {
            errors.put("email", "Email must not be empty");
        } else if (!emailValidator.isValid(email)) {
            errors.put("email", "Invalid email address");
        } else {
            Account existingAccount = accountRepository.findByEmail(email.trim());
            if (existingAccount != null) {
                if (isUpdate) {
                    Admin currentAdmin = findById(adminId);
                    if (currentAdmin != null && currentAdmin.getAccount() != null) {
                        if (!existingAccount.getId().equals(currentAdmin.getAccount().getId())) {
                            errors.put("email", "Email is already in use");
                        }
                    }
                } else {
                    errors.put("email", "Email is already in use");
                }
            }
        }

        if (phone == null || phone.trim().isEmpty()) {
            errors.put("phone", "Phone number must not be empty");
        } else if (!phone.matches("^0[1-9]\\d{8}$")) {
            errors.put("phone", "Phone number must be 10 digits starting with 0 (ex: 0912345678)");
        }

        if (address == null || address.trim().isEmpty()) {
            errors.put("address", "Address must not be empty");
        } else if (address.length() < 6 || address.length() > 200) {
            errors.put("address", "Address must be between 6 and 200 characters");
        } else if (!address.matches("^(?!.*[.,#/^()'\\-]{2,})(?!-)[A-Za-z0-9\\s.,#/^()'\\-]+$")) {
            errors.put("address", "Invalid address format (ex: 123 Main St, Chicago)");
        }

        return errors;
    }

    @Transactional
    public Admin createNewStaff(String username, String password, String name,
                                String email, String phone, String address) {
        Account account = new Account();
        account.setUsername(username.trim());
        account.setPassword(passwordEncoder.encode(password));
        account.setEmail(email.trim());
        account.setRole("STAFF");
        account.setCreated_at(new Date());
        account.setUpdated_at(new Date());
        account = accountRepository.save(account);

        Admin admin = new Admin();
        admin.setAccount(account);
        admin.setName(name.trim());
        admin.setPhone(phone.trim());
        admin.setAddress(address.trim());
        admin = adminRepository.save(admin);

        emailService.sendStaffRegistrationEmail(email.trim(), name.trim(), username.trim(), password);
        return admin;
    }

    @Transactional
    public Admin updateStaff(Integer adminId, String name, String phone, String address, String email) {
        Admin admin = findById(adminId);
        if (admin == null) {
            throw new IllegalArgumentException("Staff admin not found");
        }
        Account account = admin.getAccount();
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }

        String oldName = admin.getName();
        String oldPhone = admin.getPhone();
        String oldAddress = admin.getAddress();
        String oldEmail = account.getEmail();

        List<String> changedFields = new ArrayList<>();
        boolean emailChanged = false;

        if (!name.equals(oldName)) {
            admin.setName(name.trim());
            changedFields.add("name");
        }
        if (!phone.equals(oldPhone)) {
            admin.setPhone(phone.trim());
            changedFields.add("phone");
        }
        if (!address.equals(oldAddress)) {
            admin.setAddress(address.trim());
            changedFields.add("address");
        }
        if (!email.trim().equalsIgnoreCase(oldEmail)) {
            account.setEmail(email.trim());
            changedFields.add("email");
            emailChanged = true;
        }

        if (changedFields.isEmpty()) {
            return admin;
        }

        account.setUpdated_at(new Date());
        accountRepository.save(account);
        admin = adminRepository.save(admin);

        if (emailChanged) {
            emailService.sendStaffUpdateNotification(email.trim(), admin.getName(), changedFields, oldEmail, email.trim());
            emailService.sendEmailChangedAlert(oldEmail, admin.getName(), email.trim());
        }

        return admin;
    }
}