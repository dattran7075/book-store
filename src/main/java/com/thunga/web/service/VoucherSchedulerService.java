package com.thunga.web.service;

import com.thunga.web.entity.Voucher;
import com.thunga.web.repository.VoucherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class VoucherSchedulerService {

    @Autowired
    private VoucherRepository voucherRepository;

    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void updatePromotionStatus() {
        LocalDate currentDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        System.out.println("========================================");
        System.out.println("SCHEDULER RUNNING AT: " + now.format(formatter));
        System.out.println("========================================");

        // Tìm các promotion cần ACTIVE
        List<Voucher> toActivate = voucherRepository
                .findByStatusAndStartDateLessThanEqual("CREATED", currentDate);

        System.out.println("Found " + toActivate.size() + " promotions to ACTIVATE");
        for (Voucher promo : toActivate) {
            System.out.println("   → Activating: " + promo.getCode() + " (ID: " + promo.getVoucherId() + ")");
            promo.setStatus("ACTIVE");
            voucherRepository.save(promo);
        }

        // Tìm các promotion cần EXPIRED
        List<Voucher> toExpire = voucherRepository
                .findByStatusAndEndDateLessThan("ACTIVE", currentDate);

        System.out.println("Found " + toExpire.size() + " promotions to EXPIRE");
        for (Voucher promo : toExpire) {
            System.out.println("   → Expiring: " + promo.getCode() + " (ID: " + promo.getVoucherId() + ")");
            promo.setStatus("EXPIRED");
            voucherRepository.save(promo);
        }

        System.out.println("Scheduler completed: " + toActivate.size() + " activated, " + toExpire.size() + " expired");
        System.out.println("========================================\n");
    }
}