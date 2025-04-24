package com.ups.config;

import com.ups.model.entity.User;
import com.ups.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User.UserBuilder;

@Configuration
public class UserDetailsConfig {
    
    private final UserRepository userRepository;
    
    @Autowired
    public UserDetailsConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsService() {
            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                User user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
                
                UserBuilder builder = org.springframework.security.core.userdetails.User.withUsername(username);
                builder.password(user.getPassword());
                builder.roles("USER");
                
                return builder.build();
            }
        };
    }
}