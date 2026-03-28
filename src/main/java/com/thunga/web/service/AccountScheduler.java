package com.thunga.web.scheduler;

import com.thunga.web.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduler for automatic account maintenance tasks
 */
@Component
public class AccountScheduler {

    @Autowired
    private AccountService accountService;

    /**
     * Auto-deactivate inactive accounts daily at 2 AM
     * Cron expression: "0 0 2 * * ?" = second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoDeactivateInactiveAccounts() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + timestamp + "] Running auto-deactivation task...");

        try {
            accountService.autoDeactivateInactiveAccounts();
            System.out.println("[" + timestamp + "] Auto-deactivation task completed successfully");
        } catch (Exception e) {
            System.err.println("[" + timestamp + "] Error during auto-deactivation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cleanup expired OTP data every hour
     * Cron expression: "0 0 * * * ?" = At minute 0 of every hour
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredOTPs() {
        try {
            accountService.cleanupExpiredOTPs();
        } catch (Exception e) {
            System.err.println("Error during OTP cleanup: " + e.getMessage());
        }
    }
}