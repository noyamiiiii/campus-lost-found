-- Local development only: account used to enter the management console.

INSERT INTO `sys_user`
  (`public_id`, `student_no`, `real_name`, `phone`, `role`, `dept`, `status`, `phone_verified_at`)
VALUES
  ('usr_demo_admin', 'admin', '系统管理员', '13900000000', 'ADMIN', '信息中心', 'ACTIVE', CURRENT_TIMESTAMP(3));
