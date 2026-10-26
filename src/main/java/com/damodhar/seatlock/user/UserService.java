package com.damodhar.seatlock.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.damodhar.seatlock.common.error.NotFoundException;
import com.damodhar.seatlock.user.dto.UserResponse;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found."));
    }
}
