package com.thunga.web.config;

import com.thunga.web.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    AccountService accountService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(accountService)        // Cung cấp userservice cho spring security
                .passwordEncoder(passwordEncoder());     // cung cấp password encoder
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable();

        // ============================================================
        // PUBLIC ENDPOINTS (everyone can access)
        // ============================================================
        http.authorizeRequests()
                .antMatchers("/", "/signup", "/login", "/products", "/search",
                        "/detail-product", "/bookImage", "/error").permitAll();

        // ============================================================
        // USER ENDPOINTS (only users)
        // ============================================================
        http.authorizeRequests()
                .antMatchers("/add-to-cart", "/purchase", "/confirm", "/remove-book")
                .access("hasRole('USER')");

        // ============================================================
        // USER/ADMIN/STAFF ENDPOINTS (all authenticated users)
        // ============================================================
        http.authorizeRequests()
                .antMatchers("/cart", "/orderList", "/profile", "/view-cart", "/orders",
                        "/save-user", "/save-admin", "/change-password", "/profile-info")
                .access("hasAnyRole('USER', 'ADMIN', 'STAFF')");

        // ============================================================
        // ADMIN ENDPOINTS (/admin/*)
        // ============================================================
        http.authorizeRequests()
                .antMatchers("/admin/**")
                .access("hasRole('ADMIN')");


        // ============================================================
        // STAFF ENDPOINTS (/staff/*)
        // ============================================================
        http.authorizeRequests()
                .antMatchers("/staff/**")
                .access("hasRole('STAFF')");

        // ============================================================
        // LOGIN/LOGOUT CONFIGURATION
        // ============================================================
        http.authorizeRequests()
                .and()
                .formLogin()
                .loginProcessingUrl("/j_spring_security_check")
                .loginPage("/login")
                .defaultSuccessUrl("/")
                .failureUrl("/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .and()
                .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/");
    }
}