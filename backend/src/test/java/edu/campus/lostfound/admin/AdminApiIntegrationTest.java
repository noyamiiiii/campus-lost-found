package edu.campus.lostfound.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class AdminApiIntegrationTest {

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
    void blocksAdminEndpointsForAnonymousAndStudent() throws Exception {
        mockMvc.perform(get("/api/v1/admin/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        String studentToken = loginAs("20260001", "13800000001");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void listsUsersAndBansThenRestoresAccount() throws Exception {
        String adminToken = loginAs("admin", "13900000000");

        mockMvc.perform(get("/api/v1/admin/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(3))
                .andExpect(jsonPath("$.data.adminUsers").value(1));

        MvcResult users = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "20260002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].studentNo").value("20260002"))
                .andExpect(jsonPath("$.data.list[0].phoneMasked").value("138****0002"))
                .andReturn();

        String publicId = JsonPath.read(users.getResponse().getContentAsString(), "$.data.list[0].publicId");

        mockMvc.perform(patch("/api/v1/admin/users/{publicId}/status", publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"测试封禁\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BANNED"));

        requestLogin("20260002", "13800000002");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"20260002\",\"phone\":\"13800000002\",\"code\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(patch("/api/v1/admin/users/{publicId}/status", publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"测试解封\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void refusesToBanAdministratorAccount() throws Exception {
        String adminToken = loginAs("admin", "13900000000");

        mockMvc.perform(patch("/api/v1/admin/users/{publicId}/status", "usr_demo_admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能修改自己的账号状态"));
    }

    private String loginAs(String studentNo, String phone) throws Exception {
        requestLogin(studentNo, phone);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(studentNo, phone)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private void requestLogin(String studentNo, String phone) throws Exception {
        // 同一账号 60 秒内只允许发送一次，重复调用时忽略 429，沿用仍在有效期内的验证码记录。
        mockMvc.perform(post("/api/v1/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone + "\"}"));
    }

    private String loginBody(String studentNo, String phone) {
        return "{\"studentNo\":\"" + studentNo + "\",\"phone\":\"" + phone + "\",\"code\":\"123456\"}";
    }
}
