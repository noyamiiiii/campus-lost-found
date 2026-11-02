package edu.campus.lostfound.post;

import edu.campus.lostfound.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class PostCursorCodec {

    public String encode(PostRow row) {
        String raw = row.getCreatedAt() + "|" + row.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                throw ApiException.badRequest("分页游标无效");
            }
            return new Cursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw ApiException.badRequest("分页游标无效");
        }
    }

    public record Cursor(LocalDateTime createdAt, Long id) {
    }
}
