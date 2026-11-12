package edu.campus.lostfound.file;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 文件接口的回归测试：上传成功返回可访问的地址与像素尺寸、非图片与空文件被拒绝。
 * 这些行为是前端 ImageUploader 与会话里的契约，容易被后续重构破坏，所以单独覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class FileApiIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.3")
            .withDatabaseName("lostfound")
            .withUsername("lostfound_test")
            .withPassword("lostfound_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadsPngAndServesItBack() throws Exception {
        String token = loginAs("20260001", "13800000001");

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "umbrella.png", "image/png", tinyPng()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mime").value("image/png"))
                .andExpect(jsonPath("$.data.width").value(1))
                .andExpect(jsonPath("$.data.height").value(1))
                // AI 结果随上传一起返回：有 ai-service 时是 DONE，没有时是 FAILED/SKIPPED，
                // 但字段一定存在，前端据此决定要不要做「智能填写」。
                .andExpect(jsonPath("$.data.ai").exists())
                .andExpect(jsonPath("$.data.ai.status").isNotEmpty())
                .andReturn();

        String url = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.data.url");
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    if (result.getResponse().getContentAsByteArray().length == 0) {
                        throw new AssertionError("静态图片响应为空：" + url);
                    }
                });
    }

    @Test
    void rejectsNonImageUpload() throws Exception {
        String token = loginAs("20260001", "13800000001");

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "note.txt", "text/plain", "这不是图片".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsEmptyUpload() throws Exception {
        String token = loginAs("20260001", "13800000001");

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsAnonymousUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "umbrella.png", "image/png", tinyPng())))
                .andExpect(status().isUnauthorized());
    }

    private byte[] tinyPng() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
    }

    private String loginAs(String studentNo, String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone
                                + "\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }
}