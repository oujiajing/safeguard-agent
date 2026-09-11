package com.safeguard.agent.user.service;

import com.safeguard.agent.user.controller.request.LoginRequest;
import com.safeguard.agent.user.controller.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginRequest requestParam);

    void logout();
}
