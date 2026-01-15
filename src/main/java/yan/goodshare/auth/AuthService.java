package yan.goodshare.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.User;
import yan.goodshare.user.UserService;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserService userService, JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.tokenProvider = tokenProvider;
    }

    public User register(User user) {
        if (userService.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username is already taken");
        }
        return userService.register(user);
    }

    public String login(String username, String password, AuthenticationManager authenticationManager) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return tokenProvider.generateToken(authentication);
    }
}
