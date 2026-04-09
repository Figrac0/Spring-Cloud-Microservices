package com.unik.company_service.config;

import com.unik.company_service.security.GatewayHeaderAuthenticationFilter;
import com.unik.company_service.security.JwtProperties;
import com.unik.company_service.security.SecurityWhitelistProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ JwtProperties.class, SecurityWhitelistProperties.class })
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter,
            SecurityWhitelistProperties whitelistProperties) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpStatus.FORBIDDEN.value(), accessDeniedException.getMessage())))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(whitelistProperties.getWhitelist().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayHeaderAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }
}
