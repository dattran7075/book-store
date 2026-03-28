package com.thunga.web.service;

import com.thunga.web.entity.Account;
import com.thunga.web.entity.Order;
import com.thunga.web.entity.User;
import com.thunga.web.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

@Service
public class UserService {
    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    PasswordEncoder passwordEncoder;

    public User getCurrentUser(HttpServletRequest request) {
        String username = request.getRemoteUser();
        if (username == null) {
            throw new IllegalStateException("User not authenticated");
        }

        Account account = accountService.findByUsername(username);
        if (account == null) {
            throw new IllegalStateException("Account not found");
        }

        User user = userRepository.findByAccount(account);
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("User not found or not persisted");
        }

        return user;
    }

    public User getCurrentUserFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }

        try {
            Account account = accountService.findByUsername(auth.getName());
            if (account != null) {
                return userRepository.findByAccount(account);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Integer id) {
        return userRepository.findById(id).get();
    }

    public User findByName(String name) {
        return userRepository.findByName(name);
    }

    public User findByAccount(Account account) {
        return userRepository.findByAccount(account);
    }

    public User update(User user, HttpServletRequest request) {
        User updateUser = userRepository.findById(user.getId()).get();
        updateUser.setAddress(user.getAddress());
        updateUser.setName(user.getName());
        updateUser.setPhone(user.getPhone());
        updateUser.getAccount().setEmail(request.getParameter("email"));
        updateUser.getAccount().setUpdated_at(new Date());
        return userRepository.save(updateUser);
    }

    public String changePassword(HttpServletRequest request) {
        String username = request.getRemoteUser();
        Account account = accountService.findByUsername(username);
        String oldPassword = request.getParameter("oldPassword");

        if (!passwordEncoder.matches(oldPassword, account.getPassword())) {
            return "Old password is incorrect";
        }

        String newPassword = request.getParameter("newPassword");
        if (newPassword.length() < 6) {
            return "New password must be at least 6 characters";
        } else if (newPassword.length() > 20) {
            return "Password must not exceed 20 characters";
        } else if (!newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            return "Password must contain at least one letter and one number";
        }

        String confirmNewPassword = request.getParameter("confirmNewPassword");
        if (!confirmNewPassword.equals(newPassword)) {
            return "Confirm password does not match";
        }

        account.setPassword(passwordEncoder.encode(newPassword));
        account.setUpdated_at(new Date());
        accountService.save(account);

        return "Success";
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public void delete(User user) {
        User managedUser = userRepository.findById(user.getId()).orElse(null);
        if (managedUser == null) return;

        Account account = managedUser.getAccount();

        if (account != null) {
            account.setUser(null);
        }
        managedUser.setAccount(null);

        userRepository.deleteById(managedUser.getId());

        if (account != null) {
            accountService.delete(account);
        }
    }

    public Page<User> findByLimit(Integer page, Integer limit, String sortBy) {
        Pageable paging = PageRequest.of(page, limit);
        if (sortBy != null) paging = PageRequest.of(page, limit, Sort.by(sortBy));
        Page<User> users = userRepository.findAll(paging);
        return users;
    }

    /**
     * Count active orders for a user (Pending, Approved, In Delivery)
     */
    public int countActiveOrders(User user) {
        if (user == null || user.getOrderList() == null) {
            return 0;
        }

        int count = 0;
        for (Order order : user.getOrderList()) {
            String status = order.getStatus();
            if ("Pending".equals(status) || "Approved".equals(status) ||
                    "In Delivery".equals(status)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if user can be deactivated (no active orders)
     */
    public boolean canDeactivateUser(User user) {
        return countActiveOrders(user) == 0;
    }
}