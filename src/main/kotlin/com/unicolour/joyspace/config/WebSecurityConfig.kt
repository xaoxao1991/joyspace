package com.unicolour.joyspace.config

import com.unicolour.joyspace.service.ManagerService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableWebSecurity
open class WebSecurityConfig : WebSecurityConfigurerAdapter() {
    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    lateinit var managerService: ManagerService

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        //http.authorizeRequests().anyRequest().permitAll();
        val permitAllPatterns = arrayOf(
                "/favicon.ico",
                "/thirdparty/**",
                "/img/**",
                "/js/**",
                "/css/**",
                "/fonts/**",
                "/api/**",
                "/app/**",
                "/graphql",
                "/wxpay/notify",
                "/wxmp/notify",
                "/company/wxAccountAddConfirm",
                "/printStation/**",
                "/v2/**",
                "/doc/**",
                "/printStation/LLWDtNhzLW.txt",
                "/LLWDtNhzLW.txt",
                "/register",
                "/forget",
                "/company/sendVerifyCode",
                "/company/register",
                "/company/resetPassword")

        http.csrf().disable()
                .authorizeRequests()
                .antMatchers(*permitAllPatterns).permitAll()
                .antMatchers("/company/list").hasRole("SUPERADMIN")
                .antMatchers("/company/edit").hasRole("SUPERADMIN")
                .antMatchers("/printStation/allList").hasRole("SUPERADMIN")
                .antMatchers("/printStation/uploadLogFile").hasRole("SUPERADMIN")
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .loginPage("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/printOrder/list?autoRefresh=true")
                .permitAll()
                .and()
            .logout()
                .permitAll()
            .and()
                .headers()
                .frameOptions()
                .sameOrigin()
    }


    @Autowired
    @Throws(Exception::class)
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        auth.userDetailsService<ManagerService>(managerService).passwordEncoder(passwordEncoder)
    }
}