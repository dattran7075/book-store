package com.thunga.web.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "voucher")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Integer voucherId;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "discount_value")
    private Double discountValue;

    @Column(name = "start_date", nullable = false, length = 10)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, length = 10)
    private LocalDate endDate;

    @Column(name = "max_usage", nullable = false)
    private Integer maxUsage;

    @Column(name = "used_count")
    private Integer usedCount;

    @Column(name = "min_order")
    private Double minOrder;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @OneToMany(mappedBy = "voucher", fetch = FetchType.LAZY)
    private List<Order> orderList;

    @Transient
    public String getDisplayStatus() {
        LocalDate today = LocalDate.now();
        if (today.isBefore(this.startDate)) {
            return "CREATED";
        }
        if (today.isAfter(this.endDate)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

}
