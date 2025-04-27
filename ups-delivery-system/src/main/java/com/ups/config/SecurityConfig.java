package com.ups.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        MvcRequestMatcher.Builder mvcMatcherBuilder = new MvcRequestMatcher.Builder(introspector);
        
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(mvcMatcherBuilder.pattern("/api/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/register")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/login")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/tracking")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/h2-console/**")).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/**"))
                .ignoringRequestMatchers(new AntPathRequestMatcher("/h2-console/**"))
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())  // For H2 console
            );
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}