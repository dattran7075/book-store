package com.thunga.web.entity;

import lombok.*;

import javax.persistence.*;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "account")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username")
    private String username;

    @Column(name = "password_hash")
    private String password;

    @Column(name = "email")
    private String email;

    @Column(name = "role")
    private String role;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "last_login")
    private Date last_login;

    @Column(name = "created_at")
    private Date created_at;

    @Column(name = "updated_at")
    private Date updated_at;

    @OneToOne(mappedBy = "account")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private User user;

    @OneToOne(mappedBy = "account")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Admin admin;

}