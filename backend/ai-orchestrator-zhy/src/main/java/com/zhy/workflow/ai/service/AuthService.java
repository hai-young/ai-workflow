package com.zhy.workflow.ai.service;

import com.zhy.workflow.ai.dto.LoginResponse;
import com.zhy.workflow.ai.dto.RegisterRequest;
import com.zhy.workflow.ai.entity.User;
import com.zhy.workflow.ai.entity.VerifyCode;
import com.zhy.workflow.ai.repository.UserRepository;
import com.zhy.workflow.ai.repository.VerifyCodeRepository;
import com.zhy.workflow.ai.security.JwtUtil;
import com.zhy.workflow.ai.service.VerifyCodeService;
import jakarta.validation.constraints.Email;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for user authentication.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final VerifyCodeService verifyCodeService;
    private final VerifyCodeRepository verifyCodeRepository;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder, VerifyCodeService verifyCodeService, VerifyCodeRepository verifyCodeRepository) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.verifyCodeService = verifyCodeService;
        this.verifyCodeRepository = verifyCodeRepository;
    }

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param username the username
     * @param password the password
     * @return LoginResponse containing the JWT token and user info
     * @throws IllegalArgumentException if credentials are invalid
     */
    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (user.isLocked()) {
            throw new IllegalArgumentException("Account is locked due to too many failed login attempts");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.setLoginFailCount(user.getLoginFailCount() + 1);
            userRepository.save(user);
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("User account is disabled");
        }

        user.setLastLoginTime(LocalDateTime.now());
        user.setLoginFailCount(0);
        userRepository.save(user);

        String token = jwtUtil.generateToken(username, user.getRole());
        long expiresIn = jwtUtil.extractExpiration(token).getTime() - System.currentTimeMillis();

        return new LoginResponse(token, username, user.getRole(), expiresIn);
    }

    /**
     * Registers a new user account.
     *
     * @param registerRequest the registration request containing user details and verification code
     * @throws IllegalArgumentException if username already exists or validation fails
     */
    public void register(RegisterRequest registerRequest) {
        String username = registerRequest.getUsername();
        String password = registerRequest.getPassword();
        String confirmPassword = registerRequest.getConfirmPassword();
        String email = registerRequest.getEmail();
        String phone = registerRequest.getPhone();
        String code = registerRequest.getCode();

        // Verify the verification code
        Optional<VerifyCode> codeOpt = verifyCodeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone);
        if (codeOpt.isEmpty()) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        VerifyCode verifyCode = codeOpt.get();
        if (!verifyCode.getCode().equals(code)) {
            throw new IllegalArgumentException("验证码错误");
        }

        if (!verifyCode.getType().equals("register")) {
            throw new IllegalArgumentException("无效的验证码类型");
        }

        if (verifyCode.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("验证码已过期");
        }

        // Delete the used code
        verifyCodeRepository.delete(verifyCode);

        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // Validate email format if provided
        if (email != null && !email.isEmpty()) {
            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                throw new IllegalArgumentException("无效的邮箱格式");
            }
        }

        // Validate phone number format if provided
        if (phone != null && !phone.isEmpty()) {
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                throw new IllegalArgumentException("无效的手机号格式");
            }
        }

        // Validate password confirmation
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }

        // Encrypt password
        String encodedPassword = passwordEncoder.encode(password);

        // Create new user
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setEmail(email);
        user.setPhone(phone);
        user.setEnabled(true);

        // Save user to database
        userRepository.save(user);
    }

    /**
     * Resets user password using phone verification code.
     *
     * @param phone the phone number
     * @param code the verification code
     * @param newPassword the new password
     * @param confirmPassword the confirm new password
     * @throws IllegalArgumentException if validation fails
     */
    public void resetPassword(String phone, String code, String newPassword, String confirmPassword) {
        // Verify the verification code
        Optional<VerifyCode> codeOpt = verifyCodeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone);
        if (codeOpt.isEmpty()) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        VerifyCode verifyCode = codeOpt.get();
        if (!verifyCode.getCode().equals(code)) {
            throw new IllegalArgumentException("验证码错误");
        }

        if (!verifyCode.getType().equals("reset")) {
            throw new IllegalArgumentException("无效的验证码类型");
        }

        if (verifyCode.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("验证码已过期");
        }

        // Delete the used code
        verifyCodeRepository.delete(verifyCode);

        // Validate password match
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }

        // Find user by phone
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("该手机号未注册"));

        // Update password
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userRepository.save(user);
    }

    /**
     * Authenticates a user using phone number and verification code.
     *
     * @param phone the phone number
     * @param code the verification code
     * @return LoginResponse containing the JWT token and user info
     * @throws IllegalArgumentException if credentials are invalid
     */
    public LoginResponse phoneLogin(String phone, String code) {
        // Get the latest code for this phone
        Optional<VerifyCode> codeOpt = verifyCodeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone);

        // Check if code exists
        if (codeOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid verification code or code expired");
        }

        VerifyCode verifyCode = codeOpt.get();

        // Check if code matches
        if (!verifyCode.getCode().equals(code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        // Check if code type matches
        if (!verifyCode.getType().equals("login")) {
            throw new IllegalArgumentException("Invalid verification code type");
        }

        // Check if code is expired
        if (verifyCode.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification code has expired");
        }

        // Delete the used code to prevent reuse
        verifyCodeRepository.delete(verifyCode);

        // Find user by phone number or create if not exists
        User user = userRepository.findByPhone(phone)
                .orElseGet(() -> {
                    // Create new user with phone as username
                    User newUser = new User();
                    newUser.setUsername("user_" + phone); // Generate username from phone
                    newUser.setPassword(passwordEncoder.encode(code)); // Encrypt code as password
                    newUser.setPhone(phone);
                    newUser.setEnabled(true);
                    return userRepository.save(newUser);
                });

        if (user.isLocked()) {
            throw new IllegalArgumentException("Account is locked due to too many failed login attempts");
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("User account is disabled");
        }

        user.setLastLoginTime(LocalDateTime.now());
        user.setLoginFailCount(0);
        userRepository.save(user);

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        long expiresIn = jwtUtil.extractExpiration(token).getTime() - System.currentTimeMillis();

        return new LoginResponse(token, user.getUsername(), user.getRole(), expiresIn);
    }
}
