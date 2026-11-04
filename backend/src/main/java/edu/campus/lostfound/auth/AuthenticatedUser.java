package edu.campus.lostfound.auth;

public record AuthenticatedUser(Long userId, String publicId, String role) {
}
