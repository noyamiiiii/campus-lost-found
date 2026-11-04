package edu.campus.lostfound.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.user.UserDtos.UserProfileUpdateRequest;
import edu.campus.lostfound.user.UserDtos.UserProfileView;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local")
public class UserService {

    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;

    public UserService(SysUserMapper userMapper, UserProfileMapper profileMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @Transactional(readOnly = true)
    public SysUserEntity findByStudentNo(String studentNo) {
        return userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getStudentNo, studentNo)
                .last("LIMIT 1"));
    }

    @Transactional(readOnly = true)
    public SysUserEntity findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getPhone, phone)
                .last("LIMIT 1"));
    }

    @Transactional(readOnly = true)
    public SysUserEntity requireById(Long userId) {
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null || "DELETED".equals(user.getStatus())) {
            throw ApiException.unauthorized("用户不存在或会话已失效");
        }
        return user;
    }

    @Transactional
    public SysUserEntity createStudent(String studentNo, String phone) {
        try {
            SysUserEntity user = new SysUserEntity();
            user.setPublicId("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toLowerCase(Locale.ROOT));
            user.setStudentNo(studentNo);
            user.setPhone(phone);
            user.setRole("STUDENT");
            user.setStatus("ACTIVE");
            user.setPhoneVerifiedAt(LocalDateTime.now());
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);

            UserProfileEntity profile = new UserProfileEntity();
            profile.setUserId(user.getId());
            profileMapper.insert(profile);
            return user;
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("该手机号或学号已被使用");
        }
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        userMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getLastLoginAt, LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public UserProfileView getProfile(Long userId) {
        return toView(requireById(userId));
    }

    @Transactional
    public UserProfileView updateProfile(Long userId, UserProfileUpdateRequest request) {
        SysUserEntity user = requireById(userId);
        String realName = request.realName().trim();
        String dept = request.dept().trim();
        userMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getRealName, realName)
                .set(SysUserEntity::getDept, dept));

        String dorm = normalizeNullable(request.dorm());
        String wechat = normalizeNullable(request.wechat());
        UserProfileEntity profile = profileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfileEntity();
            profile.setUserId(userId);
            profile.setDorm(dorm);
            profile.setWechat(wechat);
            profileMapper.insert(profile);
        } else {
            profileMapper.update(null, Wrappers.<UserProfileEntity>lambdaUpdate()
                    .eq(UserProfileEntity::getUserId, userId)
                    .set(UserProfileEntity::getDorm, dorm)
                    .set(UserProfileEntity::getWechat, wechat));
        }

        user.setRealName(realName);
        user.setDept(dept);
        return toView(user);
    }

    public UserProfileView toView(SysUserEntity user) {
        UserProfileEntity profile = profileMapper.selectById(user.getId());
        String dorm = profile == null ? null : profile.getDorm();
        String wechat = profile == null ? null : profile.getWechat();
        boolean profileComplete = StringUtils.hasText(user.getRealName()) && StringUtils.hasText(user.getDept());
        return new UserProfileView(
                user.getPublicId(),
                user.getStudentNo(),
                user.getRealName(),
                maskPhone(user.getPhone()),
                user.getDept(),
                dorm,
                wechat,
                user.getRole(),
                user.getStatus(),
                profileComplete,
                user.getPhoneVerifiedAt() != null);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
