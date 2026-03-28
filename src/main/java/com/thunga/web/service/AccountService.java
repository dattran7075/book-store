package com.thunga.web.service;

import com.thunga.web.entity.Account;
import com.thunga.web.repository.AccountRepository;
import org.apache.commons.validator.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService implements UserDetailsService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EmailService emailService;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private EmailValidator emailValidator = EmailValidator.getInstance();

    // Auto-deactivation threshold: 30 days of inactivity
    private static final int INACTIVE_DAYS_THRESHOLD = 30;

    // ==================== GENERIC OTP STORAGE ====================
    // Map: email -> OTP code
    private Map<String, String> otpStorage = new ConcurrentHashMap<>();

    // Map: email -> Expiry time
    private Map<String, LocalDateTime> otpExpiryStorage = new ConcurrentHashMap<>();

    // Map: email -> Purpose (signup, password_reset)
    private Map<String, String> otpPurposeStorage = new ConcurrentHashMap<>();

    // Map: email -> Temporary form data
    private Map<String, Map<String, String>> formDataStorage = new ConcurrentHashMap<>();

    // Map: email -> Reset token (cho password reset)
    private Map<String, String> resetTokenStorage = new ConcurrentHashMap<>();

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final String PURPOSE_SIGNUP = "signup";
    private static final String PURPOSE_PASSWORD_RESET = "password_reset";
    private static final String RESET_TOKEN_PREFIX = "RESET_";

    // ==================== CRUD METHODS ====================

    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    public Account findByUsername(String username) {
        return accountRepository.findByUsername(username);
    }

    public Account findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    public Account save(Account account) {
        return accountRepository.save(account);
    }

    public void delete(Account account) {
        accountRepository.delete(account);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("Account not found: " + username);
        }

        // Check if account is INACTIVE
        if ("INACTIVE".equals(account.getStatus())) {
            throw new UsernameNotFoundException("Your account has been deactivated.");
        }

        // Update last_login timestamp
        account.setLast_login(new Date());
        accountRepository.save(account);

        return org.springframework.security.core.userdetails.User
                .withUsername(account.getUsername())
                .password(account.getPassword())
                .authorities("ROLE_" + account.getRole())
                .build();
    }

    // ==================== ACCOUNT STATUS MANAGEMENT ====================

    /**
     * Check and auto-deactivate accounts inactive for more than 30 days
     * Should be called periodically (e.g., daily scheduled task)
     */
    public void autoDeactivateInactiveAccounts() {
        List<Account> allAccounts = accountRepository.findAll();
        Date now = new Date();
        int deactivatedCount = 0;

        for (Account account : allAccounts) {
            // Only check ACTIVE accounts with USER role
            if ("ACTIVE".equals(account.getStatus()) && "USER".equals(account.getRole())) {
                Date lastLogin = account.getLast_login();
                if (lastLogin == null) {
                    lastLogin = account.getCreated_at();
                }

                if (lastLogin != null) {
                    long daysSinceLastLogin = getDaysBetween(lastLogin, now);
                    if (daysSinceLastLogin >= INACTIVE_DAYS_THRESHOLD) {
                        account.setStatus("INACTIVE");
                        account.setUpdated_at(now);
                        accountRepository.save(account);
                        deactivatedCount++;
                    }
                }
            }
        }

        if (deactivatedCount > 0) {
            System.out.println("Auto-deactivated " + deactivatedCount + " inactive accounts");
        }
    }

    /**
     * Calculate days between two dates
     */
    private long getDaysBetween(Date start, Date end) {
        long diffInMillis = end.getTime() - start.getTime();
        return diffInMillis / (1000 * 60 * 60 * 24);
    }

    /**
     * Manually activate an account
     */
    public void activateAccount(Integer accountId) {
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account != null) {
            account.setStatus("ACTIVE");
            account.setUpdated_at(new Date());
            accountRepository.save(account);
        }
    }

    /**
     * Manually deactivate an account
     */
    public void deactivateAccount(Integer accountId) {
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account != null) {
            account.setStatus("INACTIVE");
            account.setUpdated_at(new Date());
            accountRepository.save(account);
        }
    }

    // ==================== GENERIC OTP METHODS ====================

    /**
     * Generate and send OTP - Reusable for any purpose (signup, password reset, etc.)
     *
     * @param email   Email address
     * @param purpose Purpose of OTP: "signup" or "password_reset"
     * @return OTP code (6 digits)
     */
    public String generateAndSendOTP(String email, String purpose) {
        Random random = new Random();
        String otp = String.format("%06d", random.nextInt(1000000));

        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);
        otpStorage.put(email, otp);
        otpExpiryStorage.put(email, expiryTime);
        otpPurposeStorage.put(email, purpose);

        emailService.sendOTPEmail(email, otp);

        return otp;
    }

    /**
     * Verify OTP - Reusable for any purpose
     *
     * @param email    Email address
     * @param inputOtp OTP entered by user
     * @param purpose  Expected purpose of OTP
     * @return true if OTP is valid, false otherwise
     */
    public boolean verifyOTP(String email, String inputOtp, String purpose) {
        String storedOtp = otpStorage.get(email);
        LocalDateTime expiryTime = otpExpiryStorage.get(email);
        String storedPurpose = otpPurposeStorage.get(email);

        if (storedOtp == null || expiryTime == null || storedPurpose == null) {
            return false;
        }

        // Check if purpose matches
        if (!storedPurpose.equals(purpose)) {
            return false;
        }

        // Check if OTP is expired
        if (LocalDateTime.now().isAfter(expiryTime)) {
            clearOTP(email);
            return false;
        }

        // Check if OTP matches
        if (storedOtp.equals(inputOtp)) {
            // Remove OTP after successful verification
            otpStorage.remove(email);
            otpExpiryStorage.remove(email);
            otpPurposeStorage.remove(email);
            return true;
        }

        return false;
    }

    /**
     * Resend OTP - Reusable for any purpose
     *
     * @param email   Email address
     * @param purpose Purpose of OTP
     */
    public void resendOTP(String email, String purpose) {
        // Clear existing OTP
        clearOTP(email);
        // Generate new OTP
        generateAndSendOTP(email, purpose);
    }

    /**
     * Clear OTP data for an email
     *
     * @param email Email address
     */
    private void clearOTP(String email) {
        otpStorage.remove(email);
        otpExpiryStorage.remove(email);
        otpPurposeStorage.remove(email);
    }

    /**
     * Check if OTP exists and is not expired
     *
     * @param email Email address
     * @return true if OTP exists and not expired
     */
    public boolean hasValidOTP(String email) {
        String otp = otpStorage.get(email);
        LocalDateTime expiryTime = otpExpiryStorage.get(email);

        if (otp == null || expiryTime == null) {
            return false;
        }

        return !LocalDateTime.now().isAfter(expiryTime);
    }

    /**
     * Get OTP expiry time remaining in seconds
     *
     * @param email Email address
     * @return Remaining seconds, or 0 if expired/not found
     */
    public long getOTPExpirySeconds(String email) {
        LocalDateTime expiryTime = otpExpiryStorage.get(email);
        if (expiryTime == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiryTime)) {
            return 0;
        }

        return java.time.temporal.ChronoUnit.SECONDS.between(now, expiryTime);
    }

    // ==================== FORM DATA STORAGE ====================

    /**
     * Store form data temporarily (for signup process)
     *
     * @param email    Email address
     * @param formData Form data map
     */
    public void storeFormData(String email, Map<String, String> formData) {
        formDataStorage.put(email, new HashMap<>(formData));
    }

    /**
     * Retrieve stored form data
     *
     * @param email Email address
     * @return Form data map or null
     */
    public Map<String, String> getFormData(String email) {
        return formDataStorage.get(email);
    }

    /**
     * Clear form data and all related OTP data
     *
     * @param email Email address
     */
    public void clearFormData(String email) {
        formDataStorage.remove(email);
        clearOTP(email);
        resetTokenStorage.remove(email);
    }

    // ==================== PASSWORD RESET TOKEN ====================

    /**
     * Generate reset token after OTP verification
     *
     * @param email Email address
     * @return Reset token
     */
    public String generateResetToken(String email) {
        String resetToken = RESET_TOKEN_PREFIX + System.currentTimeMillis() + "_" + email;
        resetTokenStorage.put(email, resetToken);
        return resetToken;
    }

    /**
     * Verify and consume reset token
     *
     * @param email Email address
     * @param token Reset token
     * @return true if token is valid
     */
    public boolean verifyResetToken(String email, String token) {
        String storedToken = resetTokenStorage.get(email);
        return storedToken != null && storedToken.equals(token);
    }

    /**
     * Clear reset token
     *
     * @param email Email address
     */
    public void clearResetToken(String email) {
        resetTokenStorage.remove(email);
    }

    // ==================== VALIDATION METHODS ====================

    /**
     * Validate account registration data
     *
     * @return Map of field errors (field name -> error message)
     */
    public Map<String, String> validateAccountRegistration(String userName, String password,
                                                           String confirmPassword, String fullName,
                                                           String email, String address) {
        Map<String, String> errors = new HashMap<>();

        // Validate userName
        if (userName == null || userName.trim().isEmpty()) {
            errors.put("userName", "Username must not be empty");
        } else if (userName.length() < 3 || userName.length() > 20) {
            errors.put("userName", "Username must be between 3 and 20 characters");
        } else if (!userName.matches("^(?=.*[A-Za-z])[A-Za-z0-9]+$")) {
            errors.put("userName", "Username can only contain letters, numbers");
        } else {
            Account existingAccount = accountRepository.findByUsername(userName);
            if (existingAccount != null) {
                errors.put("userName", "Username is already in use");
            }
        }

        // Validate password
        if (password == null || password.trim().isEmpty()) {
            errors.put("password", "Password must not be empty");
        } else if (password.length() < 6) {
            errors.put("password", "Password must be at least 6 characters long");
        } else if (password.length() > 20) {
            errors.put("password", "Password must not exceed 20 characters");
        } else if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            errors.put("password", "Password must contain at least one letter and one number");
        }

        // Validate confirmPassword
        if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
            errors.put("confirmPassword", "Confirm password must not be empty");
        } else if (!confirmPassword.equals(password)) {
            errors.put("confirmPassword", "The verification password does not match");
        }

        // Validate fullName
        if (fullName == null || fullName.trim().isEmpty()) {
            errors.put("fullName", "Full name must not be empty");
        } else if (fullName.length() < 2 || fullName.length() > 50) {
            errors.put("fullName", "Full name must be between 2 and 50 characters");
        } else if (!fullName.matches("^([A-Z][a-z]+)(\\s[A-Z][a-z]+)*$")) {
            errors.put("fullName", "Full name must be capitalized each word (ex: Nguyen Van A)");
        }

        // Validate email
        if (email == null || email.trim().isEmpty()) {
            errors.put("email", "Email must not be empty");
        } else if (!emailValidator.isValid(email)) {
            errors.put("email", "Invalid email address");
        } else {
            Account existingAccount = accountRepository.findByEmail(email);
            if (existingAccount != null) {
                errors.put("email", "Email is already in use");
            }
        }

        // Validate address
        if (address == null || address.trim().isEmpty()) {
            errors.put("address", "Address must not be empty");
        } else if (address.length() < 2 || address.length() > 50) {
            errors.put("address", "Address must be between 2 and 50 characters");
        } else if (!address.matches("^(?!.*[.,#/^()'\\-]{2,})(?!-)[A-Za-z0-9\\s.,#/^()'\\-]{2,50}$")) {
            errors.put("address", "Invalid address format (ex: 123 Main St, Chicago)");
        }

        return errors;
    }

    /**
     * Validate password (for password reset)
     */
    public Map<String, String> validatePassword(String newPassword, String confirmPassword) {
        Map<String, String> errors = new HashMap<>();

        // Validate newPassword
        if (newPassword == null || newPassword.trim().isEmpty()) {
            errors.put("newPassword", "Password must not be empty");
        } else if (newPassword.length() < 6) {
            errors.put("newPassword", "Password must be at least 6 characters long");
        } else if (newPassword.length() > 20) {
            errors.put("newPassword", "Password must not exceed 20 characters");
        } else if (!newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            errors.put("newPassword", "Password must contain at least one letter and one number");
        }

        // Validate confirmPassword
        if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
            errors.put("confirmPassword", "Confirm password must not be empty");
        } else if (newPassword != null && !confirmPassword.equals(newPassword)) {
            errors.put("confirmPassword", "The verification password does not match");
        }

        return errors;
    }

    /**
     * Check if username already exists
     */
    public boolean isUsernameExists(String username) {
        return accountRepository.findByUsername(username) != null;
    }

    /**
     * Check if email already exists
     */
    public boolean isEmailExists(String email) {
        return accountRepository.findByEmail(email) != null;
    }

    /**
     * Verify email exists for password reset
     */
    public boolean emailExistsForReset(String email) {
        return accountRepository.findByEmail(email) != null;
    }

    /**
     * Update password for an account
     */
    public void updatePassword(String email, String newPassword) {
        Account account = accountRepository.findByEmail(email);
        if (account != null) {
            account.setPassword(passwordEncoder.encode(newPassword));
            account.setUpdated_at(new Date());
            accountRepository.save(account);
        }
    }

    /**
     * Cleanup expired OTPs (should be called periodically)
     */
    public void cleanupExpiredOTPs() {
        LocalDateTime now = LocalDateTime.now();

        otpExpiryStorage.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue())) {
                String email = entry.getKey();
                clearOTP(email);
                resetTokenStorage.remove(email);
                return true;
            }
            return false;
        });
    }
}