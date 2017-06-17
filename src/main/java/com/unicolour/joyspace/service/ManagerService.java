package com.unicolour.joyspace.service;

import com.unicolour.joyspace.dto.LoginManagerDetail;
import com.unicolour.joyspace.model.Company;
import com.unicolour.joyspace.model.Manager;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface ManagerService extends UserDetailsService {
	Manager createManager(String userName, String password, String fullName, String cellPhone, String email, Company company);
	boolean resetPassword(int userId, String password);
	Manager login(String userName, String password);
	LoginManagerDetail getLoginManager();
}
