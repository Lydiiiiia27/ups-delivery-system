package com.ups.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Disable CSRF for API endpoints
            .authorizeHttpRequests(authz -> authz
                // Amazon API endpoints - explicitly permit all without authentication
                .requestMatchers(new AntPathRequestMatcher("/api/createshipment")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/changedestination")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/queryshipmentstatus")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/updateshipmentstatus")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/notifytruckarrived")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/notifydeliverycomplete")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/notify/**")).permitAll()
                // Public endpoints
                .requestMatchers(new AntPathRequestMatcher("/login")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/register")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/css/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/js/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/tracking/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/h2-console/**")).permitAll()
                // Everything else needs authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .headers(headers -> headers.frameOptions().disable()); // For H2 console
        
        return http.build();
    }
}