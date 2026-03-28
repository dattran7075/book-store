package com.thunga.web.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "[order]")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<OrderDetail> orderDetailList = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "admin_id", referencedColumnName = "id_admin")
    private Admin admin;

    @Column(name = "total_cost")
    private Double total_cost;

    @Column(name = "customer_name")
    private String customer_name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "discount_amount", precision = 10, scale = 3)
    private Double discount_amount = 0.0;


    @Column(name = "shipping_fee", precision = 10, scale = 3)
    private Double shipping_fee;

    @Column(name = "status")
    private String status;

    @Column(name = "shipping_method")
    private String shipping_method = "STANDARD";

    @Column(name = "payment_method")
    private String payment_method = "COD";

    @Column(name = "payment_status")
    private String payment_status = "UNPAID";

    @Column(name = "payment_note")
    private String payment_note;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @Column(name = "updated_order")
    private LocalDate updated_at;

    @ManyToOne
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    public void addOrderDetail(OrderDetail orderDetail) {
        orderDetailList.add(orderDetail);
        orderDetail.setOrder(this);
    }

    /**
     * Calculate final total including shipping fee
     */
    public Double getFinalTotal() {
        double total = (total_cost != null ? total_cost : 0);
        double discount = (discount_amount != null ? discount_amount : 0);
        double shipping = (shipping_fee != null ? shipping_fee : 0);
        return total - discount + shipping;
    }

    public Double calculateTotalWeight() {
        double totalWeight = 0.0;
        if (orderDetailList != null) {
            for (OrderDetail detail : orderDetailList) {
                if (detail.getBook() != null && detail.getBook().getWeight_kg() != null) {
                    totalWeight += detail.getBook().getWeight_kg() * detail.getNumber();
                }
            }
        }
        return totalWeight;
    }

}