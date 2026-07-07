package com.anitrack.domain.user.service;

public interface TokenProvider {
    String generateToken(Long userId);
}
